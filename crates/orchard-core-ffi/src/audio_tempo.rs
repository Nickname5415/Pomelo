use crate::audio_engine::OrchardAudioEngineHandle;
use signalsmith_stretch::Stretch;
use std::slice;

const MIN_SPEED: f32 = 0.5;
const MAX_SPEED: f32 = 2.0;
const MAX_SEMITONES: f32 = 12.0;
const ACTIVE_EPSILON: f32 = 1.0e-3;

// Streaming tempo and pitch stage: `speed` is the input/output frame ratio.
pub struct TempoStretcher {
    sample_rate: u32,
    channels: usize,
    stretch: Option<Stretch>,
    speed: f32,
    semitones: f32,
    // Input frames not yet matched by output; keeps the long-run ratio exact.
    owed: f64,
}

fn finite_clamp(value: f32, min: f32, max: f32, fallback: f32) -> f32 {
    if value.is_finite() {
        value.clamp(min, max)
    } else {
        fallback
    }
}

impl TempoStretcher {
    pub fn new(sample_rate: u32, channels: usize) -> Self {
        Self {
            sample_rate,
            channels,
            stretch: None,
            speed: 1.0,
            semitones: 0.0,
            owed: 0.0,
        }
    }

    pub fn is_active(&self) -> bool {
        (self.speed - 1.0).abs() > ACTIVE_EPSILON || self.semitones.abs() > ACTIVE_EPSILON
    }

    pub fn configure(&mut self, speed: f32, semitones: f32) {
        // A zeroed field from an older caller means unity speed.
        let speed = if speed == 0.0 {
            1.0
        } else {
            finite_clamp(speed, MIN_SPEED, MAX_SPEED, 1.0)
        };
        let semitones = finite_clamp(semitones, -MAX_SEMITONES, MAX_SEMITONES, 0.0);
        let was_active = self.is_active();
        self.speed = speed;
        self.semitones = semitones;
        if !self.is_active() {
            return;
        }
        if !was_active {
            self.reset();
        }
        let channels = self.channels as u32;
        let sample_rate = self.sample_rate;
        let stretch = self
            .stretch
            .get_or_insert_with(|| Stretch::preset_default(channels, sample_rate));
        stretch.set_transpose_factor_semitones(semitones, None);
    }

    pub fn reset(&mut self) {
        self.owed = 0.0;
        if let Some(stretch) = self.stretch.as_mut() {
            stretch.reset();
        }
    }

    // Upper bound on output frames for `input_frames` at any supported speed.
    pub fn max_output_frames(input_frames: usize) -> usize {
        (input_frames as f32 / MIN_SPEED) as usize + 2
    }

    pub fn process(&mut self, input: &[f32], output: &mut [f32]) -> usize {
        let Some(stretch) = self.stretch.as_mut() else {
            return 0;
        };
        let channels = self.channels;
        let input_frames = input.len() / channels;
        let total = self.owed + input_frames as f64;
        let wanted =
            ((total / f64::from(self.speed)).floor() as usize).min(output.len() / channels);
        self.owed = total - wanted as f64 * f64::from(self.speed);
        if wanted == 0 {
            // Feed the input anyway so the analysis window keeps its history.
            stretch.process(input, &mut [] as &mut [f32]);
            return 0;
        }
        stretch.process(input, &mut output[..wanted * channels]);
        wanted
    }
}

#[unsafe(no_mangle)]
/// Reports whether the handle currently changes tempo or pitch.
///
/// # Safety
/// `handle` must be null or a live handle returned by `orchard_audio_engine_create`.
pub unsafe extern "C" fn orchard_audio_engine_tempo_active(
    handle: *const OrchardAudioEngineHandle,
) -> u8 {
    if handle.is_null() {
        return 0;
    }
    u8::from(unsafe { &*handle }.tempo.is_active())
}

#[unsafe(no_mangle)]
/// Stretches interleaved PCM into `output`, returning the frames written.
/// `output_capacity` must cover `ceil(frame_count / 0.5) + 2` frames.
///
/// # Safety
/// `handle` must be live, `input` readable for `frame_count * channels` values and
/// `output` writable for `output_capacity * channels` values.
pub unsafe extern "C" fn orchard_audio_engine_tempo_process(
    handle: *mut OrchardAudioEngineHandle,
    input: *const f32,
    frame_count: usize,
    output: *mut f32,
    output_capacity: usize,
) -> usize {
    if handle.is_null() || input.is_null() || output.is_null() || frame_count == 0 {
        return 0;
    }
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let handle = unsafe { &mut *handle };
        let channels = handle.engine.channel_count();
        if output_capacity < TempoStretcher::max_output_frames(frame_count) {
            return 0;
        }
        let input = unsafe { slice::from_raw_parts(input, frame_count * channels) };
        let output = unsafe { slice::from_raw_parts_mut(output, output_capacity * channels) };
        handle.tempo.process(input, output)
    }))
    .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tone(frames: usize, hz: f32) -> Vec<f32> {
        (0..frames)
            .flat_map(|frame| {
                let sample =
                    (2.0 * std::f32::consts::PI * hz * frame as f32 / 48_000.0).sin() * 0.5;
                [sample, sample]
            })
            .collect()
    }

    fn run(stretcher: &mut TempoStretcher, input: &[f32]) -> Vec<f32> {
        let mut out = Vec::new();
        for chunk in input.chunks(4_096 * 2) {
            let mut block = vec![0.0; TempoStretcher::max_output_frames(chunk.len() / 2) * 2];
            let frames = stretcher.process(chunk, &mut block);
            out.extend_from_slice(&block[..frames * 2]);
        }
        out
    }

    fn zero_crossings(samples: &[f32]) -> usize {
        samples
            .chunks(2)
            .map(|frame| frame[0])
            .collect::<Vec<_>>()
            .windows(2)
            .filter(|pair| pair[0] < 0.0 && pair[1] >= 0.0)
            .count()
    }

    #[test]
    fn inactive_by_default() {
        let mut stretcher = TempoStretcher::new(48_000, 2);
        stretcher.configure(0.0, 0.0);
        assert!(!stretcher.is_active());
    }

    #[test]
    fn faster_speed_shortens_output_and_keeps_pitch() {
        let mut stretcher = TempoStretcher::new(48_000, 2);
        stretcher.configure(1.5, 0.0);
        let out = run(&mut stretcher, &tone(96_000, 440.0));
        assert_eq!(out.len() / 2, 64_000);
        let skip = 9_600 * 2;
        let body = &out[skip..];
        let hz = zero_crossings(body) as f32 / (body.len() as f32 / 2.0 / 48_000.0);
        assert!((hz - 440.0).abs() < 8.0, "pitch drifted to {hz}");
    }

    #[test]
    fn semitones_shift_pitch_at_unity_speed() {
        let mut stretcher = TempoStretcher::new(48_000, 2);
        stretcher.configure(1.0, 12.0);
        let out = run(&mut stretcher, &tone(96_000, 440.0));
        assert_eq!(out.len() / 2, 96_000);
        let body = &out[9_600 * 2..];
        let hz = zero_crossings(body) as f32 / (body.len() as f32 / 2.0 / 48_000.0);
        assert!((hz - 880.0).abs() < 16.0, "pitch was {hz}");
    }
}

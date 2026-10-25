<div align="center">

# Pomelo / Pomelo Mobile

**UI-first fork of [Orchard](https://github.com/SFG5453/Orchard)**

[Mobile build notes](mobile/README.md) · [Upstream Orchard](https://github.com/SFG5453/Orchard)

[![License](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

</div>

---

## Credit: Orchard

**[Orchard](https://github.com/SFG5453/Orchard)** is by **SFG545** and **Julian-FF2000**. That project owns the hard parts: YouTube Music providers, playback, queueing, lyrics, caching, Orchard Connect, and the native stacks behind them.

**Pomelo** and **Pomelo Mobile** start from Orchard’s codebase under the [GNU AGPL-3.0-or-later](LICENSE). If audio plays, a library loads, or Connect pairs, that’s Orchard’s work. Pomelo changes branding, copy, and—especially on Android—a lot of **interface** code.

Official Orchard:

- Source: [github.com/SFG5453/Orchard](https://github.com/SFG5453/Orchard)
- Site: [sfg545.dev/orchard](https://sfg545.dev/orchard)

Use upstream when you want maintained releases, full feature set, and support from the original authors.

## What this fork is (and isn’t)

| | Pomelo / Pomelo Mobile | Orchard |
| --- | --- | --- |
| **Goal** | Look and feel, Material You, experiments | Complete music client |
| **Strength** | Compose UI, themes, motion | Playback, providers, Connect |
| **Expectation** | Hobby / vibe project | Production-oriented |

This is **not** a competing product claim. It’s a skin and UX pass on top of Orchard, with some proprietary upstream pieces removed in this tree (Adaptive Mix, related ML tooling) in favor of standard crossfade and gapless paths.

## Vibecoded

Most Pomelo-specific changes were made **fast**, with **AI-assisted editing** (e.g. Cursor). That means:

- UI can be ahead of testing in places.
- Refactors may be broad rather than surgical.
- Comments and docs might lag the code.

Treat this repo as **“looks cool, ymmv on edge cases.”** Report bugs upstream when they’re really Orchard behavior; report here when it’s clearly the Pomelo UI layer.

## Focus: UI, not new features

**Pomelo Mobile** (under `mobile/`) is the main reason this fork exists:

- Material 3 and Material You–style surfaces across screens.
- Haptics, typography, and layout polish in Jetpack Compose.
- Pomelo identity in the shell (name, icons, about text).

It does **not** aim to out-feature Orchard on streaming, audio engines, or Connect protocol work. New capabilities should usually go upstream; Pomelo is for **how it feels in your hand**.

Desktop pieces in this monorepo may still carry Pomelo naming and docs updates, but the active “fork story” is the Android UI work.

## Maintainer

Community fork maintained by **Nickname5415**. Not affiliated with, sponsored by, or endorsed by SFG545, Julian-FF2000, the Orchard team, Google, YouTube, or Qobuz.

## License

Pomelo is distributed under **AGPL-3.0-or-later**. You must comply with AGPL when you run modified versions and when you convey source. See [LICENSE](LICENSE), [NOTICE.md](NOTICE.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

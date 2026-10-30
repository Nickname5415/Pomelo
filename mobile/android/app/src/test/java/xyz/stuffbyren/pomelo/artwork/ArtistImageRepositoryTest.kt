package xyz.stuffbyren.pomelo.artwork

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistImageRepositoryTest {
    private class Fixture(private val body: String = PORTRAIT, concurrent: Int = 1) {
        val requests = AtomicInteger()
        val entered = CountDownLatch(concurrent)
        val release = CountDownLatch(1)
        val repository = ArtistImageRepository(OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build())

        fun awaitRequest() = check(entered.await(5, TimeUnit.SECONDS))
    }

    @Test fun `overlapping normalized names share one request and cache entry`() = runBlocking {
        val fixture = Fixture()
        val lookups = (1..20).map { index ->
            async(Dispatchers.IO) { fixture.repository.images(if (index % 2 == 0) "  FIXTURE  " else "fixture") }
        }
        try {
            fixture.awaitRequest()
            delay(100)
        } finally {
            fixture.release.countDown()
        }
        withTimeout(5_000) {
            lookups.awaitAll().forEach { assertEquals(URL, it?.portraitUrl) }
        }
        assertEquals(URL, fixture.repository.images("Fixture")?.portraitUrl)
        assertEquals(1, fixture.requests.get())
    }

    @Test fun `different artists load concurrently`() = runBlocking {
        val fixture = Fixture(concurrent = 2)
        val first = async(Dispatchers.IO) { fixture.repository.images("First") }
        val second = async(Dispatchers.IO) { fixture.repository.images("Second") }
        try {
            fixture.awaitRequest()
        } finally {
            fixture.release.countDown()
        }
        assertEquals(URL, first.await()?.portraitUrl)
        assertEquals(URL, second.await()?.portraitUrl)
        assertEquals(2, fixture.requests.get())
    }

    @Test fun `absent imagery is shared and remains negatively cached`() = runBlocking {
        val fixture = Fixture(body = """{"artists":null}""")
        val first = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        fixture.awaitRequest()
        val second = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        try {
            delay(100)
        } finally {
            fixture.release.countDown()
        }
        assertNull(first.await())
        assertNull(second.await())
        assertNull(fixture.repository.images("Fixture"))
        assertEquals(1, fixture.requests.get())
    }

    @Test fun `cancelling a waiter preserves the owner and cache`() = runBlocking {
        val fixture = Fixture()
        val owner = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        fixture.awaitRequest()
        val waiter = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        try {
            delay(100)
            waiter.cancel()
        } finally {
            fixture.release.countDown()
        }
        waiter.join()
        assertEquals(URL, withTimeout(5_000) { owner.await() }?.portraitUrl)
        assertEquals(URL, fixture.repository.images("Fixture")?.portraitUrl)
        assertEquals(1, fixture.requests.get())
    }

    @Test fun `cancelling a blocking owner still settles its waiters`() = runBlocking {
        val fixture = Fixture()
        val owner = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        fixture.awaitRequest()
        val waiter = async(Dispatchers.IO) { fixture.repository.images("Fixture") }
        try {
            delay(100)
            owner.cancel()
        } finally {
            fixture.release.countDown()
        }
        owner.join()
        assertEquals(URL, withTimeout(5_000) { waiter.await() }?.portraitUrl)
        assertEquals(1, fixture.requests.get())
    }

    private companion object {
        const val URL = "https://example.com/portrait.jpg"
        const val PORTRAIT = """{"artists":[{"strArtistThumb":"https://example.com/portrait.jpg"}]}"""
    }
}

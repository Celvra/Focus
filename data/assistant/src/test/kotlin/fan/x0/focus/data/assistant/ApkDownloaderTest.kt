package fan.x0.focus.data.assistant

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDownloaderTest {

    @Test
    fun downloadsWithRangesAndResumesAfterFailure() = runBlocking {
        val payload = ByteArray(5 * 1024 * 1024 + 37) { index -> (index * 31).toByte() }
        val server = RangeServer(payload)
        server.start()
        val directory = Files.createTempDirectory("apk-downloader").toFile()
        val client = HttpClient(OkHttp) { expectSuccess = false }
        try {
            val target = File(directory, "focus.apk")
            val downloader = ApkDownloader(client)
            val parts = File(directory, "focus.apk.parts")
            assertTrue(parts.mkdirs())
            File(parts, "meta").writeText("${server.url}\n${payload.size}\n2")
            File(parts, "0.part").writeBytes(payload.copyOfRange(0, 1024 * 1024))

            server.failNextRange.set(true)
            val first = runCatching {
                downloader.download(server.url, target, payload.size.toLong()) { _, _ -> }
            }
            assertTrue(first.isFailure)
            assertTrue(parts.isDirectory)
            assertTrue(parts.listFiles().orEmpty().any { it.name.endsWith(".part") && it.length() > 0 })

            server.failNextRange.set(false)
            downloader.download(server.url, target, payload.size.toLong()) { _, _ -> }

            assertTrue(target.readBytes().contentEquals(payload))
            assertFalse(parts.exists())
            assertTrue(server.rangeRequests.get() >= 2)
        } finally {
            client.close()
            server.stop()
            directory.deleteRecursively()
        }
    }

    @Test
    fun comparesVersions() {
        assertTrue(AppVersion.isNewer("v1.2.0", "1.1.9"))
        assertTrue(AppVersion.isNewer("1.0.1", "1.0"))
        assertFalse(AppVersion.isNewer("v1.0.0", "1.0"))
        assertFalse(AppVersion.isNewer("v1.0.0", "1.0.1"))
    }
}

private class RangeServer(private val payload: ByteArray) {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val rangeRequests = AtomicInteger()
    val failNextRange = AtomicBoolean(false)
    val url: String get() = "http://127.0.0.1:${server.address.port}/focus.apk"

    init {
        server.executor = Executors.newFixedThreadPool(4)
        server.createContext("/focus.apk", ::handle)
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod == "HEAD") {
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.sendResponseHeaders(200, -1)
                return
            }
            val range = exchange.requestHeaders.getFirst("Range")
            if (range == null) {
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.write(payload)
                return
            }
            rangeRequests.incrementAndGet()
            val bounds = rangeBounds(range)
            if (bounds == null) {
                exchange.sendResponseHeaders(416, -1)
                return
            }
            val (start, end) = bounds
            val length = end - start + 1
            if (failNextRange.compareAndSet(true, false)) {
                exchange.sendResponseHeaders(500, -1)
                return
            }
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.write(payload, start, length)
        } catch (_: IOException) {
        } finally {
            exchange.close()
        }
    }

    private fun rangeBounds(header: String): Pair<Int, Int>? {
        val spec = header.removePrefix("bytes=").substringBefore(',')
        val start = spec.substringBefore('-').toIntOrNull() ?: return null
        val end = spec.substringAfter('-', "").toIntOrNull() ?: payload.lastIndex
        if (start < 0 || start > payload.lastIndex) return null
        return start to end.coerceIn(start, payload.lastIndex)
    }
}

package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.allowLongStream
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class ApkDownloader(private val http: HttpClient) {

    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = download(url, target, expectedSize, multiThreaded = true, onProgress = onProgress)

    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        multiThreaded: Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val parent = target.absoluteFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("cannot create download directory")
        }
        val total = expectedSize.takeIf { it > 0 } ?: remoteSize(url)
        if (total > 0 && target.length() == total) {
            deleteParts(target)
            onProgress(total, total)
            return@withContext target
        }
        if (total <= 0) {
            target.delete()
            downloadWhole(url, target, onProgress)
            return@withContext target
        }
        if (!multiThreaded) {
            downloadSingle(url, target, total, onProgress)
            return@withContext target
        }
        val partCount = min(MAX_THREADS, max(1, (total / MIN_PART_SIZE).toInt()))
        if (partCount <= 1) {
            downloadSingle(url, target, total, onProgress)
            return@withContext target
        }
        try {
            downloadParts(url, target, total, partCount, onProgress)
        } catch (_: RangeUnsupported) {
            deleteParts(target)
            target.delete()
            downloadSingle(url, target, total, onProgress)
        }
        target
    }

    private suspend fun remoteSize(url: String): Long {
        val response = try {
            http.head(url) { allowLongStream() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        if (!response.status.isSuccess()) return 0
        return response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
    }

    private suspend fun downloadWhole(
        url: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        val response = request(url)
        if (!response.status.isSuccess()) throw httpError(response)
        val channel = response.bodyAsChannel()
        val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
        var downloaded = 0L
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(0)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                if (read == 0) continue
                file.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
        }
        if (total > 0 && target.length() != total) throw IOException("incomplete download")
    }

    private suspend fun downloadSingle(
        url: String,
        target: File,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var existing = target.length()
        if (existing > total) {
            target.delete()
            existing = 0
        }
        if (existing == total) {
            onProgress(total, total)
            return
        }
        if (existing > 0) onProgress(existing, total)
        val response = request(url, start = existing.takeIf { it > 0 })
        if (!response.status.isSuccess()) throw httpError(response)
        if (existing > 0 && response.status != HttpStatusCode.PartialContent) existing = 0
        val channel = response.bodyAsChannel()
        var downloaded = existing
        RandomAccessFile(target, "rw").use { file ->
            file.setLength(existing)
            file.seek(existing)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                if (read == 0) continue
                file.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
        }
        if (target.length() != total) throw IOException("incomplete download")
    }

    private suspend fun downloadParts(
        url: String,
        target: File,
        total: Long,
        partCount: Int,
        onProgress: (Long, Long) -> Unit,
    ) {
        val directory = partsDir(target)
        val meta = DownloadMeta(url = url, total = total, count = partCount)
        if (!directory.exists() || readMeta(directory) != meta) {
            directory.deleteRecursively()
            if (!directory.mkdirs()) throw IOException("cannot create part directory")
            writeMeta(directory, meta)
        }
        val ranges = partRanges(total, partCount, directory)
        val initial = ranges.sumOf { min(it.file.length(), it.length) }
        val reporter = ProgressReporter(total, initial, onProgress)
        if (initial > 0) onProgress(initial, total)
        coroutineScope {
            ranges.map { range ->
                async(Dispatchers.IO) { downloadPart(url, range, reporter) }
            }.awaitAll()
        }
        ranges.forEach { range ->
            if (range.file.length() != range.length) throw IOException("incomplete part")
        }
        mergeParts(target, ranges)
        directory.deleteRecursively()
        onProgress(total, total)
    }

    private suspend fun downloadPart(url: String, range: PartRange, reporter: ProgressReporter) {
        var existing = range.file.length()
        if (existing > range.length) {
            range.file.delete()
            existing = 0
        }
        if (existing == range.length) return
        val response = request(url, start = range.start + existing, end = range.end)
        if (response.status != HttpStatusCode.PartialContent) {
            response.bodyAsChannel().cancel()
            if (response.status.isSuccess()) throw RangeUnsupported()
            throw NetworkExceptionMapper.http(response.status.value, null)
        }
        val channel = response.bodyAsChannel()
        RandomAccessFile(range.file, "rw").use { file ->
            file.setLength(existing)
            file.seek(existing)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break
                if (read == 0) continue
                file.write(buffer, 0, read)
                reporter.add(read.toLong())
            }
        }
    }

    private suspend fun request(
        url: String,
        start: Long? = null,
        end: Long? = null,
    ): HttpResponse = try {
        http.get(url) {
            if (start != null) {
                val range = if (end != null) "bytes=$start-$end" else "bytes=$start-"
                header(HttpHeaders.Range, range)
            }
            header(HttpHeaders.AcceptEncoding, "identity")
            allowLongStream()
            timeout {
                requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS
                socketTimeoutMillis = DOWNLOAD_TIMEOUT_MS
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw NetworkExceptionMapper.mapOrNull(e) ?: e
    }

    private suspend fun httpError(response: HttpResponse): Throwable =
        NetworkExceptionMapper.http(response.status.value, response.bodyAsText())

    private fun mergeParts(target: File, ranges: List<PartRange>) {
        val merged = File(target.absoluteFile.parentFile, "${target.name}.merged")
        merged.delete()
        try {
            merged.outputStream().use { output ->
                ranges.sortedBy { it.index }.forEach { range ->
                    range.file.inputStream().use { input -> input.copyTo(output) }
                }
            }
            target.delete()
            if (!merged.renameTo(target)) {
                merged.copyTo(target, overwrite = true)
                merged.delete()
            }
        } finally {
            merged.delete()
        }
    }

    private fun partRanges(total: Long, count: Int, directory: File): List<PartRange> {
        val size = (total + count - 1) / count
        return (0 until count).mapNotNull { index ->
            val start = index.toLong() * size
            if (start >= total) return@mapNotNull null
            val end = min(total - 1, start + size - 1)
            PartRange(
                index = index,
                start = start,
                end = end,
                file = File(directory, "$index.part"),
            )
        }
    }

    private fun partsDir(target: File): File =
        File(target.absoluteFile.parentFile, "${target.name}.parts")

    private fun deleteParts(target: File) {
        partsDir(target).deleteRecursively()
    }

    private fun readMeta(directory: File): DownloadMeta? = runCatching {
        val lines = File(directory, META_FILE).readLines()
        DownloadMeta(url = lines[0], total = lines[1].toLong(), count = lines[2].toInt())
    }.getOrNull()

    private fun writeMeta(directory: File, meta: DownloadMeta) {
        File(directory, META_FILE).writeText("${meta.url}\n${meta.total}\n${meta.count}")
    }

    private data class PartRange(
        val index: Int,
        val start: Long,
        val end: Long,
        val file: File,
    ) {
        val length: Long get() = end - start + 1
    }

    private data class DownloadMeta(
        val url: String,
        val total: Long,
        val count: Int,
    )

    private class RangeUnsupported : Exception()

    private class ProgressReporter(
        private val total: Long,
        initial: Long,
        private val onProgress: (Long, Long) -> Unit,
    ) {
        private val downloaded = AtomicLong(initial)
        private val lastEmit = AtomicLong(0L)

        fun add(bytes: Long) {
            val value = downloaded.addAndGet(bytes)
            val now = System.currentTimeMillis()
            val last = lastEmit.get()
            if (now - last >= PROGRESS_INTERVAL_MS && lastEmit.compareAndSet(last, now)) {
                onProgress(value, total)
            }
        }
    }

    private companion object {
        const val MAX_THREADS = 4
        const val MIN_PART_SIZE = 2 * 1024 * 1024L
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 120L
        const val DOWNLOAD_TIMEOUT_MS = 600_000L
        const val META_FILE = "meta"
    }
}

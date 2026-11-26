package dev.hori.browser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse
import java.io.File

/** Public download state for the UI. */
data class DownloadItem(
    val id: Long,
    val fileName: String,
    val url: String,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    // One of: "running", "paused", "done", "failed", "cancelled"
    val status: String = "running",
    val filePath: String? = null,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) {
            (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isActive: Boolean get() = status == "running" || status == "paused"
}

/**
 * Owns file downloads. GeckoView delivers a finished response (headers + body
 * stream) via [handleExternalResponse]; we stream it to disk ourselves so we
 * control naming, progress, cancellation and where files land.
 */
class DownloadManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads

    private val jobs = HashMap<Long, Job>()
    private var nextId = 1L

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
            .apply { mkdirs() }

    /** Turns a GeckoView external response into a tracked download. */
    fun handleExternalResponse(response: WebResponse) {
        val body = response.body
        val url = response.uri
        if (body == null) {
            // Nothing to stream (e.g. requestExternalApp) — ignore.
            return
        }
        val fileName = resolveFileName(url, response.headers)
        val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
        val id = synchronized(this) { nextId++ }
        val target = uniqueFile(fileName)

        val item = DownloadItem(
            id = id,
            fileName = target.name,
            url = url,
            totalBytes = total,
            status = "running",
            filePath = target.absolutePath,
        )
        _downloads.value = _downloads.value + item

        val job = scope.launch {
            streamToFile(id, body, target, total)
        }
        synchronized(this) { jobs[id] = job }
    }

    private fun streamToFile(
        id: Long,
        body: java.io.InputStream,
        target: File,
        total: Long,
    ) {
        try {
            body.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        // Throttle UI updates to ~every 100 KB.
                        if (downloaded - lastEmit >= 100 * 1024) {
                            lastEmit = downloaded
                            update(id) { it.copy(bytesDownloaded = downloaded) }
                        }
                    }
                    update(id) {
                        it.copy(
                            bytesDownloaded = downloaded,
                            totalBytes = if (total > 0) total else downloaded,
                            status = "done",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // A cancelled job throws here as well; distinguish via job state.
            val cancelled = jobs[id]?.isCancelled == true
            update(id) {
                it.copy(
                    status = if (cancelled) "cancelled" else "failed",
                    error = if (cancelled) null else e.message,
                )
            }
            if (cancelled) target.delete()
        } finally {
            synchronized(this) { jobs.remove(id) }
        }
    }

    fun cancel(id: Long) {
        synchronized(this) { jobs[id] }?.cancel()
        update(id) { it.copy(status = "cancelled") }
    }

    fun remove(id: Long) {
        cancel(id)
        _downloads.value = _downloads.value.filterNot { it.id == id }
    }

    fun clearFinished() {
        _downloads.value = _downloads.value.filter { it.isActive }
    }

    /** Opens a finished download with the system's default app. */
    fun open(id: Long) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        val path = item.filePath ?: return
        val file = File(path)
        if (!file.exists()) return
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun update(id: Long, transform: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map { if (it.id == id) transform(it) else it }
    }

    private fun resolveFileName(url: String, headers: Map<String, String>): String {
        // Prefer Content-Disposition, then the URL's last path segment.
        val disposition = headers["Content-Disposition"]
        val fromHeader = disposition
            ?.substringAfter("filename=", "")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
        val fromUrl = Uri.parse(url).lastPathSegment
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() && it.contains('.') }
        return sanitize(fromHeader ?: fromUrl ?: "download")
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return if (cleaned.isEmpty()) "download" else cleaned.take(180)
    }

    private fun uniqueFile(name: String): File {
        val dir = downloadsDir
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            val suffix = if (ext.isEmpty()) " ($i)" else " ($i).$ext"
            candidate = File(dir, "$base$suffix")
            i++
        }
        return candidate
    }
}
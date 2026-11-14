package org.johnbrenof.dowtube.download

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.johnbrenof.dowtube.settings.AppSettings

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val historyFile = File(application.filesDir, "history.json")

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val mediaByTask = ConcurrentHashMap<String, String>()

    init {
        AppSettings.init(application)
        viewModelScope.launch(Dispatchers.IO) {
            restoreHistory()
            _downloads
                .map { list ->
                    list.joinToString("|") {
                        "${it.id}:${it.status}:${it.title}:${it.message}:${it.progress.toInt()}"
                    }
                }
                .distinctUntilChanged()
                .collect { persistHistory(_downloads.value) }
        }
    }

    fun onUrlChange(url: String) {
        _urlInput.value = url
    }

    fun startDownload(
        url: String,
        media: String = DownloadItem.MEDIA_VIDEO,
        quality: String = DownloadItem.QUALITY_MAX,
        playlist: Boolean = false,
    ) {
        val normalized = normalizeUrl(url.trim())
        if (!normalized.startsWith("http")) return
        launchTask(normalized, media, quality, playlist)
        _urlInput.value = ""
    }

    fun retry(item: DownloadItem) {
        launchTask(
            url = item.url,
            media = item.media,
            quality = item.quality,
            playlist = item.playlist,
            replaceId = item.id,
        )
    }

    fun clearFinished() {
        _downloads.update { list ->
            list.filter {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            }
        }
    }

    fun remove(item: DownloadItem) {
        if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) return
        _downloads.update { list -> list.filterNot { it.id == item.id } }
    }

    fun purgeOldHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloads.update { list -> list.filter { !isExpired(it) } }
        }
    }

    private fun isExpired(item: DownloadItem): Boolean {
        if (!AppSettings.autoClearHistory.value) return false
        if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) {
            return false
        }
        return item.createdAt < System.currentTimeMillis() - SEVEN_DAYS
    }

    private fun launchTask(
        url: String,
        media: String,
        quality: String,
        playlist: Boolean,
        replaceId: String? = null,
    ) {
        val taskId = UUID.randomUUID().toString().take(8)
        mediaByTask[taskId] = media

        val fresh = DownloadItem(
            id = taskId,
            url = url,
            media = media,
            quality = quality,
            playlist = playlist,
        )

        _downloads.update { list ->
            if (replaceId == null) {
                list + fresh
            } else {
                val index = list.indexOfFirst { it.id == replaceId }
                if (index < 0) list + fresh else list.toMutableList().also { it[index] = fresh }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            DownloadEngine.submit(
                taskId = taskId,
                url = url,
                media = media,
                quality = quality,
                playlist = playlist,
                outputDir = outputDirectory().absolutePath,
                ffmpegPath = FFmpegResolver.resolve(getApplication()),
                listener = bridgeListener,
            )
        }
    }

    private val bridgeListener = object : DownloadListener {

        override fun onProgress(taskId: String, percent: Double, title: String) {
            updateItem(taskId) { item ->
                item.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = (percent / 100.0).toFloat().coerceIn(0f, 1f),
                    title = title.ifBlank { item.title },
                )
            }
        }

        override fun onComplete(taskId: String, filePath: String) {
            viewModelScope.launch(Dispatchers.IO) {
                val media = mediaByTask[taskId] ?: DownloadItem.MEDIA_VIDEO
                val isVideo = media != DownloadItem.MEDIA_AUDIO
                val sources = parsePaths(filePath)
                val exported = sources.mapNotNull { path ->
                    MediaStoreExporter.export(getApplication(), File(path), isVideo)
                }
                val visible = exported.ifEmpty { sources }

                updateItem(taskId) { item ->
                    item.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        message = visible.joinToString("\n"),
                        paths = visible,
                    )
                }
            }
        }

        override fun onError(taskId: String, message: String) {
            updateItem(taskId) { item ->
                item.copy(status = DownloadStatus.ERROR, message = message)
            }
        }
    }

    private fun parsePaths(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val array = runCatching { JSONArray(trimmed) }.getOrNull()
            if (array != null) {
                val values = (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
                if (values.isNotEmpty()) return values
            }
        }
        return listOf(raw)
    }

    private fun updateItem(taskId: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    private fun restoreHistory() {
        val raw = runCatching { historyFile.readText() }.getOrNull() ?: return
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return

        val restored = (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val url = obj.optString("url")
            if (id.isBlank() || url.isBlank()) return@mapNotNull null

            val stored = obj.optString("status")
            val finished = stored == DownloadStatus.COMPLETED.name
            val message = obj.optString("message")
            val media = obj.optString("media").ifBlank { legacyMedia(obj) }
            val quality = obj.optString("quality").ifBlank { legacyQuality(obj) }
            val paths = readPaths(obj, finished, message)

            DownloadItem(
                id = id,
                url = url,
                title = obj.optString("title").ifBlank { url },
                media = media,
                quality = quality,
                playlist = obj.optBoolean("playlist"),
                status = if (finished) DownloadStatus.COMPLETED else DownloadStatus.ERROR,
                progress = if (finished) 1f else 0f,
                message = when {
                    finished -> message
                    message.isBlank() -> "Download interrompido"
                    else -> message
                },
                paths = paths,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            )
        }

        val cleaned = restored.filterNot { isExpired(it) }
        if (cleaned.isNotEmpty()) {
            _downloads.update { current -> if (current.isEmpty()) cleaned else current }
        }
    }

    private fun legacyMedia(obj: JSONObject): String =
        if (obj.optBoolean("isAudio") || obj.optString("qualityValue") == "audio") {
            DownloadItem.MEDIA_AUDIO
        } else {
            DownloadItem.MEDIA_VIDEO
        }

    private fun legacyQuality(obj: JSONObject): String = when (obj.optString("qualityValue")) {
        "360" -> DownloadItem.QUALITY_LOW
        "720" -> DownloadItem.QUALITY_MEDIUM
        "1080" -> DownloadItem.QUALITY_MAX
        else -> DownloadItem.QUALITY_MEDIUM
    }

    private fun readPaths(obj: JSONObject, finished: Boolean, message: String): List<String> {
        val array = obj.optJSONArray("paths")
        if (array != null) {
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        if (!finished || message.isBlank()) return emptyList()
        return message.split("\n").filter { it.isNotBlank() }
    }

    private fun persistHistory(items: List<DownloadItem>) {
        runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("url", item.url)
                        put("title", item.title)
                        put("media", item.media)
                        put("quality", item.quality)
                        put("playlist", item.playlist)
                        put("status", item.status.name)
                        put("message", item.message ?: "")
                        put("paths", JSONArray(item.paths))
                        put("createdAt", item.createdAt)
                    },
                )
            }
            historyFile.writeText(array.toString())
        }
    }

    private fun normalizeUrl(raw: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("//") -> "https:$raw"
        else -> "https://$raw"
    }

    private fun outputDirectory(): File {
        val base = getApplication<Application>()
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: getApplication<Application>().filesDir
        return File(base, "DowTube").apply { mkdirs() }
    }

    companion object {
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
    }
}

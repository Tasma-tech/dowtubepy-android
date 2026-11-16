package org.johnbrenof.dowtube.download

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.johnbrenof.dowtube.util.UrlRules

object DownloadRepository {

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = AtomicBoolean(false)
    private val historyFile: File get() = File(app.filesDir, "history.json")

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val mediaByTask = ConcurrentHashMap<String, String>()

    fun init(context: Context) {
        if (!ready.compareAndSet(false, true)) return
        app = context.applicationContext
        AppSettings.init(app)
        scope.launch {
            restoreHistory()
            _downloads
                .map { list ->
                    list.joinToString("|") {
                        "${it.id}:${it.status}:${it.title}:${it.thumbnail}:${it.message}:${it.progress.toInt()}:${it.paths.size}"
                    }
                }
                .distinctUntilChanged()
                .collect { persistHistory(_downloads.value) }
        }
    }

    fun enqueue(
        url: String,
        media: String,
        quality: String,
        playlist: Boolean,
        preview: PreviewInfo?,
        replaceId: String? = null,
        playlistItems: String = "",
    ): String? {
        if (!ready.get()) return null
        val normalized = UrlRules.normalize(url.trim())
        if (!normalized.startsWith("http")) return null

        val taskId = UUID.randomUUID().toString().take(8)
        mediaByTask[taskId] = media

        val fresh = DownloadItem(
            id = taskId,
            url = normalized,
            title = preview?.title?.ifBlank { normalized } ?: normalized,
            thumbnail = preview?.thumbnail.orEmpty(),
            media = media,
            quality = quality,
            playlist = playlist,
            playlistItems = playlistItems,
        )

        _downloads.update { list ->
            if (replaceId == null) {
                list + fresh
            } else {
                val index = list.indexOfFirst { it.id == replaceId }
                if (index < 0) list + fresh else list.toMutableList().also { it[index] = fresh }
            }
        }

        val intent = Intent(app, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_URL, normalized)
            putExtra(DownloadService.EXTRA_MEDIA, media)
            putExtra(DownloadService.EXTRA_QUALITY, quality)
            putExtra(DownloadService.EXTRA_PLAYLIST, playlist)
            putExtra(DownloadService.EXTRA_PLAYLIST_ITEMS, playlistItems)
        }
        ContextCompat.startForegroundService(app, intent)
        return taskId
    }

    fun retry(item: DownloadItem) {
        enqueue(
            item.url,
            item.media,
            item.quality,
            item.playlist,
            previewOf(item),
            item.id,
            item.playlistItems,
        )
    }

    fun remove(item: DownloadItem) {
        if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) return
        _downloads.update { list -> list.filterNot { it.id == item.id } }
    }

    fun clearFinished() {
        _downloads.update { list ->
            list.filter {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            }
        }
    }

    fun purgeOldHistory() {
        scope.launch { _downloads.update { list -> list.filter { !isExpired(it) } } }
    }

    fun find(taskId: String): DownloadItem? = _downloads.value.firstOrNull { it.id == taskId }

    fun onProgress(taskId: String, percent: Double, title: String) {
        _downloads.update { list ->
            list.map { item ->
                if (item.id == taskId) {
                    item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = (percent / 100.0).toFloat().coerceIn(0f, 1f),
                        title = title.ifBlank { item.title },
                    )
                } else {
                    item
                }
            }
        }
    }

    fun complete(taskId: String, filePath: String, onDone: (DownloadItem?) -> Unit) {
        scope.launch {
            val media = mediaByTask[taskId] ?: DownloadItem.MEDIA_VIDEO
            val isVideo = media != DownloadItem.MEDIA_AUDIO
            val sources = parsePaths(filePath)
            val exported = sources.mapNotNull { path ->
                MediaStoreExporter.export(app, File(path), isVideo)
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
            onDone(find(taskId))
        }
    }

    fun fail(taskId: String, message: String) {
        updateItem(taskId) { it.copy(status = DownloadStatus.ERROR, message = message) }
    }

    fun outputDirectory(): File {
        val base = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: app.filesDir
        return File(base, "DowTube").apply { mkdirs() }
    }

    private fun previewOf(item: DownloadItem): PreviewInfo? = PreviewInfo(
        title = item.title,
        uploader = "",
        thumbnail = item.thumbnail,
        duration = 0,
        entries = if (item.playlist) 1 else 0,
        isPlaylist = item.playlist,
    )

    private fun isExpired(item: DownloadItem): Boolean {
        if (!AppSettings.autoClearHistory.value) return false
        if (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING) {
            return false
        }
        return item.createdAt < System.currentTimeMillis() - SEVEN_DAYS
    }

    private fun updateItem(taskId: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
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
                thumbnail = obj.optString("thumbnail"),
                media = media,
                quality = quality,
                playlist = obj.optBoolean("playlist"),
                playlistItems = obj.optString("playlistItems"),
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
                        put("thumbnail", item.thumbnail)
                        put("media", item.media)
                        put("quality", item.quality)
                        put("playlist", item.playlist)
                        put("playlistItems", item.playlistItems)
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

    private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
}

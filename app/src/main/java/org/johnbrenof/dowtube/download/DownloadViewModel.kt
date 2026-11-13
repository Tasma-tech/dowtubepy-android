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

data class QualityOption(val value: String, val label: String)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val historyFile = File(application.filesDir, "history.json")

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _quality = MutableStateFlow(DEFAULT_QUALITY)
    val quality: StateFlow<QualityOption> = _quality.asStateFlow()

    private val qualityByTask = ConcurrentHashMap<String, String>()

    val qualityOptions = listOf(
        QualityOption("360", "360p"),
        QualityOption("720", "720p"),
        QualityOption("1080", "1080p"),
        QualityOption("audio", "Áudio"),
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreHistory()
            _downloads
                .map { list -> list.joinToString("|") { "${it.id}:${it.status}:${it.title}:${it.message}" } }
                .distinctUntilChanged()
                .collect { persistHistory(_downloads.value) }
        }
    }

    fun onUrlChange(url: String) {
        _urlInput.value = url
    }

    fun onQualityChange(option: QualityOption) {
        _quality.value = option
    }

    fun startDownload() {
        val raw = _urlInput.value.trim()
        if (raw.isEmpty()) return

        val option = _quality.value
        launchTask(normalizeUrl(raw), option.value, option.label)
        _urlInput.value = ""
    }

    fun retry(item: DownloadItem) {
        launchTask(
            url = item.url,
            qualityValue = item.qualityValue.ifBlank { DEFAULT_QUALITY.value },
            qualityLabel = item.qualityLabel.ifBlank { DEFAULT_QUALITY.label },
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

    private fun launchTask(
        url: String,
        qualityValue: String,
        qualityLabel: String,
        replaceId: String? = null,
    ) {
        val taskId = UUID.randomUUID().toString().take(8)
        qualityByTask[taskId] = qualityValue

        val fresh = DownloadItem(
            id = taskId,
            url = url,
            qualityValue = qualityValue,
            qualityLabel = qualityLabel,
            isAudio = qualityValue == "audio",
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
                quality = qualityValue,
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
                val source = File(filePath)
                val isVideo = qualityByTask[taskId] != "audio"
                val visible = MediaStoreExporter.export(getApplication(), source, isVideo)

                updateItem(taskId) { item ->
                    item.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        message = visible ?: filePath,
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

            DownloadItem(
                id = id,
                url = url,
                title = obj.optString("title").ifBlank { url },
                qualityValue = obj.optString("qualityValue"),
                qualityLabel = obj.optString("qualityLabel"),
                isAudio = obj.optBoolean("isAudio"),
                status = if (finished) DownloadStatus.COMPLETED else DownloadStatus.ERROR,
                progress = if (finished) 1f else 0f,
                message = when {
                    finished -> message
                    message.isBlank() -> "Download interrompido"
                    else -> message
                },
            )
        }

        if (restored.isNotEmpty()) {
            _downloads.update { current -> if (current.isEmpty()) restored else current }
        }
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
                        put("qualityValue", item.qualityValue)
                        put("qualityLabel", item.qualityLabel)
                        put("isAudio", item.isAudio)
                        put("status", item.status.name)
                        put("message", item.message ?: "")
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
        private val DEFAULT_QUALITY = QualityOption("720", "720p")
    }
}

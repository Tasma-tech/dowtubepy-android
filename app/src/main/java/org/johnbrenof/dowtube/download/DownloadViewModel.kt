package org.johnbrenof.dowtube.download

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class QualityOption(val value: String, val label: String)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _quality = MutableStateFlow(DEFAULT_QUALITY)
    val quality: StateFlow<QualityOption> = _quality.asStateFlow()

    private val qualityByTask = java.util.concurrent.ConcurrentHashMap<String, String>()

    val qualityOptions = listOf(
        QualityOption("360", "360p"),
        QualityOption("720", "720p"),
        QualityOption("1080", "1080p"),
        QualityOption("audio", "Só áudio"),
    )

    fun onUrlChange(url: String) {
        _urlInput.value = url
    }

    fun onQualityChange(option: QualityOption) {
        _quality.value = option
    }

    fun startDownload() {
        val url = _urlInput.value.trim()
        if (url.isEmpty()) return

        val taskId = UUID.randomUUID().toString().take(8)
        qualityByTask[taskId] = _quality.value.value

        _downloads.update { list ->
            list + DownloadItem(id = taskId, url = url)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = outputDirectory().absolutePath
            val ffmpegPath = FFmpegResolver.resolve(getApplication())

            DownloadEngine.submit(
                taskId = taskId,
                url = url,
                quality = _quality.value.value,
                outputDir = outputDir,
                ffmpegPath = ffmpegPath,
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
                val isVideo = qualityOf(taskId) != "audio"
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

    private fun qualityOf(taskId: String): String = qualityByTask[taskId] ?: "720"

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

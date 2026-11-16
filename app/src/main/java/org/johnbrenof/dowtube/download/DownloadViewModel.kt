package org.johnbrenof.dowtube.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.johnbrenof.dowtube.settings.AppSettings
import org.johnbrenof.dowtube.util.UrlRules

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<Int>>(emptySet())
    val selectedItems: StateFlow<Set<Int>> = _selectedItems.asStateFlow()

    private var probeJob: Job? = null

    val downloads get() = DownloadRepository.downloads

    init {
        DownloadRepository.init(application)
    }

    fun onUrlChange(url: String) {
        _urlInput.value = url
        scheduleProbe(url)
    }

    fun paste(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _urlInput.value = trimmed
        scheduleProbe(trimmed)
    }

    private fun scheduleProbe(url: String) {
        probeJob?.cancel()
        val trimmed = url.trim()
        if (!UrlRules.isValidUrl(trimmed)) {
            _preview.value = PreviewState()
            _selectedItems.value = emptySet()
            return
        }
        if (_preview.value.url == trimmed && _preview.value.info != null) {
            _preview.update { it.copy(url = trimmed, loading = false) }
            return
        }
        _preview.value = PreviewState(url = trimmed, loading = true)
        _selectedItems.value = emptySet()
        probeJob = viewModelScope.launch {
            delay(450)
            val result = withContext(Dispatchers.IO) {
                runCatching { DownloadEngine.probe(trimmed) }.getOrNull()
            }
            if (_urlInput.value.trim() != trimmed) return@launch
            _preview.value = PreviewState(url = trimmed, loading = false, info = result)
            _selectedItems.value = result?.items.orEmpty().mapTo(HashSet()) { it.position }
        }
    }

    fun toggleItem(position: Int) {
        _selectedItems.update { current ->
            if (position in current) current - position else current + position
        }
    }

    fun setSelected(positions: Set<Int>) {
        _selectedItems.value = positions
    }

    fun startDownload(
        url: String,
        media: String,
        quality: String,
        playlist: Boolean,
    ) {
        val trimmed = url.trim()
        if (!UrlRules.isValidUrl(trimmed)) return
        val preview = _preview.value.takeIf { it.url == trimmed }?.info
        val items = preview?.items.orEmpty()
        val selection = _selectedItems.value
        if (items.isNotEmpty() && selection.isEmpty()) return
        val playlistItems = if (items.isEmpty()) {
            ""
        } else {
            selection.sorted().joinToString(",")
        }
        DownloadRepository.enqueue(trimmed, media, quality, playlist, preview, null, playlistItems)
        _urlInput.value = ""
        _preview.value = PreviewState()
        _selectedItems.value = emptySet()
    }

    fun retry(item: DownloadItem) = DownloadRepository.retry(item)

    fun remove(item: DownloadItem) = DownloadRepository.remove(item)

    fun clearFinished() = DownloadRepository.clearFinished()

    fun purgeOldHistory() = DownloadRepository.purgeOldHistory()

    fun pasteFromClipboard(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        _urlInput.value = value
        scheduleProbe(value)
    }

    override fun onCleared() {
        probeJob?.cancel()
        super.onCleared()
    }

    companion object {
        val DEFAULT_MAX_QUALITY get() = AppSettings.maxQualityVideo.value
    }
}

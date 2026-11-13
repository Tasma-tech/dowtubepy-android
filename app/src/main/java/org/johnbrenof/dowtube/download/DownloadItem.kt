package org.johnbrenof.dowtube.download

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String = url,
    val qualityValue: String = "720",
    val qualityLabel: String = "720p",
    val isAudio: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val message: String? = null,
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    ERROR,
}

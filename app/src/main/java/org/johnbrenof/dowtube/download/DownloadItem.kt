package org.johnbrenof.dowtube.download

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String = url,
    val media: String = MEDIA_VIDEO,
    val quality: String = QUALITY_MAX,
    val playlist: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val message: String? = null,
    val paths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isAudio: Boolean
        get() = media == MEDIA_AUDIO

    companion object {
        const val MEDIA_VIDEO = "video"
        const val MEDIA_AUDIO = "audio"
        const val QUALITY_MAX = "max"
        const val QUALITY_MEDIUM = "medium"
        const val QUALITY_LOW = "low"
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    ERROR,
}

package org.johnbrenof.dowtube.download

data class PreviewInfo(
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val duration: Long,
    val entries: Int,
    val isPlaylist: Boolean,
)

data class PreviewState(
    val url: String = "",
    val loading: Boolean = false,
    val info: PreviewInfo? = null,
)

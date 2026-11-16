package org.johnbrenof.dowtube.download

data class PreviewEntry(
    val position: Int,
    val id: String,
    val title: String,
    val thumbnail: String,
    val duration: Long,
)

data class PreviewInfo(
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val duration: Long,
    val entries: Int,
    val isPlaylist: Boolean,
    val items: List<PreviewEntry> = emptyList(),
)

data class PreviewState(
    val url: String = "",
    val loading: Boolean = false,
    val info: PreviewInfo? = null,
)

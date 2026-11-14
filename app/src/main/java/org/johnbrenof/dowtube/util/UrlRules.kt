package org.johnbrenof.dowtube.util

object UrlRules {

    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        val body = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("//")
        return body.contains('.') && body.length > 3
    }

    fun looksLikePlaylist(url: String): Boolean {
        val value = url.lowercase()
        return value.contains("list=") ||
            value.contains("/playlist") ||
            value.contains("/set?") ||
            value.contains("/album") ||
            value.contains("/mixes")
    }

    fun normalize(raw: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("//") -> "https:$raw"
        else -> "https://$raw"
    }
}

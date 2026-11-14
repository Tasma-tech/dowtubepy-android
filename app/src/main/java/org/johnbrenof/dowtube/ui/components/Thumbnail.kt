package org.johnbrenof.dowtube.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.LruCache

private val imageCache = object : LruCache<String, ImageBitmap>(48) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = 1
}

@Composable
fun Thumbnail(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf(imageCache.get(url)) }

    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadImage(url) } ?: return@LaunchedEffect
        imageCache.put(url, loaded)
        bitmap = loaded
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else if (url.isBlank()) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun loadImage(url: String): ImageBitmap? = try {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.instanceFollowRedirects = true
    connection.inputStream.use { stream ->
        val bytes = stream.readBytes()
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

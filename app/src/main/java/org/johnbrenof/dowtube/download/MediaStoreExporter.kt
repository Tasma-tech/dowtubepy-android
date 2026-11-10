package org.johnbrenof.dowtube.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object MediaStoreExporter {

    private const val TAG = "MediaStoreExporter"
    private const val DIR_NAME = "DowTube"

    fun export(context: Context, source: File, isVideo: Boolean): String? {
        if (!source.exists() || source.length() == 0L) {
            Log.w(TAG, "Arquivo de origem ausente ou vazio: ${source.absolutePath}")
            return null
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(context, source, isVideo)
            } else {
                exportToPublicDir(source, isVideo)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao exportar ${source.name}; mantendo no dir do app.", e)
            null
        }
    }

    private fun exportViaMediaStore(
        context: Context,
        source: File,
        isVideo: Boolean,
    ): String? {
        val resolver = context.contentResolver
        val relativePath = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/$DIR_NAME"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME"
        }
        val visible = "/storage/emulated/0/$relativePath/${source.name}"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(source.name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
        if (uri == null) {
            Log.w(TAG, "MediaStore.insert devolveu null para ${source.name}")
            return null
        }

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        try {
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            if (!File(visible).exists()) {
                Log.w(TAG, "Falha ao finalizar ${source.name} no MediaStore", e)
                resolver.delete(uri, null, null)
                return null
            }
            Log.w(TAG, "Entrada já finalizada pelo MediaStore; seguindo.", e)
        }

        source.delete()
        Log.i(TAG, "Exportado para $visible")
        return visible
    }

    private fun exportToPublicDir(source: File, isVideo: Boolean): String? {
        val base = if (isVideo) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val dir = File(base, DIR_NAME).apply { mkdirs() }
        val target = File(dir, source.name)

        val finalTarget = if (target.exists()) {
            val dot = source.name.lastIndexOf('.')
            val stem = if (dot > 0) source.name.substring(0, dot) else source.name
            val ext = if (dot > 0) source.name.substring(dot) else ""
            var n = 1
            var candidate: File
            do {
                candidate = File(dir, "$stem ($n)$ext")
                n++
            } while (candidate.exists())
            candidate
        } else {
            target
        }

        source.copyTo(finalTarget, overwrite = true)
        source.delete()
        Log.i(TAG, "Exportado para ${finalTarget.absolutePath}")
        return finalTarget.absolutePath
    }

    private fun mimeTypeFor(name: String): String = when {
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".mkv", true) -> "video/x-matroska"
        name.endsWith(".m4a", true) -> "audio/mp4"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".opus", true) -> "audio/ogg"
        name.endsWith(".ogg", true) -> "audio/ogg"
        else -> "application/octet-stream"
    }
}

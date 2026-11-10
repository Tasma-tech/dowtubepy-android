package org.johnbrenof.dowtube.download

import android.content.Context
import android.util.Log
import java.io.File

object FFmpegResolver {

    private const val TAG = "FFmpegResolver"
    private const val BINARY_NAME = "libffmpeg.so"

    fun resolve(context: Context): String? = try {
        val file = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        when {
            !file.exists() -> {
                Log.i(TAG, "FFmpeg não empacotado em jniLibs; mesclagem A/V desativada.")
                null
            }
            !file.canExecute() -> {
                file.setExecutable(true, false)
                if (file.canExecute()) file.absolutePath else {
                    Log.w(TAG, "FFmpeg presente mas sem permissão de exec em ${file.absolutePath}")
                    null
                }
            }
            else -> file.absolutePath
        }
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao localizar o FFmpeg; seguindo sem mesclagem.", e)
        null
    }
}

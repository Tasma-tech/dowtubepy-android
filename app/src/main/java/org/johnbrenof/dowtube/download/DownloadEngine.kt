package org.johnbrenof.dowtube.download

import android.util.Log
import com.chaquo.python.Python
import java.util.concurrent.Executors

object DownloadEngine {

    private const val TAG = "DownloadEngine"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "python-bridge").apply { isDaemon = true }
    }

    fun submit(
        taskId: String,
        url: String,
        quality: String,
        outputDir: String,
        ffmpegPath: String?,
        listener: DownloadListener,
    ) {
        executor.execute {
            try {
                if (!Python.isStarted()) {
                    listener.onError(taskId, "Python não foi iniciado (PyApplication ausente).")
                    return@execute
                }
                val downloader = Python.getInstance().getModule("downloader")
                downloader.callAttr(
                    "download",
                    taskId,
                    url,
                    quality,
                    outputDir,
                    ffmpegPath,
                    listener,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Falha na ponte Python para a task $taskId", t)
                listener.onError(taskId, t.message ?: t.toString())
            }
        }
    }
}

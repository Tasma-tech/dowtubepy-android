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
        media: String,
        quality: String,
        playlist: Boolean,
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
                    media,
                    quality,
                    playlist,
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

    fun probe(url: String): PreviewInfo? {
        if (!Python.isStarted()) return null
        return try {
            val downloader = Python.getInstance().getModule("downloader")
            val raw = downloader.callAttr("probe", url)?.toString() ?: return null
            val json = org.json.JSONObject(raw)
            PreviewInfo(
                title = json.optString("title"),
                uploader = json.optString("uploader"),
                thumbnail = json.optString("thumbnail"),
                duration = json.optLong("duration"),
                entries = json.optInt("count"),
                isPlaylist = json.optBoolean("playlist"),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao sondar $url", t)
            null
        }
    }
}

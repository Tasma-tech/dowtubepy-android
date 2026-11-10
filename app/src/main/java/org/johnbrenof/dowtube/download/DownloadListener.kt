package org.johnbrenof.dowtube.download

interface DownloadListener {
    fun onProgress(taskId: String, percent: Double, title: String)

    fun onComplete(taskId: String, filePath: String)

    fun onError(taskId: String, message: String)
}

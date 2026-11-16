package org.johnbrenof.dowtube.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.atomic.AtomicInteger
import org.johnbrenof.dowtube.MainActivity
import org.johnbrenof.dowtube.R

class DownloadService : Service() {

    private val activeTasks = AtomicInteger(0)
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        DownloadRepository.init(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopIfIdle()
            return START_NOT_STICKY
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val url = intent.getStringExtra(EXTRA_URL)
        if (taskId == null || url.isNullOrBlank()) {
            stopIfIdle()
            return START_NOT_STICKY
        }

        val media = intent.getStringExtra(EXTRA_MEDIA) ?: DownloadItem.MEDIA_VIDEO
        val quality = intent.getStringExtra(EXTRA_QUALITY) ?: DownloadItem.QUALITY_MAX
        val playlist = intent.getBooleanExtra(EXTRA_PLAYLIST, false)
        val playlistItems = intent.getStringExtra(EXTRA_PLAYLIST_ITEMS).orEmpty()

        val wasIdle = activeTasks.getAndIncrement() == 0
        startForegroundIfNeeded(wasIdle)

        DownloadEngine.submit(
            taskId = taskId,
            url = url,
            media = media,
            quality = quality,
            playlist = playlist,
            playlistItems = playlistItems,
            outputDir = DownloadRepository.outputDirectory().absolutePath,
            ffmpegPath = FFmpegResolver.resolve(this),
            listener = ServiceListener(taskId, media),
        )

        val item = DownloadRepository.find(taskId)
        if (wasIdle || item != null) {
            notifyProgress(item?.title ?: url, item?.progress ?: 0f, item, true)
        }

        return START_NOT_STICKY
    }

    private inner class ServiceListener(
        private val taskId: String,
        private val media: String,
    ) : DownloadListener {

        override fun onProgress(taskId: String, percent: Double, title: String) {
            if (taskId != this.taskId) return
            DownloadRepository.onProgress(taskId, percent, title)
            val item = DownloadRepository.find(taskId)
            notifyProgress(title, item?.progress ?: 0f, item, false)
        }

        override fun onComplete(taskId: String, filePath: String) {
            if (taskId != this.taskId) return
            DownloadRepository.complete(taskId, filePath) { item ->
                notifyDone(taskId, item)
                finishTask()
            }
        }

        override fun onError(taskId: String, message: String) {
            if (taskId != this.taskId) return
            DownloadRepository.fail(taskId, message)
            notifyDone(taskId, DownloadRepository.find(taskId))
            finishTask()
        }
    }

    private fun finishTask() {
        if (activeTasks.decrementAndGet() <= 0) {
            activeTasks.set(0)
            stopForegroundAndSelf()
        } else {
            val next = DownloadRepository.downloads.value.firstOrNull {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
            notifyProgress(next?.title.orEmpty(), next?.progress ?: 0f, next, true)
        }
    }

    private fun startForegroundIfNeeded(startNew: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_ID,
                buildProgressNotification(getString(R.string.notif_starting), 0f, null, true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else if (startNew || Build.VERSION.SDK_INT >= 29) {
            startForeground(
                FOREGROUND_ID,
                buildProgressNotification(getString(R.string.notif_starting), 0f, null, true),
            )
        }
    }

    private fun stopForegroundAndSelf() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun stopIfIdle() {
        if (activeTasks.get() <= 0) stopForegroundAndSelf()
    }

    private fun notifyProgress(
        title: String,
        progress: Float,
        item: DownloadItem?,
        indeterminate: Boolean,
    ) {
        notificationManager?.notify(
            FOREGROUND_ID,
            buildProgressNotification(title, progress, item, indeterminate),
        )
    }

    private fun buildProgressNotification(
        title: String,
        progress: Float,
        item: DownloadItem?,
        indeterminate: Boolean,
    ): Notification {
        val percent = (progress * 100).toInt()
        val detail = when {
            item == null -> getString(R.string.notif_starting)
            item.status == DownloadStatus.QUEUED -> getString(R.string.status_queued)
            else -> if (indeterminate) {
                getString(R.string.status_downloading)
            } else {
                getString(R.string.notif_progress, percent)
            }
        }
        val subline = if (item != null) {
            "$detail · ${mediaLabel(item.media)}"
        } else {
            detail
        }

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(subline)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .setColor(0xFFE11D48.toInt())
            .build()
    }

    private fun notifyDone(taskId: String, item: DownloadItem?) {
        val manager = notificationManager ?: return
        if (item == null) return

        val failed = item.status == DownloadStatus.ERROR
        val text = when {
            failed -> item.message ?: getString(R.string.status_error)
            item.paths.size > 1 -> getString(R.string.notif_done_count, item.paths.size)
            else -> getString(R.string.status_completed)
        }

        val tapIntent = if (!failed && item.paths.isNotEmpty()) {
            openFileIntent(item.paths.first())
        } else {
            openAppIntent()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(if (failed) R.drawable.ic_stat_download else R.drawable.ic_stat_done)
            .setContentTitle(item.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent)
            .setColor(if (failed) 0xFFE11D48.toInt() else 0xFFE11D48.toInt())
            .build()

        manager.notify(taskId.hashCode() or 0x40000000, notification)
    }

    private fun mediaLabel(media: String): String =
        getString(if (media == DownloadItem.MEDIA_AUDIO) R.string.type_audio else R.string.type_video)

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openFileIntent(path: String): PendingIntent {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                java.io.File(path),
            )
            val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeFor(path))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PendingIntent.getActivity(
                this,
                path.hashCode(),
                view,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } catch (e: Exception) {
            openAppIntent()
        }
    }

    private fun mimeTypeFor(name: String): String = when {
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".mkv", true) -> "video/x-matroska"
        name.endsWith(".m4a", true) -> "audio/mp4"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".opus", true) -> "audio/ogg"
        name.endsWith(".ogg", true) -> "audio/ogg"
        else -> "*/*"
    }

    private fun createChannels() {
        val manager = notificationManager ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            getString(R.string.notif_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_progress_desc)
            setShowBadge(false)
        }

        val done = NotificationChannel(
            CHANNEL_DONE,
            getString(R.string.notif_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notif_channel_done_desc)
        }

        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(done)
    }

    companion object {
        const val ACTION_START = "org.johnbrenof.dowtube.download.START"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_URL = "url"
        const val EXTRA_MEDIA = "media"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_PLAYLIST_ITEMS = "playlist_items"

        private const val FOREGROUND_ID = 1001
        private const val CHANNEL_PROGRESS = "download_progress"
        private const val CHANNEL_DONE = "download_done"
    }
}

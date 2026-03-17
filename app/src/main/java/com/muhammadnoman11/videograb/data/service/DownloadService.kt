package com.muhammadnoman11.videograb.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.muhammadnoman11.videograb.ui.MainActivity
import com.muhammadnoman11.videograb.core.db.DownloadDao
import com.muhammadnoman11.videograb.core.util.resolveFileUri
import com.muhammadnoman11.videograb.domain.model.DownloadEntity
import com.muhammadnoman11.videograb.domain.model.DownloadStatus
import com.muhammadnoman11.videograb.data.worker.DownloadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import javax.inject.Inject

/*
 * Copyright 2026 Muhammad Noman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var dao: DownloadDao

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // IDs of downloads that have already triggered a "complete" notification
    private val notifiedCompleted = mutableSetOf<Int>()
    private var completionIdCounter = 9200

    companion object {
        private const val CH_PROGRESS = "ch_progress"
        private const val CH_COMPLETE = "ch_complete"
        const val NOTIF_FOREGROUND = 9001

        const val ACTION_PAUSE = "com.videodownloader.PAUSE"
        const val ACTION_RESUME = "com.videodownloader.RESUME"
        const val ACTION_CANCEL = "com.videodownloader.CANCEL"
        const val ACTION_PLAY = "com.videodownloader.PLAY"

        const val EXTRA_ID = "extra_download_id"
        const val EXTRA_PATH = "extra_file_path"
        const val EXTRA_IS_AUDIO = "extra_is_audio"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, DownloadService::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_FOREGROUND, idleNotif())
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_PAUSE -> if (downloadId != -1) handlePause(downloadId)
            ACTION_RESUME -> if (downloadId != -1) handleResume(downloadId)
            ACTION_CANCEL -> if (downloadId != -1) handleCancel(downloadId)
            ACTION_PLAY -> {
                val path = intent.getStringExtra(EXTRA_PATH) ?: return START_STICKY
                val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
                openFile(path, isAudio)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy(); scope.cancel()
    }

    // Observation loop
    private fun observeDownloads() {
        scope.launch {
            dao.getAllDownloads().collectLatest { list ->

                // Fire one-shot completion notifications (within the last 30s)
                list.filter { dl ->
                    dl.status == DownloadStatus.COMPLETED.name &&
                            dl.id !in notifiedCompleted &&
                            System.currentTimeMillis() - (dl.completedAt ?: 0L) < 30_000L
                }.forEach { dl ->
                    notifiedCompleted.add(dl.id)
                    nm.notify(
                        completionIdCounter++,
                        completeNotif(dl.title, dl.filePath, dl.format == "MP3")
                    )
                }

                // Active downloads
                val active = list.filter {
                    it.status in listOf(
                        DownloadStatus.QUEUED.name,
                        DownloadStatus.DOWNLOADING.name,
                        DownloadStatus.PAUSED.name
                    )
                }

                if (active.isEmpty()) {
                    nm.notify(NOTIF_FOREGROUND, idleNotif())
                    delay(1500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    else
                        @Suppress("DEPRECATION") stopForeground(true)
                    stopSelf()
                    return@collectLatest
                }

                // Primary = DOWNLOADING with highest progress, else first active
                val primary = active
                    .filter { it.status == DownloadStatus.DOWNLOADING.name }
                    .maxByOrNull { it.progress } ?: active.first()

                nm.notify(
                    NOTIF_FOREGROUND,
                    if (active.size == 1) singleDownloadNotif(primary)
                    else multiDownloadNotif(active.size, primary)
                )
            }
        }
    }

    // Notification builders
    private fun idleNotif(): Notification =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("VideoGrab")
            .setContentText("Ready")
            .setOngoing(false).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun singleDownloadNotif(dl: DownloadEntity): Notification {
        val status = DownloadStatus.valueOf(dl.status)
        val openPi = openAppPi()

        val builder = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(
                if (status == DownloadStatus.PAUSED) android.R.drawable.ic_media_pause
                else android.R.drawable.stat_sys_download
            )
            .setContentTitle(dl.title.take(50))
            .setContentIntent(openPi)
            .setOngoing(status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (status) {
            DownloadStatus.QUEUED -> {
                builder.setContentText("Queued — waiting to start")
                builder.setProgress(0, 0, true)
            }

            DownloadStatus.DOWNLOADING -> {
                val sub = when {
                    dl.progress >= 99 -> "Merging video + audio…"
                    dl.totalBytes > 0 -> "${dl.progress}%  •  ${fmtBytes(dl.downloadedBytes)} / ${
                        fmtBytes(
                            dl.totalBytes
                        )
                    }"

                    else -> "${dl.progress}%"
                }
                builder.setContentText(sub)
                builder.setProgress(100, dl.progress, false)
            }

            DownloadStatus.PAUSED -> {
                builder.setContentText("Paused — ${dl.progress}%")
                builder.setProgress(100, dl.progress, false)
            }

            else -> builder.setContentText(status.name)
        }

        when (status) {
            DownloadStatus.DOWNLOADING -> {
                builder.addAction(
                    android.R.drawable.ic_media_pause, "Pause",
                    actionPi(ACTION_PAUSE, dl.id, 100 + dl.id)
                )
                builder.addAction(
                    android.R.drawable.ic_delete, "Cancel",
                    actionPi(ACTION_CANCEL, dl.id, 200 + dl.id)
                )
            }

            DownloadStatus.PAUSED -> {
                builder.addAction(
                    android.R.drawable.ic_media_play, "Resume",
                    actionPi(ACTION_RESUME, dl.id, 300 + dl.id)
                )
                builder.addAction(
                    android.R.drawable.ic_delete, "Cancel",
                    actionPi(ACTION_CANCEL, dl.id, 200 + dl.id)
                )
            }

            DownloadStatus.QUEUED -> {
                builder.addAction(
                    android.R.drawable.ic_delete, "Cancel",
                    actionPi(ACTION_CANCEL, dl.id, 200 + dl.id)
                )
            }

            else -> {}
        }
        return builder.build()
    }

    private fun multiDownloadNotif(count: Int, primary: DownloadEntity): Notification {
        val sub = when (DownloadStatus.valueOf(primary.status)) {
            DownloadStatus.DOWNLOADING -> "${primary.title.take(30)}… — ${primary.progress}%"
            else -> "${primary.title.take(30)}…"
        }
        return NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$count downloads in progress")
            .setContentText(sub)
            .setProgress(
                100, primary.progress,
                DownloadStatus.valueOf(primary.status) == DownloadStatus.QUEUED
            )
            .setContentIntent(openAppPi())
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun completeNotif(title: String, filePath: String, isAudio: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CH_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete ✓")
            .setContentText(title.take(50))
            .setContentIntent(openAppPi(filePath.hashCode()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (filePath.isNotBlank()) {
            val playPi = PendingIntent.getService(
                this, filePath.hashCode() + 1,
                Intent(this, DownloadService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_PATH, filePath)
                    putExtra(EXTRA_IS_AUDIO, isAudio)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                if (isAudio) "Play" else "Play Video", playPi
            )
        }
        return builder.build()
    }

    //  Action handlers
    private fun handlePause(id: Int) {
        scope.launch {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("download_$id")
            dao.markPaused(id)
        }
    }

    private fun handleResume(id: Int) {
        scope.launch {
            val entity = dao.getById(id) ?: return@launch
            dao.update(entity.copy(status = DownloadStatus.QUEUED.name))
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "download_$id", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to id))
                    .addTag("download_$id")
                    .build()
            )
        }
    }

    private fun handleCancel(id: Int) {
        scope.launch {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("download_$id")
            // Clean up temp files
            val tempDir = File(applicationContext.getExternalFilesDir(null), "temp")
            tempDir.listFiles()
                ?.filter { it.name.startsWith("dl_${id}.") || it.name.startsWith("dl_${id}_") }
                ?.forEach { it.delete() }
            dao.markCancelled(id)
        }
    }

    private fun openFile(filePath: String, isAudio: Boolean) {
        try {
            val uri = resolveFileUri(filePath) ?: run {
                Log.w("DownloadService", "File not found: $filePath")
                return
            }
            val mime = if (isAudio) "audio/mpeg" else "video/mp4"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (packageManager.resolveActivity(intent, 0) != null) {
                startActivity(intent)
            } else {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
                        flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                    if (isAudio) "Play audio with…" else "Play video with…"
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                startActivity(chooser)
            }
        } catch (e: Exception) {
            Log.e("DownloadService", "openFile failed: $filePath", e)
        }
    }

    //PendingIntent helpers
    private fun openAppPi(reqCode: Int = 0): PendingIntent = PendingIntent.getActivity(
        this, reqCode,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionPi(action: String, downloadId: Int, reqCode: Int): PendingIntent =
        PendingIntent.getService(
            this, reqCode,
            Intent(this, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, downloadId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CH_PROGRESS,
                        "Downloads",
                        NotificationManager.IMPORTANCE_LOW
                    )
                        .apply { setSound(null, null); enableVibration(false) },
                    NotificationChannel(
                        CH_COMPLETE,
                        "Download Complete",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            )
        }
    }

    private fun fmtBytes(b: Long): String = when {
        b >= 1_073_741_824 -> "%.1f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1_024 -> "%.1f KB".format(b / 1_024.0)
        else -> "$b B"
    }
}

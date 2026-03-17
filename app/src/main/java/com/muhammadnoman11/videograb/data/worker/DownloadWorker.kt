package com.muhammadnoman11.videograb.data.worker

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.muhammadnoman11.videograb.VideoGrabApp
import com.muhammadnoman11.videograb.core.db.DownloadDao
import com.muhammadnoman11.videograb.domain.model.DownloadStatus
import com.muhammadnoman11.videograb.data.storage.DownloadStorageManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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


private const val TAG = "DownloadWorker"

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val storage: DownloadStorageManager
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val NOTIF_ID = 8001
        private const val CHANNEL = "worker_bg"
    }

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_DOWNLOAD_ID, -1)
        if (id == -1) return Result.failure()

        val entity = dao.getById(id) ?: return Result.failure()

        createChannel()
        setForeground(foregroundInfo())

        val app = context.applicationContext as VideoGrabApp

        // Wait for yt-dlp init + update to finish (max 90s)
        if (!app.isReady.value) {
            val ready = withTimeoutOrNull(90_000L) { app.isReady.first { it } }
            if (ready == null) {
                Log.e(TAG, "yt-dlp not ready after 90s")
                return Result.retry()
            }
        }

        if (!app.isYtDlpInitialized) return Result.retry()

        Log.d(TAG, "yt-dlp v${app.getVersion()}")

        return try {
            download(entity, app.ffmpegReady)
            Result.success()
        } catch (e: Exception) {
            if (isStopped) Result.success()
            else {
                Log.e(TAG, "Download $id failed", e)
                dao.markFailed(id, DownloadStatus.FAILED.name, e.message)
                Result.retry()
            }
        }
    }

    // Core download logic
    private suspend fun download(
        entity: com.muhammadnoman11.videograb.domain.model.DownloadEntity,
        ffmpegOk: Boolean
    ) =
        withContext(Dispatchers.IO) {

            val isAudio = entity.format == "MP3"
            val tempDir = storage.tempDir

            Log.d(
                TAG,
                "▶ id=${entity.id} quality=${entity.quality} isAudio=$isAudio ffmpegOk=$ffmpegOk"
            )

            val before = tempDir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
            val outputTemplate = "${tempDir.absolutePath}/dl_${entity.id}.%(ext)s"

            dao.updateProgress(entity.id, DownloadStatus.DOWNLOADING.name, 0, 0, 0)

            // Write all yt-dlp options to a config file.
            // This bypasses the library's broken argument-passing on Android 10,
            // where addOption("-f", value) puts the value in the URL position.
            val cfg = writeConfig(entity.id, outputTemplate, isAudio, ffmpegOk, entity.quality)
            Log.d(TAG, "Config:\n${cfg.readText()}")

            val request = YoutubeDLRequest(entity.url).apply {
                addOption("--config-location", cfg.absolutePath)
                addOption("--ignore-config")
            }

            val processId = "dl_${entity.id}_${System.currentTimeMillis()}"
            val startPct = if (entity.totalBytes > 0)
                ((entity.downloadedBytes * 100) / entity.totalBytes).toInt().coerceIn(0, 99) else 0

            dao.updateProgress(
                entity.id, DownloadStatus.DOWNLOADING.name,
                startPct, entity.downloadedBytes, entity.totalBytes
            )

            var lastWrite = 0L

            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val thread = Thread({
                        try {
                            YoutubeDL.getInstance()
                                .execute(request, processId) { progress, _, line ->
                                    if (isStopped) {
                                        YoutubeDL.getInstance().destroyProcessById(processId)
                                        return@execute
                                    }

                                    val merge = line.contains("[ffmpeg]", true) ||
                                            line.contains("[Merger]", true) ||
                                            line.contains("Merging", true)

                                    val p: Int;
                                    val d: Long;
                                    val t: Long
                                    if (merge) {
                                        p = 99; d = entity.totalBytes; t = entity.totalBytes
                                    } else {
                                        p = progress.toInt().coerceIn(0, 99)
                                        val (d2, t2) = parseProgress(line)
                                        d = if (d2 > 0) d2 else entity.downloadedBytes
                                        t = if (t2 > 0) t2 else entity.totalBytes
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastWrite > 500) {
                                        lastWrite = now
                                        kotlinx.coroutines.runBlocking {
                                            dao.updateProgress(
                                                entity.id,
                                                DownloadStatus.DOWNLOADING.name, p, d, t
                                            )
                                        }
                                    }
                                }
                            if (cont.isActive) cont.resume(Unit)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        } finally {
                            cfg.delete()
                        }
                    }, "ytdlp-${entity.id}")
                    thread.isDaemon = true
                    thread.start()

                    cont.invokeOnCancellation {
                        YoutubeDL.getInstance().destroyProcessById(processId)
                        cfg.delete()
                       runBlocking {
                            dao.getById(entity.id)?.let { e ->
                                if (e.status == DownloadStatus.DOWNLOADING.name)
                                    dao.update(e.copy(status = DownloadStatus.PAUSED.name))
                            }
                        }
                    }
                }
            } catch (e: YoutubeDLException) {
                if (isStopped) return@withContext
                throw Exception("yt-dlp error: ${e.message}")
            }

            if (isStopped) return@withContext

            dao.updateProgress(
                entity.id, DownloadStatus.DOWNLOADING.name,
                100, entity.totalBytes, entity.totalBytes
            )

            delay(1000) // Allow file system to flush

            // Find the output file
            val allFiles = tempDir.listFiles() ?: emptyArray()
            Log.d(TAG, "Temp dir after (${allFiles.size} files):")
            allFiles.forEach { f ->
                Log.d(
                    TAG,
                    "  ${if (f.absolutePath !in before) "NEW" else "old"} ${f.name} ${f.length()}B"
                )
            }

            val newFiles = allFiles.filter { it.absolutePath !in before }
            val output = findOutput(entity.id, newFiles, allFiles)
                ?: throw Exception("Output not found.\n${allFiles.joinToString("\n") { "  ${it.name} ${it.length()}B" }}")

            Log.d(TAG, "Output: ${output.name}  ${output.length()}B")
            if (output.length() == 0L) {
                output.delete(); throw Exception("Output file is empty.")
            }

            // Rename to the standard target extension
            val targetExt = if (isAudio) "mp3" else "mp4"
            val fileToMove = if (output.extension.lowercase() != targetExt) {
                Log.d(TAG, "Renaming .${output.extension} → .$targetExt")
                val renamed = File(tempDir, "dl_${entity.id}_final.$targetExt")
                if (output.renameTo(renamed)) renamed
                else {
                    output.copyTo(renamed, true); output.delete(); renamed
                }
            } else output

            val finalPath = storage.moveToFinalDestination(
                tempFile = fileToMove,
                fileName = "${storage.sanitize(entity.title)}.$targetExt",
                isAudio = isAudio
            )
            Log.d(TAG, "Final: $finalPath")

            // Clean up any leftover temp files for this download
            storage.cleanTempForDownload(entity.id)

            dao.markCompleted(
                entity.id, DownloadStatus.COMPLETED.name,
                finalPath, System.currentTimeMillis()
            )
        }

    // Config file

    /**
     * Writes all yt-dlp options to a text config file.
     *
     * All options (especially -f) go here — never via addOption() — because the
     * junkfood02 library wrapper on Android 10 passes option values as positional
     * URL arguments, causing "[generic] format-string is not a valid URL".
     * yt-dlp reads --config-location before the extractor runs, so the format
     * selector is always parsed correctly regardless of library version.
     *
     * Config file format: one flag per line, same as the command line.
     */
    private fun writeConfig(
        id: Int, outputTemplate: String,
        isAudio: Boolean, ffmpegOk: Boolean, quality: String
    ): File {
        val file = File(storage.tempDir, "dl_${id}_cfg.txt")
        val lines = buildList<String> {
            add("-o $outputTemplate")
            add("-c")                         // Resume partial downloads
            add("--no-playlist")
            add("--no-check-certificate")
            add("--retries 5")
            add("--fragment-retries 5")
            add("--output-na-placeholder \"\"")

            if (isAudio) {
                if (ffmpegOk) {
                    // ffmpeg present: convert downloaded audio to mp3
                    add("-x")
                    add("--audio-format mp3")
                    add("--audio-quality 0")
                } else {
                    // ffmpeg absent: download raw m4a/aac — renamed to .mp3 after download.
                    // Players read codec from the file header, not the extension.
                    add("-f bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio")
                }
            } else {
                if (ffmpegOk) {
                    // ffmpeg present: fetch best separate streams and merge
                    add("-f ${buildFmtMerge(quality)}")
                    add("--merge-output-format mp4")
                } else {
                    // ffmpeg absent: must use pre-muxed single-file formats only.
                    // "best" selects the best format with both video+audio in one file.
                    add("-f ${buildFmtNoMerge(quality)}")
                }
            }
        }
        file.writeText(lines.joinToString("\n"))
        return file
    }

    // Format selectors
    private fun buildFmtMerge(q: String): String {
        val h = heightOf(q)
        return if (h != null)
            "bestvideo[height<=$h][ext=mp4]+bestaudio[ext=m4a]/" +
                    "bestvideo[height<=$h][ext=mp4]+bestaudio/" +
                    "bestvideo[height<=$h]+bestaudio/best[height<=$h]"
        else
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
    }

    /** "best" = best single container with both video+audio — no ffmpeg needed */
    private fun buildFmtNoMerge(q: String): String {
        val h = heightOf(q)
        return if (h != null)
            "best[height<=$h][ext=mp4]/best[height<=$h]/best[ext=mp4]/best"
        else
            "best[ext=mp4]/best"
    }

    private fun heightOf(q: String) =
        Regex("(\\d+)p").find(q)?.groupValues?.get(1)?.toIntOrNull()

    // Output file finder

    private fun findOutput(id: Int, newFiles: List<File>, all: Array<File>): File? {
        val MEDIA =
            setOf("mp4", "mp3", "mkv", "webm", "m4a", "ogg", "opus", "avi", "ts", "aac", "flac")
        val SKIP = setOf(
            "part", "ytdl", "tmp", "json", "description",
            "annotations", "vtt", "srt", "ass", "lrc", "txt"
        )

        fun isFragment(f: File): Boolean {
            if (!f.name.startsWith("dl_${id}.f")) return false
            val after = f.name.removePrefix("dl_${id}.f")
            return after.firstOrNull()?.isDigit() == true ||
                    after.startsWith("dash", true) ||
                    after.startsWith("video", true) ||
                    after.startsWith("audio", true)
        }

        fun usable(f: File) = f.isFile && f.length() > 0 &&
                f.extension.lowercase() in MEDIA &&
                f.extension.lowercase() !in SKIP &&
                !isFragment(f)

        // Priority 1: new file with exact expected name
        listOf("mp4", "m4a", "mp3", "mkv", "webm", "ts", "opus", "ogg", "aac").forEach { ext ->
            newFiles.firstOrNull { it.name == "dl_${id}.$ext" && usable(it) }
                ?.also { Log.d(TAG, "Found P1: ${it.name}"); return it }
        }

        // Priority 2: any new usable media file (largest = most likely main output)
        newFiles.filter { usable(it) }.maxByOrNull { it.length() }
            ?.also { Log.d(TAG, "Found P2: ${it.name}"); return it }

        // Priority 3: existing exact name (handles pre-existing partial file edge case)
        listOf("mp4", "m4a", "mp3", "mkv", "webm", "ts", "opus", "ogg", "aac").forEach { ext ->
            File(storage.tempDir, "dl_${id}.$ext").takeIf { usable(it) }
                ?.also { Log.d(TAG, "Found P3: ${it.name}"); return it }
        }

        return null
    }

    // Progress parsing

    private fun parseProgress(line: String): Pair<Long, Long> {
        return try {
            val ofIdx = line.indexOf(" of ")
            val atIdx = line.indexOf(" at ")
            if (ofIdx == -1 || atIdx == -1) return 0L to 0L
            val total = parseBytes(line.substring(ofIdx + 4, atIdx).trim())
            val pct = Regex("(\\d+\\.?\\d*)%").find(line)
                ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            (total * pct / 100.0).toLong() to total
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun parseBytes(s: String): Long {
        val m = Regex("([\\d.]+)\\s*(GiB|MiB|KiB|GB|MB|KB|B)", RegexOption.IGNORE_CASE)
            .find(s) ?: return 0L
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (m.groupValues[2].uppercase()) {
            "GIB", "GB" -> (v * 1_073_741_824).toLong()
            "MIB", "MB" -> (v * 1_048_576).toLong()
            "KIB", "KB" -> (v * 1_024).toLong()
            else -> v.toLong()
        }
    }

    // Worker foreground notification (hidden DownloadService owns the visible UI)

    private fun foregroundInfo(): ForegroundInfo {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("VideoGrab")
            .setContentText("Downloading…")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(NOTIF_ID, n)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch =
                NotificationChannel(CHANNEL, "Download Worker", NotificationManager.IMPORTANCE_MIN)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_SECRET
                    }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
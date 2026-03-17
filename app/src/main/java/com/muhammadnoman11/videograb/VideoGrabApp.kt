package com.muhammadnoman11.videograb


import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.muhammadnoman11.videograb.core.network.NetworkMonitor
import com.muhammadnoman11.videograb.data.storage.DownloadStorageManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
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


private const val TAG = "VideoDownloaderApp"

@HiltAndroidApp
class VideoGrabApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var storageManager: DownloadStorageManager

    /** True once yt-dlp init and update attempt have both completed. Workers wait on this. */
    val isReady = MutableStateFlow(false)

    /** User-facing status shown in the UI banner while updating. */
    val statusMessage = MutableStateFlow("Initializing…")

    /** True if ffmpeg binary executes cleanly on this device. */
    var ffmpegReady = false
        private set

    var isYtDlpInitialized = false
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: VideoGrabApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch { initYtDlp() }
        networkMonitor.start()
        appScope.launch { storageManager.cleanStale() }
    }

    override fun onTerminate() {
        super.onTerminate()
        networkMonitor.stop()
    }

    //  yt-dlp setup
    private suspend fun initYtDlp() {
        try {
            YoutubeDL.getInstance().init(this)

            try {
                FFmpeg.getInstance().init(this)
            } catch (e: YoutubeDLException) {
                Log.w(TAG, "FFmpeg module init failed (non-critical): ${e.message}")
            }

            isYtDlpInitialized = true
            Log.i(TAG, "yt-dlp initialised. Version: ${getVersion()}")

            // Update yt-dlp — required on Android 10 where the bundled binary
            // is too old to extract YouTube without a JS runtime
            statusMessage.value = "Updating yt-dlp…"
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(
                    this, YoutubeDL.UpdateChannel.NIGHTLY
                )
                Log.i(TAG, "yt-dlp update: $status  v${getVersion()}")
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed (non-critical): ${e.message}")
            }

            // Run actual ffmpeg binary smoke test — FFmpeg.init() only registers
            // the path and never fails, so we must execute it to know if it works.
            ffmpegReady = testFfmpegBinary()
            Log.i(TAG, "Ready. ffmpegReady=$ffmpegReady  v${getVersion()}")
            statusMessage.value = "Ready"

        } catch (e: YoutubeDLException) {
            Log.e(TAG, "yt-dlp init failed: ${e.message}")
            statusMessage.value = "Initialization failed"
        } finally {
            isReady.value = true // Always unblock workers
        }
    }

    /**
     * Execute [ffmpeg -version] to verify the binary works at runtime.
     *
     * On some Android 10 OEM builds the bundled ffmpeg crashes immediately due
     * to a libmediandk.so ABI mismatch. When this returns false, the worker
     * uses pre-muxed format selectors that don't require ffmpeg.
     *
     * Note: ffmpeg lives in noBackupFilesDir (not nativeLibraryDir), so it is
     * not subject to Android's W^X policy and can be run via ProcessBuilder.
     */
    private fun testFfmpegBinary(): Boolean {
        return try {
            val bin = findFfmpegBinary() ?: run {
                Log.w(TAG, "ffmpeg binary not found")
                return false
            }
            Log.d(TAG, "Testing ffmpeg: $bin")
            val p = ProcessBuilder(bin, "-version").redirectErrorStream(true).start()
            val finished = p.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly(); Log.w(TAG, "ffmpeg timed out"); return false
            }
            val ok = p.exitValue() == 0
            Log.d(TAG, "ffmpeg exit=${p.exitValue()} → ok=$ok")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg test threw: ${e.message}")
            false
        }
    }

    private fun findFfmpegBinary(): String? {
        val base = noBackupFilesDir
        listOf(
            "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg",
            "youtubedl-android/ffmpeg",
            "youtubedl-android/usr/bin/ffmpeg"
        ).map { File(base, it) }.forEach { f ->
            if (f.exists() && f.isFile) {
                if (!f.canExecute()) f.setExecutable(true, false)
                return f.absolutePath
            }
        }
        return base.walkTopDown().maxDepth(8)
            .firstOrNull { it.isFile && it.name == "ffmpeg" }
            ?.also { if (!it.canExecute()) it.setExecutable(true, false) }
            ?.absolutePath
    }

    fun getVersion(): String = try {
        YoutubeDL.getInstance().version(this) ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
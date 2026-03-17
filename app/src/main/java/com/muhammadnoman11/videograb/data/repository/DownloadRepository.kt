package com.muhammadnoman11.videograb.data.repository

import android.content.Context
import androidx.work.*
import com.muhammadnoman11.videograb.core.db.DownloadDao
import com.muhammadnoman11.videograb.domain.model.DownloadEntity
import com.muhammadnoman11.videograb.domain.model.DownloadStatus
import com.muhammadnoman11.videograb.domain.model.Quality
import com.muhammadnoman11.videograb.data.service.DownloadService
import com.muhammadnoman11.videograb.domain.extractor.YtDlpExtractor
import com.muhammadnoman11.videograb.data.worker.DownloadWorker
import com.muhammadnoman11.videograb.domain.model.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val workManager: WorkManager,
    private val extractor: YtDlpExtractor
) {

    // Reactive streams
    val activeDownloads: Flow<List<DownloadEntity>> = dao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = dao.getCompletedDownloads()

    // Info extraction
    suspend fun extractInfo(url: String): Result<StreamInfo> = extractor.extractStreamInfo(url)

    // Download lifecycle
    /** Persists a new download and enqueues the worker. Returns the new row ID. */
    suspend fun startDownload(
        url: String,
        title: String,
        thumbnailUrl: String,
        quality: Quality
    ): Int {
        val entity = DownloadEntity(
            title = title,
            url = url,
            thumbnailUrl = thumbnailUrl,
            format = quality.format.name,
            quality = quality.label,
            status = DownloadStatus.QUEUED.name
        )
        val id = dao.insert(entity).toInt()
        DownloadService.start(context)
        enqueue(id)
        return id
    }

    suspend fun pauseDownload(id: Int) {
        workManager.cancelUniqueWork("download_$id")
        dao.markPaused(id)
    }

    suspend fun resumeDownload(id: Int) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(status = DownloadStatus.QUEUED.name))
        DownloadService.start(context)
        enqueue(id)
    }

    suspend fun cancelDownload(id: Int) {
        workManager.cancelUniqueWork("download_$id")
        dao.markCancelled(id)
    }

    suspend fun deleteDownload(id: Int) {
        workManager.cancelUniqueWork("download_$id")
        dao.deleteById(id)
    }

    suspend fun getById(id: Int): DownloadEntity? = dao.getById(id)

    //  Internal
    private fun enqueue(downloadId: Int) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to downloadId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag("download_$downloadId")
            .addTag("all_downloads")
            .build()

        workManager.enqueueUniqueWork(
            "download_$downloadId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

package com.muhammadnoman11.videograb.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muhammadnoman11.videograb.domain.model.DownloadEntity
import kotlinx.coroutines.flow.Flow

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

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("""
        SELECT * FROM downloads
        WHERE status IN ('QUEUED','DOWNLOADING','PAUSED')
        ORDER BY createdAt ASC
    """)
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("""
        SELECT * FROM downloads
        WHERE status = 'COMPLETED'
        ORDER BY COALESCE(completedAt, createdAt) DESC
    """)
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>


    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Int): DownloadEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Int)


    @Query("""
        UPDATE downloads
        SET status = :status, progress = :progress,
            downloadedBytes = :downloaded, totalBytes = :total
        WHERE id = :id
    """)
    suspend fun updateProgress(id: Int, status: String, progress: Int, downloaded: Long, total: Long)

    @Query("""
        UPDATE downloads
        SET status = :status, filePath = :filePath, completedAt = :completedAt, progress = 100
        WHERE id = :id
    """)
    suspend fun markCompleted(id: Int, status: String, filePath: String, completedAt: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: Int, status: String, error: String?)

    @Query("UPDATE downloads SET status = 'PAUSED' WHERE id = :id")
    suspend fun markPaused(id: Int)

    @Query("UPDATE downloads SET status = 'CANCELLED', progress = 0, downloadedBytes = 0, totalBytes = 0 WHERE id = :id")
    suspend fun markCancelled(id: Int)
}
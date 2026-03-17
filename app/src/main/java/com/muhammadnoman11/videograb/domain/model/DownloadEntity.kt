package com.muhammadnoman11.videograb.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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


// Download entity stored in Room
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,             // Original page URL (permanent, used for re-extraction)
    val thumbnailUrl: String,
    val format: String,          // "MP4" or "MP3"
    val quality: String,         // e.g. "720p", "MP3 128kbps"
    val status: String = DownloadStatus.QUEUED.name,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
)
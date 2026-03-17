package com.muhammadnoman11.videograb.domain.model

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



// Quality option shown in the bottom sheet
data class Quality(
    val label: String,                          // "1080p", "720p", "MP3 128kbps"
    val resolution: String,                     // "1920x1080" or "Audio Only"
    val format: MediaFormat,
    val streamUrl: String,                      // CDN URL (expires) or page URL
    val audioUrl: String? = null,               // Separate audio URL for DASH streams
    val fileSizeMb: Double = 0.0,
    val isAudioOnly: Boolean = false,
    val httpHeaders: Map<String, String> = emptyMap()
)
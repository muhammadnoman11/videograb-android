package com.muhammadnoman11.videograb.domain.extractor


import android.content.Context
import android.util.Log
import com.muhammadnoman11.videograb.domain.model.MediaFormat
import com.muhammadnoman11.videograb.domain.model.Quality
import com.muhammadnoman11.videograb.domain.model.StreamInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_android.mapper.VideoFormat

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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


private const val TAG = "YtDlpExtractor"

@Singleton
class YtDlpExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Extracts metadata and available quality options for the given URL. */
    suspend fun extractStreamInfo(url: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.startsWith("http")) {
            return@withContext Result.failure(Exception("Invalid URL. Please paste a valid video link."))
        }
        try {
            val info: VideoInfo = YoutubeDL.getInstance().getInfo(clean)
            Log.d(TAG, "getInfo OK: ${info.title}  formats=${info.formats?.size ?: 0}")
            info.formats?.take(5)?.forEach { f ->
                Log.d(
                    TAG,
                    "  fmt id=${f.formatId} ext=${f.ext} vcodec=${f.vcodec} acodec=${f.acodec} h=${f.height}"
                )
            }
            Result.success(mapToStreamInfo(info, clean))
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "getInfo error: ${e.message}")
            Result.failure(friendlyError(e.message))
        } catch (e: Exception) {
            Log.e(TAG, "getInfo unexpected: ${e.message}")
            Result.failure(Exception("Could not fetch video: ${e.message}"))
        }
    }

    // Mapping
    private fun mapToStreamInfo(info: VideoInfo, pageUrl: String): StreamInfo {
        val qualities = mutableListOf<Quality>()
        val seen = mutableSetOf<String>()
        val formats = info.formats?.reversed() ?: emptyList()

        // Pass 1 — pre-muxed (video + audio in one stream, no ffmpeg needed)
        formats.forEach { fmt ->
            if (isMuxed(fmt) && fmt.url != null) {
                val label = videoLabel(fmt)
                if (seen.add("v_$label")) {
                    qualities += Quality(
                        label = label,
                        resolution = fmt.height?.let { "${fmt.width ?: "?"}x$it" } ?: label,
                        format = mediaFormat(fmt.ext),
                        streamUrl = fmt.url!!,
                        audioUrl = null,
                        fileSizeMb = fileSizeMb(fmt.fileSize),
                        isAudioOnly = false,
                        httpHeaders = fmt.httpHeaders ?: emptyMap()
                    )
                }
            }
        }

        // Pass 2 — DASH video-only (needs separate audio + ffmpeg merge)
        val bestAudioUrl = formats.firstOrNull { isAudioOnly(it) && it.url != null }?.url
        formats.forEach { fmt ->
            if (isVideoOnly(fmt) && fmt.url != null) {
                val h = fmt.height ?: return@forEach
                val label = "${h}p"
                if (seen.add("v_$label")) {
                    qualities += Quality(
                        label = label,
                        resolution = "${fmt.width ?: "?"}x$h",
                        format = mediaFormat(fmt.ext),
                        streamUrl = fmt.url!!,
                        audioUrl = bestAudioUrl,
                        fileSizeMb = fileSizeMb(fmt.fileSize),
                        isAudioOnly = false,
                        httpHeaders = fmt.httpHeaders ?: emptyMap()
                    )
                }
            }
        }

        // Pass 3 — audio-only
        formats.forEach { fmt ->
            if (isAudioOnly(fmt) && fmt.url != null) {
                val abr = fmt.abr?.toInt() ?: 0
                val label = if (abr > 0) "MP3 ${abr}kbps" else "MP3 Audio"
                if (seen.add("a_$label")) {
                    qualities += Quality(
                        label = label,
                        resolution = "Audio Only",
                        format = MediaFormat.MP3,
                        streamUrl = fmt.url!!,
                        fileSizeMb = fileSizeMb(fmt.fileSize),
                        isAudioOnly = true,
                        httpHeaders = fmt.httpHeaders ?: emptyMap()
                    )
                }
            }
        }

        // Fallback when no formats have direct URLs
        if (qualities.isEmpty()) {
            Log.w(TAG, "No direct-URL formats — using page URL fallback")
            qualities += listOf(
                Quality("1080p", "1920x1080", MediaFormat.MP4, pageUrl),
                Quality("720p", "1280x720", MediaFormat.MP4, pageUrl),
                Quality("480p", "854x480", MediaFormat.MP4, pageUrl),
                Quality("MP3 Audio", "Audio Only", MediaFormat.MP3, pageUrl, isAudioOnly = true)
            )
        }

        Log.d(
            TAG, "Mapped ${qualities.size} qualities: " +
                    "muxed=${qualities.count { !it.isAudioOnly && it.audioUrl == null }}  " +
                    "dash=${qualities.count { !it.isAudioOnly && it.audioUrl != null }}  " +
                    "audio=${qualities.count { it.isAudioOnly }}"
        )

        return StreamInfo(
            title = info.title?.ifBlank { null } ?: "Unknown",
            thumbnailUrl = info.thumbnail ?: "",
            duration = info.duration?.toLong() ?: 0L,
            uploaderName = info.uploader?.ifBlank { null } ?: "Unknown",
            availableQualities = qualities
        )
    }

    // Format classifier helpers
    private fun isMuxed(f: VideoFormat): Boolean {
        val v = f.vcodec?.lowercase()?.trim() ?: ""
        val a = f.acodec?.lowercase()?.trim() ?: ""
        return v.isNotEmpty() && v != "none" && v != "null" &&
                a.isNotEmpty() && a != "none" && a != "null"
    }

    private fun isVideoOnly(f: VideoFormat): Boolean {
        val v = f.vcodec?.lowercase()?.trim() ?: ""
        val a = f.acodec?.lowercase()?.trim() ?: ""
        return v.isNotEmpty() && v != "none" && v != "null" &&
                (a.isEmpty() || a == "none" || a == "null")
    }

    private fun isAudioOnly(f: VideoFormat): Boolean {
        val v = f.vcodec?.lowercase()?.trim() ?: ""
        val a = f.acodec?.lowercase()?.trim() ?: ""
        return (v.isEmpty() || v == "none" || v == "null") &&
                a.isNotEmpty() && a != "none" && a != "null"
    }

    private fun videoLabel(f: VideoFormat): String {
        val h = f.height
        val note = f.formatNote?.trim()?.ifBlank { null }
        return when {
            h != null && h > 0 -> "${h}p"
            note != null -> note
            f.format != null -> f.format!!
            else -> "Unknown"
        }
    }

    private fun fileSizeMb(size: Long?): Double =
        if (size != null && size > 0) size / 1_048_576.0 else 0.0

    private fun mediaFormat(ext: String?): MediaFormat = when (ext?.lowercase()) {
        "webm" -> MediaFormat.WEBM
        "m4a" -> MediaFormat.M4A
        "mp3" -> MediaFormat.MP3
        else -> MediaFormat.MP4
    }

    private fun friendlyError(raw: String?): Exception {
        val msg = raw ?: ""
        return Exception(
            when {
                msg.contains("Unsupported URL", true) -> "This site is not supported."
                msg.contains("Private video", true) -> "This video is private."
                msg.contains("age", true) -> "Age-restricted content is not supported."
                msg.contains("not available", true) ||
                        msg.contains(
                            "unavailable",
                            true
                        ) -> "Video is unavailable or has been removed."

                msg.contains("HTTP Error 403", true) -> "Access denied (403). May be geo-blocked."
                msg.contains("HTTP Error 404", true) -> "Video not found (404)."
                msg.contains("No internet", true) ||
                        msg.contains("UnknownHost", true) -> "No internet connection."

                else -> "Could not fetch video: $msg"
            }
        )
    }
}

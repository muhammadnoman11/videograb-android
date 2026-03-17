package com.muhammadnoman11.videograb.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

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


/**
 * Builds an ACTION_VIEW intent for the given file path or content URI.
 */
fun Context.buildOpenFileIntent(filePath: String, isAudio: Boolean): Intent? = try {
    val uri = resolveFileUri(filePath) ?: return null
    val mime = if (isAudio) "audio/*" else "video/*"
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
} catch (e: Exception) {
    Log.e("FileUtils", "buildOpenFileIntent failed: ${e.message}")
    null
}

/**
 * Attempts to open the file in a system player.
 * Falls back to a chooser if the default player is unavailable.
 * Returns true if an activity was started.
 */
fun playFile(context: Context, filePath: String, isAudio: Boolean): Boolean = try {
    val uri = context.resolveFileUri(filePath) ?: return false
    val mime = if (isAudio) "audio/mpeg" else "video/mp4"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    if (context.packageManager.resolveActivity(intent, 0) != null) {
        context.startActivity(intent)
        true
    } else {
        // Fallback: show chooser with wildcard MIME
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (isAudio) "audio/*" else "video/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            if (isAudio) "Play audio with…" else "Play video with…"
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(chooser)
        true
    }
} catch (e: Exception) {
    Log.e("FileUtils", "playFile failed for $filePath: ${e.message}")
    false
}

/**
 * Resolves a content:// URI or raw file path into a Uri.
 * Uses FileProvider for Android 7+ (API 24+) file paths to avoid FileUriExposedException.
 */
fun Context.resolveFileUri(filePath: String): Uri? {
    if (filePath.isBlank()) return null
    return if (filePath.startsWith("content://")) {
        Uri.parse(filePath)
    } else {
        val file = File(filePath)
        if (!file.exists()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(file)
        }
    }
}

/**
 * Checks whether a file at [filePath] actually exists.
 * Handles both content:// URIs (Android 10+) and file system paths.
 */
fun fileExistsAtPath(context: Context, filePath: String): Boolean = try {
    when {
        filePath.isBlank() -> false
        filePath.startsWith("content://") -> {
            val uri = Uri.parse(filePath)
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }

        else -> File(filePath).exists()
    }
} catch (_: Exception) {
    false
}

// Byte formatting
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
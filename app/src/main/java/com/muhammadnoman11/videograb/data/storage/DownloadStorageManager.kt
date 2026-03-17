package com.muhammadnoman11.videograb.data.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

private const val TAG = "StorageManager"

@Singleton
class DownloadStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    //Temp file management

    val tempDir: File
        get() = File(context.getExternalFilesDir(null), "temp").also { it.mkdirs() }

    fun tempFileFor(downloadId: Int, ext: String): File =
        File(tempDir, "dl_${downloadId}.$ext")

    /** Removes temp files older than 24 hours */
    fun cleanStale() {
        if (!tempDir.exists()) return
        val cutoff = System.currentTimeMillis() - 86_400_000L
        tempDir.listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** Deletes all temp files belonging to a specific download (all extensions) */
    fun cleanTempForDownload(downloadId: Int) {
        tempDir.listFiles()
            ?.filter { it.name.startsWith("dl_${downloadId}") }
            ?.forEach { it.delete() }
    }

    // Final destination
    /**
     * Moves [tempFile] to the public Movies or Music folder and returns a URI string.
     *
     * Android 10+ (API 29+): uses MediaStore with IS_PENDING pattern.
     *   Returns a content:// URI string that survives app uninstall.
     *
     * Android 8–9: copies to the public directory and triggers MediaScanner.
     *   Returns an absolute file path string.
     */
    fun moveToFinalDestination(tempFile: File, fileName: String, isAudio: Boolean): String {
        val sanitized = sanitize(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            moveViaMediaStore(tempFile, sanitized, isAudio)
        } else {
            moveToPublicDir(tempFile, sanitized, isAudio)
        }
    }

    // Android 10+
    private fun moveViaMediaStore(source: File, fileName: String, isAudio: Boolean): String {
        val resolver = context.contentResolver
        val ext = source.extension.lowercase()
        val mimeType = resolveMime(ext, isAudio)
        val collection = if (isAudio)
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val relativePath = if (isAudio)
            "${Environment.DIRECTORY_MUSIC}/VideoGrab"
        else
            "${Environment.DIRECTORY_MOVIES}/VideoGrab"

        Log.d(TAG, "MediaStore ← $fileName  mime=$mimeType  ${source.length()} B")

        // Remove any stale entry we own with the same name
        removeOwnedEntry(fileName, isAudio)

        // Insert as pending so the file is invisible while writing
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values)
            ?: throw Exception("MediaStore insert returned null for $fileName")

        try {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                source.inputStream().use { it.copyTo(out, 65_536) }
            } ?: throw Exception("Cannot open output stream for $fileName")

            // Clear IS_PENDING — file becomes visible in gallery
            val clearValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, clearValues, null, null)

            // Android 10 OEM bug: update() can return 0 even on success.
            // Verify by querying, and re-insert without IS_PENDING if still pending.
            if (queryIsPending(uri) != 0) {
                Log.w(TAG, "IS_PENDING still set — re-inserting without pending flag")
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                return reInsertWithoutPending(source, fileName, mimeType, collection, relativePath)
            }

            Log.d(TAG, "MediaStore finalised: $uri")
            source.delete()
            return uri.toString()
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun reInsertWithoutPending(
        source: File,
        fileName: String,
        mimeType: String,
        collection: Uri,
        relativePath: String
    ): String {
        val resolver = context.contentResolver
        // Append timestamp to avoid name collisions if the original entry still exists
        val uniqueName = uniqueFileName(fileName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values)
            ?: throw Exception("Re-insert returned null for $uniqueName")
        resolver.openOutputStream(uri, "wt")?.use { out ->
            source.inputStream().use { it.copyTo(out, 65_536) }
        } ?: throw Exception("Re-insert output stream null for $uniqueName")
        source.delete()
        Log.d(TAG, "Re-insert OK: $uri")
        return uri.toString()
    }

    private fun queryIsPending(uri: Uri): Int = try {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        } ?: -1
    } catch (_: Exception) {
        -1
    }

    /**
     * Only delete entries owned by this app to avoid SecurityException.
     * On Android 10+ each MediaStore entry has an OWNER_PACKAGE_NAME column.
     */
    private fun removeOwnedEntry(fileName: String, isAudio: Boolean) {
        val resolver = context.contentResolver
        val collection = if (isAudio)
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val owner = try {
                    cursor.getString(1)
                } catch (_: Exception) {
                    null
                }
                if (owner == null || owner == context.packageName) {
                    val entryUri = Uri.withAppendedPath(collection, id.toString())
                    try {
                        resolver.delete(entryUri, null, null)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // Android 8–9
    private fun moveToPublicDir(source: File, fileName: String, isAudio: Boolean): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            ), "VideoGrab"
        ).also { it.mkdirs() }

        // Avoid overwriting existing files by appending a counter
        var dest = File(dir, fileName)
        var counter = 1
        while (dest.exists()) {
            val base = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            dest = File(dir, "${base}_$counter.$ext")
            counter++
        }

        source.copyTo(dest, overwrite = false)
        source.delete()

        // Notify gallery / music apps
        MediaScannerConnection.scanFile(
            context,
            arrayOf(dest.absolutePath),
            arrayOf(resolveMime(dest.extension.lowercase(), isAudio))
        ) { path, scanUri -> Log.d(TAG, "MediaScanner: $path → $scanUri") }

        return dest.absolutePath
    }


    /**
     * Resolves the correct MIME type from the file extension.
     * Using the wrong MIME (e.g. video/mp4 for .webm) causes playback rejection on Android 10.
     */
    fun resolveMime(ext: String, isAudio: Boolean): String = when {
        isAudio -> when (ext) {
            "m4a" -> "audio/mp4"
            "webm" -> "audio/webm"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            else -> "audio/mpeg"
        }

        else -> when (ext) {
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/avi"
            "ts" -> "video/mp2ts"
            else -> "video/mp4"
        }
    }

    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)

    private fun uniqueFileName(name: String): String {
        val base = name.substringBeforeLast(".")
        val ext = name.substringAfterLast(".", "")
        val ts = System.currentTimeMillis() % 10_000
        return if (ext.isNotEmpty()) "${base}_$ts.$ext" else "${base}_$ts"
    }
}
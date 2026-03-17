package com.muhammadnoman11.videograb.core.util


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
 * Returns the permissions this app requires on the current Android version.
 */
fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

fun Context.hasStoragePermission(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        checkPermission(Manifest.permission.READ_MEDIA_VIDEO)

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)

    else ->
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        checkPermission(Manifest.permission.POST_NOTIFICATIONS)
    else true

fun Context.hasAllPermissions(): Boolean =
    hasStoragePermission() && hasNotificationPermission()

private fun Context.checkPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

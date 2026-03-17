package com.muhammadnoman11.videograb.ui.screens.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muhammadnoman11.videograb.core.util.hasNotificationPermission
import com.muhammadnoman11.videograb.core.util.hasStoragePermission
import com.muhammadnoman11.videograb.core.util.requiredPermissions
import com.muhammadnoman11.videograb.R

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


@Composable
fun PermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current

    var storageOk by remember { mutableStateOf(context.hasStoragePermission()) }
    var notificationOk by remember { mutableStateOf(context.hasNotificationPermission()) }
    var showRationale by remember { mutableStateOf(false) }
    var permDenied by remember { mutableStateOf(false) }

    val allGranted = storageOk && notificationOk

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        storageOk = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                results[Manifest.permission.READ_MEDIA_VIDEO] ?: false

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                results[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

            else ->
                results[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        }
        notificationOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            results[Manifest.permission.POST_NOTIFICATIONS] ?: false
        else true

        if (!storageOk) permDenied = true
    }

    LaunchedEffect(Unit) {
        if (!allGranted) showRationale = true
    }

    if (allGranted) {
        content(); return
    }

    if (showRationale && !permDenied) {
        PermissionRationaleDialog(
            storageNeeded = !storageOk,
            notificationNeeded = !notificationOk,
            onRequest = { showRationale = false; launcher.launch(requiredPermissions()) },
            onDismiss = { showRationale = false }
        )
    }

    if (permDenied) {
        PermissionDeniedDialog(
            onOpenSettings = {
                permDenied = false
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            },
            onDismiss = { permDenied = false }
        )
    }

    PermissionBlockedScreen(
        onRequest = {
            if (permDenied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            } else {
                showRationale = false
                launcher.launch(requiredPermissions())
            }
        }
    )
}

// Rationale dialog

@Composable
private fun PermissionRationaleDialog(
    storageNeeded: Boolean,
    notificationNeeded: Boolean,
    onRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252540),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Icon(
                Icons.Default.Lock,
                null,
                tint = Color(0xFF6C63FF),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Permissions Required", color = Color.White, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "VideoGrab needs the following permissions:",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp
                )
                if (storageNeeded) PermissionItem(
                    R.drawable.baseline_storage_24, "Storage",
                    "Save downloaded videos and audio to your device"
                )
                if (notificationNeeded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    PermissionItem(
                        R.drawable.outline_notifications_unread_24, "Notifications",
                        "Show download progress and completion alerts"
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant Permissions", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not Now", color = Color.White.copy(0.5f)) }
        }
    )
}

@Composable
private fun PermissionItem(icon: Int, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = Color.White.copy(0.6f), fontSize = 12.sp)
        }
    }
}

// Permanently denied dialog

@Composable
private fun PermissionDeniedDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252540),
        shape = RoundedCornerShape(20.dp),
        title = { Text("Permission Denied", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Storage permission was denied. Please open Settings and grant it manually.",
                color = Color.White.copy(0.7f), fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Open Settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.5f)) }
        }
    )
}

// Blocked screen (while waiting for permission)

@Composable
private fun PermissionBlockedScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Lock, null,
                modifier = Modifier.size(80.dp), tint = Color(0xFF6C63FF).copy(0.5f)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Permissions Needed",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "VideoGrab needs storage access to save your downloads.",
                color = Color.White.copy(0.6f), textAlign = TextAlign.Center, fontSize = 14.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Grant Permissions", fontWeight = FontWeight.Bold) }
        }
    }
}

package com.muhammadnoman11.videograb.ui.screens.downloads

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.muhammadnoman11.videograb.domain.model.DownloadEntity
import com.muhammadnoman11.videograb.domain.model.DownloadStatus
import com.muhammadnoman11.videograb.ui.viewmodel.MainViewModel
import com.muhammadnoman11.videograb.core.util.fileExistsAtPath
import com.muhammadnoman11.videograb.core.util.formatBytes
import com.muhammadnoman11.videograb.core.util.playFile
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
fun DownloadsScreen(viewModel: MainViewModel = hiltViewModel()) {
    val active by viewModel.activeDownloads.collectAsState()
    val completed by viewModel.completedDownloads.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))))
    ) {
        if (active.isEmpty() && completed.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (active.isNotEmpty()) {
                    item {
                        SectionHeader("Active Downloads")
                    }
                    items(active, key = { it.id }) { dl ->
                        ActiveDownloadCard(
                            download = dl,
                            onPause = { viewModel.pauseDownload(dl.id) },
                            onResume = { viewModel.resumeDownload(dl.id) },
                            onCancel = { viewModel.cancelDownload(dl.id) }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item { SectionHeader("Completed") }
                    items(completed, key = { it.id }) { dl ->
                        CompletedDownloadCard(
                            download = dl,
                            onDelete = { viewModel.deleteDownload(dl.id) },
                            onAutoDelete = { viewModel.deleteDownload(dl.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}


@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// Active download card

@Composable
fun ActiveDownloadCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val status = DownloadStatus.valueOf(download.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252540))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = download.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp, 45.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        download.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp
                    )
                    Text(
                        "${download.quality} • ${download.format}",
                        color = Color.White.copy(0.5f), fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when (status) {
                    DownloadStatus.PAUSED -> Color(0xFFFFA500)
                    else -> Color(0xFF6C63FF)
                },
                trackColor = Color.White.copy(0.1f)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        when (status) {
                            DownloadStatus.DOWNLOADING -> "Downloading… ${download.progress}%"
                            DownloadStatus.PAUSED -> "Paused — ${download.progress}%"
                            DownloadStatus.QUEUED -> "Queued"
                            else -> status.name
                        },
                        color = Color.White.copy(0.6f), fontSize = 12.sp
                    )
                    if (download.totalBytes > 0) {
                        Text(
                            "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}",
                            color = Color.White.copy(0.4f), fontSize = 11.sp
                        )
                    }
                }

                Row {
                    when (status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painterResource(R.drawable.ic_pause),
                                    null,
                                    tint = Color(0xFFFFA500)
                                )
                            }
                        }

                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF63FFDA))
                            }
                        }

                        else -> {}
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFFF4444))
                    }
                }
            }
        }
    }
}

// Completed download card

@Composable
fun CompletedDownloadCard(
    download: DownloadEntity,
    onDelete: () -> Unit,
    onAutoDelete: () -> Unit
) {
    val context = LocalContext.current
    val isAudio = download.format == "MP3"

    // Verify file still exists (content:// URI or file path)
    val fileExists = remember(download.filePath) {
        fileExistsAtPath(context, download.filePath)
    }

    // Auto-remove DB entry if file is missing
    LaunchedEffect(fileExists, download.filePath) {
        if (download.filePath.isNotBlank() && !fileExists) {
            onAutoDelete()
        }
    }

    if (!fileExists && download.filePath.isNotBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252540))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = download.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp, 45.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        download.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp
                    )
                    Text(
                        buildString {
                            append(download.quality); append(" • "); append(download.format)
                            if (download.totalBytes > 0) {
                                append(" • "); append(formatBytes(download.totalBytes))
                            }
                        },
                        color = Color.White.copy(0.5f), fontSize = 12.sp
                    )
                    Text(
                        "✓ Completed", color = Color(0xFF4CAF50), fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (download.filePath.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val ok = playFile(context, download.filePath, isAudio)
                            if (!ok) Toast.makeText(
                                context,
                                "No app found to play this file", Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Icon(
                            painterResource(android.R.drawable.ic_media_play),
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isAudio) "Play" else "Play Video",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444)),
                        border = BorderStroke(1.dp, Color(0xFFFF4444).copy(0.5f))
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF4444).copy(0.7f))
                    }
                }
            }
        }
    }
}


@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_download), null,
            modifier = Modifier.size(80.dp), tint = Color.White.copy(0.1f)
        )
        Spacer(Modifier.height(16.dp))
        Text("No downloads yet", color = Color.White.copy(0.4f), fontSize = 16.sp)
        Text("Paste a URL on the Home tab", color = Color.White.copy(0.2f), fontSize = 13.sp)
    }
}
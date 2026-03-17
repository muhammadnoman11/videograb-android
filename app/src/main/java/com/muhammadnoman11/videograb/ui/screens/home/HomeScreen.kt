package com.muhammadnoman11.videograb.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.muhammadnoman11.videograb.domain.model.Quality
import com.muhammadnoman11.videograb.domain.model.StreamInfo
import com.muhammadnoman11.videograb.ui.viewmodel.MainViewModel
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.homeState.collectAsState()
    val ready by viewModel.appReady.collectAsState()
    val status by viewModel.appStatus.collectAsState()
    val scrollState = rememberScrollState()


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                "VideoGrab",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF6C63FF)
            )
            Text("Download from any website", fontSize = 14.sp, color = Color.White.copy(0.6f))

            Spacer(Modifier.height(16.dp))

            // yt-dlp update banner — visible until app is ready
            AnimatedVisibility(visible = !ready) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            Color(0xFF63B3ED),
                            strokeWidth = 2.dp
                        )
                        Text(status, color = Color(0xFF63B3ED), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // URL input card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252540))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Paste video URL", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::onUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6C63FF),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            cursorColor = Color(0xFF6C63FF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (state.url.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(Icons.Default.Clear, null, tint = Color.White.copy(0.5f))
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = viewModel::fetchVideoInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                        enabled = state.url.isNotEmpty() && !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetch Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Error message
            AnimatedVisibility(visible = state.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4444).copy(0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF4444))
                        Spacer(Modifier.width(8.dp))
                        Text(state.error ?: "", color = Color(0xFFFF4444), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SupportedSitesRow()
        }

        // Quality bottom sheet
        if (state.showQualitySheet && state.streamInfo != null) {
            QualityBottomSheet(
                streamInfo = state.streamInfo!!,
                onQualitySelected = viewModel::downloadWithQuality,
                onDismiss = viewModel::dismissQualitySheet
            )
        }
    }
}


@Composable
private fun SupportedSitesRow() {
    val sites =
        listOf("YouTube", "Instagram", "TikTok", "Twitter", "Facebook", "Vimeo", "+1000 more")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Supports", color = Color.White.copy(0.4f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sites.forEach { site ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF252540),
                    border = BorderStroke(1.dp, Color(0xFF6C63FF).copy(0.4f))
                ) {
                    Text(
                        site,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityBottomSheet(
    streamInfo: StreamInfo,
    onQualitySelected: (Quality) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(40.dp, 4.dp)
                    .background(Color.White.copy(0.2f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(scrollState)
        ) {
            // Video thumbnail + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = streamInfo.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        streamInfo.title, color = Color.White, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp
                    )
                    Text(streamInfo.uploaderName, color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Select Quality",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))

            val videoQualities = streamInfo.availableQualities.filter { !it.isAudioOnly }
            val audioQualities = streamInfo.availableQualities.filter { it.isAudioOnly }

            if (videoQualities.isNotEmpty()) {
                Text(
                    "🎬 Video",
                    color = Color(0xFF6C63FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                videoQualities.forEach { q ->
                    QualityRow(quality = q, onClick = { onQualitySelected(q) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (audioQualities.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🎵 Audio Only (MP3)",
                    color = Color(0xFF63FFDA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                audioQualities.forEach { q ->
                    QualityRow(quality = q, onClick = { onQualitySelected(q) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QualityRow(quality: Quality, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252540)),
        border = BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (quality.isAudioOnly) R.drawable.ic_music else R.drawable.ic_video
                    ),
                    contentDescription = null,
                    tint = if (quality.isAudioOnly) Color(0xFF63FFDA) else Color(0xFF6C63FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(quality.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (!quality.isAudioOnly) {
                        Text(quality.format.name, color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }
            Icon(
                painterResource(R.drawable.ic_download), null,
                tint = Color(0xFF6C63FF), modifier = Modifier.size(20.dp)
            )
        }
    }
}
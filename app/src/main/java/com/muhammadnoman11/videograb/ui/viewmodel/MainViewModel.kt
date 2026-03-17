package com.muhammadnoman11.videograb.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muhammadnoman11.videograb.VideoGrabApp
import com.muhammadnoman11.videograb.domain.model.Quality
import com.muhammadnoman11.videograb.domain.model.StreamInfo
import com.muhammadnoman11.videograb.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

// UI state
data class HomeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val streamInfo: StreamInfo? = null,
    val error: String? = null,
    val showQualitySheet: Boolean = false
)


@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val app get() = context.applicationContext as VideoGrabApp

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    val activeDownloads    = repository.activeDownloads.stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )
    val completedDownloads = repository.completedDownloads.stateIn(
        viewModelScope, SharingStarted.Lazily, emptyList()
    )

    // App-level readiness — drives the banner shown while yt-dlp updates
    val appReady: StateFlow<Boolean> = app.isReady
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val appStatus: StateFlow<String> = app.statusMessage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Initializing…")


    //Home screen
    fun onUrlChanged(url: String) {
        _homeState.update { it.copy(url = url, error = null) }
    }

    fun fetchVideoInfo() {
        val url = _homeState.value.url.trim()
        if (url.isEmpty()) return

        _homeState.update { it.copy(isLoading = true, error = null, streamInfo = null) }
        viewModelScope.launch {
            repository.extractInfo(url)
                .onSuccess { info ->
                    _homeState.update {
                        it.copy(isLoading = false, streamInfo = info, showQualitySheet = true)
                    }
                }
                .onFailure { e ->
                    _homeState.update {
                        it.copy(isLoading = false, error = e.message ?: "Unknown error")
                    }
                }
        }
    }

    fun downloadWithQuality(quality: Quality) {
        val state = _homeState.value
        val info  = state.streamInfo ?: return
        _homeState.update { it.copy(showQualitySheet = false) }
        viewModelScope.launch {
            repository.startDownload(
                url          = state.url,
                title        = info.title,
                thumbnailUrl = info.thumbnailUrl,
                quality      = quality
            )
        }
    }

    fun dismissQualitySheet() {
        _homeState.update { it.copy(showQualitySheet = false) }
    }

    // Called when the app receives a share intent
    fun setUrlFromShare(url: String) {
        _homeState.update { it.copy(url = url) }
        fetchVideoInfo()
    }

    // Downloads screen
    fun pauseDownload(id: Int)  = viewModelScope.launch { repository.pauseDownload(id) }
    fun resumeDownload(id: Int) = viewModelScope.launch { repository.resumeDownload(id) }
    fun cancelDownload(id: Int) = viewModelScope.launch { repository.cancelDownload(id) }
    fun deleteDownload(id: Int) = viewModelScope.launch { repository.deleteDownload(id) }
}
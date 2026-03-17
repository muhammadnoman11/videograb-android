package com.muhammadnoman11.videograb.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.*
import com.muhammadnoman11.videograb.core.db.DownloadDao
import com.muhammadnoman11.videograb.domain.model.DownloadEntity
import com.muhammadnoman11.videograb.domain.model.DownloadStatus
import com.muhammadnoman11.videograb.data.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
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


@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val workManager: WorkManager
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(checkConnection())
    val isConnected: StateFlow<Boolean> = _isConnected

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !_isConnected.value
            _isConnected.value = true
            if (wasOffline) scope.launch { resumeInterruptedDownloads() }
        }

        override fun onLost(network: Network) {
            if (!checkConnection()) _isConnected.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _isConnected.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    private fun checkConnection(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Re-enqueue downloads that were mid-progress when connection dropped
    private suspend fun resumeInterruptedDownloads() {
        delay(1500) // Allow connection to stabilise
        val all = mutableListOf<DownloadEntity>()
        dao.getAllDownloads().collect { list ->
            all.addAll(list.filter {
                it.status == DownloadStatus.PAUSED.name &&
                        it.downloadedBytes > 0 && it.totalBytes > 0
            })
            return@collect
        }
        all.forEach { entity ->
            dao.update(entity.copy(status = DownloadStatus.QUEUED.name))
            workManager.enqueueUniqueWork(
                "download_${entity.id}",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to entity.id))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag("download_${entity.id}")
                    .build()
            )
        }
    }
}

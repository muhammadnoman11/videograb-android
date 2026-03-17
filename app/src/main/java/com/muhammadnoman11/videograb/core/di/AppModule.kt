package com.muhammadnoman11.videograb.core.di


import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.muhammadnoman11.videograb.core.db.DownloadDao
import com.muhammadnoman11.videograb.core.db.DownloadDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): DownloadDatabase =
        Room.databaseBuilder(ctx, DownloadDatabase::class.java, "downloads.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideDao(db: DownloadDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
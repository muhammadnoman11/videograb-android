# VideoGrab 📥


> A powerful video and audio downloader for Android, supporting (YouTube, TikTok, Instagram, Twitter) and more. Built with yt-dlp, Jetpack Compose, and Clean Architecture.

---

## Features

- 🎬 Download videos in multiple qualities
- 🎵 Download audio as MP3 from any supported site
- 📱 Supports — YouTube, TikTok, Instagram, Twitter/X, Facebook, and more
- ⏸️ Pause and resume downloads with progress preserved
- 🔗 Share-to-download from any browser or social app
- 📡 Auto-resumes interrupted downloads when network reconnects
---

## Screenshots

<p align="center">
  <img src="screenshots/video_grab_banner.png" width="800" alt="VideoGrab App Banner"/>
</p>
---

### Package Structure

```
com.muhammadnoman11.videograb/
│
├── VideoGrabApp.kt           # Application class — yt-dlp init
│
├── core/                           # Framework / infrastructure
│   ├── db/
│   │   ├── DownloadDatabase.kt     # Room database definition
│   │   └── DownloadDao.kt          # Room DAO — reactive flows + suspend functions
│   ├── di/
│   │   └── AppModule.kt            # Hilt module — DB, WorkManager
│   ├── network/
│   │   └── NetworkMonitor.kt       # Connectivity watcher + auto-resume logic
│   └── util/
│       ├── FileUtils.kt            # File intents, URI resolution, formatBytes
│       └── PermissionUtils.kt      # Per-API-level permission helpers
│
├── domain/                         # Business logic (pure Kotlin, no Android deps)
│   ├── model/
│   │   └── Models.kt               # DownloadEntity, Quality, StreamInfo, enums
│   └── extractor/
│       └── YtDlpExtractor.kt       # yt-dlp getInfo wrapper + format parsing
│
├── data/                           # Data sources and background processing
│   ├── repository/
│   │   └── DownloadRepository.kt   # Single source of truth
│   ├── worker/
│   │   └── DownloadWorker.kt       # WorkManager worker — yt-dlp config-file execution
│   ├── service/
│   │   └── DownloadService.kt      # Foreground service + notification management
│   └── storage/
│       └── DownloadStorageManager.kt  # MediaStore (API 29+) + public dir (API 26–28)
│
└── ui/                             # Compose UI
    ├── MainActivity.kt             # Entry point, navigation, share intent
    ├── viewmodel/
    │   └── MainViewModel.kt        # Single ViewModel for all screens
    └── screens/
        ├── home/
        │   └── HomeScreen.kt       # URL input, yt-dlp status banner, quality sheet
        ├── downloads/
        │   └── DownloadsScreen.kt  # Active + completed download cards
        └── permissions/
            └── PermissionsScreen.kt  # Rationale dialogs + blocked screen
```


## Tech Stack

| Component | Library |
|---|---|
| yt-dlp wrapper | https://github.com/yausername/youtubedl-android |
| UI | Jetpack Compose |
| DI | Hilt |
| Background work | WorkManager |
| Database | Room |
| Image loading | Coil |
| Architecture | Clean Architecture |
| Language | Kotlin |

---

## License

```
Copyright 2026 Muhammad Noman

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

```


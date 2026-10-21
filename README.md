# FrostBackup (FOSS Android Root Backup)

> **A modern, Free and Open Source (GPL-3.0) Android backup application that actually preserves app login sessions, device signatures, and Keystore tokens across ROM updates and device migrations.**

---

## ⚡ The Breakthrough: Why Restored Apps Stay Logged In

Unlike legacy backup tools (e.g. Titanium Backup) or basic data copiers that leave you logged out of Discord, Reddit, Twitter, WhatsApp, or games, **FrostBackup** solves the three root causes of session loss on modern Android (Android 8 through 15+):

1. **SSAID Preservation:** Automatically captures and re-injects the app's unique device identifier (`/data/system/users/0/settings_ssaid.xml`) before the app is ever launched.
2. **Keystore Token Migration:** Copies system-level and software-backed cryptographic keys (`/data/misc/keystore/user_0/`) and dynamically remaps them to the restored application's newly assigned UID.
3. **Dynamic UID & SELinux Context Remapping:** Automatically queries the newly assigned Linux user ID (`dumpsys package`) and executes `chown -R` and `restorecon -R` so the application can access its databases.

---

## 🌟 Key Features

* **100% Free & Open Source:** Licensed under GNU GPLv3. Zero tracking, zero telemetry, zero proprietary Google Play services required.
* **Modern UI:** Built from the ground up using **Jetpack Compose** and **Material 3** with dynamic system wallpaper theming.
* **Zero-Knowledge Encryption:** Encrypt backup packages locally using **AES-256-GCM** with PBKDF2 (65,536 iterations). Your private credentials are never exposed.
* **Free Google Drive Cloud Backup:** Directly connects to Google Drive's hidden `appDataFolder` via REST API v3 with chunked resumable uploads. No paid subscriptions required.
* **Granular Backup Control:** Toggle Split APKs, internal databases, external storage, SSAID signatures, and Keystore keys individually.
* **Fast Batch Operations:** Multi-select apps with batch queuing and live progress percentages.

---

## 🏗️ Project Architecture

```
app-vault/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/io/github/project/vault/
│           ├── core/
│           │   ├── root/       # RootShell, process pipes & superuser management
│           │   ├── crypto/     # AES-256-GCM authenticated cipher streaming
│           │   ├── session/    # SSAID, Keystore, and Permission managers
│           │   ├── backup/     # BackupEngine & tar archive pipeline
│           │   ├── restore/    # RestoreEngine, UID remapping & SELinux fixer
│           │   └── cloud/      # Google Drive REST API & CloudProvider interface
│           ├── data/
│           │   ├── model/      # AppInfo, BackupMetadata, BackupConfig
│           │   └── repository/ # AppRepository & BackupRepository
│           └── ui/
│               ├── theme/      # Material 3 colors, typography, dynamic themes
│               ├── navigation/ # Screen routes & bottom navigation
│               ├── screens/    # AppListScreen, RestoreScreen, CloudScreen, Settings
│               └── components/ # Dynamic cards, progress dialogs, badges
```

---

## 🚀 Building the APK

### Prerequisites
* JDK 17 or newer
* Android SDK (API 34)

### Build with Gradle
Clone the repository and run:

```bash
./gradlew assembleDebug
```

The generated APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

For release builds with ProGuard/R8 optimization:
```bash
./gradlew assembleRelease
```

---

## 📄 License
This project is licensed under the [GNU General Public License v3.0](LICENSE).

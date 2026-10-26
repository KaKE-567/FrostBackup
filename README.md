<div align="center">

<img src="./media/logo.png" alt="FrostBackup Logo" width="128px" />

# FrostBackup

[![Official Website](https://img.shields.io/badge/Website-kake--567.github.io%2FFrostBackup-00f0ff?logo=firefoxbrowser&logoColor=white)](https://kake-567.github.io/FrostBackup/)
[![GitHub Release](https://img.shields.io/github/v/release/KaKE-567/FrostBackup?color=00bcd4&label=Latest%20Release&logo=github)](https://github.com/KaKE-567/FrostBackup/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/KaKE-567/FrostBackup/total?color=4caf50&logo=android)](https://github.com/KaKE-567/FrostBackup/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
[![Root Support](https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-orange.svg)](https://github.com/KaKE-567/FrostBackup)
[![Android](https://img.shields.io/badge/Android-8.0%20--%2017%2B-brightgreen.svg?logo=android)](https://github.com/KaKE-567/FrostBackup)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7f52ff.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285f4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)

**A modern, blazing-fast, Free and Open Source (GPL-3.0) Android root backup application that truly preserves application login sessions, device signatures, and Keystore cryptographic tokens across ROM flashes and migrations.**

[🌐 Official Website](https://kake-567.github.io/FrostBackup/) • [Download Latest APK](https://github.com/KaKE-567/FrostBackup/releases/latest) • [Documentation & Guide](https://kake-567.github.io/FrostBackup/#guide) • [Report Issue](https://github.com/KaKE-567/FrostBackup/issues) • [Changelog](https://github.com/KaKE-567/FrostBackup/releases)

---

</div>

## 📖 Overview

Most Android backup utilities—from legacy tools like Titanium Backup to modern file copiers—leave your apps broken upon restore: Discord asks you to log in again, WhatsApp requires SMS re-verification, Twitter loses its auth state, and games force you through introductory tutorials.

**FrostBackup** was engineered specifically to solve the modern Android security architecture (Android 8.0 up to Android 17+). By coupling raw root access with intelligent system-level state migration, FrostBackup restores your apps into the exact running state they were in before you wiped your device.

---

## ⚡ The Breakthrough: Why Restored Apps Stay Logged In

```text
┌─────────────────────────────────────────────────────────┐
│                      BACKUP STAGE                       │
│  • App Data (/data/data)                                │
│  • SSAID Signatures (settings_ssaid.xml)                │
│  • Keystore Crypto Keys (/data/misc/keystore)           │
│  • Base APK & Split Architectures                       │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│            ENCRYPTED FROST ARCHIVE (.frost)             │
│            (AES-256-GCM + PBKDF2 Encryption)            │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│               RESTORE & MIGRATION STAGE                 │
│  1. Extract APKs & App Data Structure                   │
│  2. Inject SSAID Device ID before App Launch            │
│  3. Remap Keystore Keys to Newly Assigned Linux UID     │
│  4. Repair Permissions: chown -R & restorecon -R        │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
              🚀 APP LAUNCHES FULLY LOGGED IN
```

1. **SSAID Preservation:** Captures and re-injects the app's unique Android ID from `/data/system/users/0/settings_ssaid.xml` before the application is started for the first time. The server believes it is communicating with the exact same physical hardware.
2. **Keystore Token Migration:** Extracts system-level and software-backed cryptographic keys from `/data/misc/keystore/user_0/` and dynamically patches their key aliases and UID descriptors to match the new UID assigned by Android's package manager upon reinstall.
3. **Dynamic SELinux & Context Healing:** Automatically resolves the newly generated Linux user ID via `dumpsys package` and applies recursive `chown -R` and `restorecon -R` so the restored application never experiences SQLite permission denials.

---

## 🌟 Key Features

* 🌳 **Universal Root Support:** Seamlessly integrates with **[Magisk](https://github.com/topjohnwu/Magisk)**, **[KernelSU](https://github.com/tiann/KernelSU)**, and **[APatch](https://github.com/bmax121/APatch)** through asynchronous root process streaming pipes.
* 📞 **Dedicated Calls & SMS Engine:** Dual-mode architecture combining direct SQLite extraction (`mmssms.db`, `calllog.db`) with ContentProvider fallbacks and smart deduplicated restore.
* 🔒 **Zero-Knowledge Encryption:** Encrypt your backup packages locally with military-grade **AES-256-GCM** using PBKDF2 key derivation (65,536 iterations). Your private credentials never leak in plain text.
* ☁️ **Direct Google Drive Cloud Sync:** Direct REST API v3 integration with Google Drive's isolated `appDataFolder`. Resumable chunked uploads without third-party middleman servers or proprietary Google Play dependencies.
* 🎨 **Material 3 Frosted Glass UI:** Built with Jetpack Compose featuring smooth crossfades, blur backdrops, Dynamic Monet (wallpaper-based palettes), 15 vibrant preset accents, and pure black `#000000` AMOLED optimization.
* 💾 **Flexible Storage Destinations:** Back up to and restore from Internal Storage, MicroSD cards, or external USB OTG flash drives with live capacity meters and dynamic volume resolution.
* ⚡ **High-Speed Batch Processing:** Multi-select applications with granular toggles (Split APKs, internal databases, external storage, SSAID, Keystore) and real-time progress tracking.


---

## 🏗️ Project Architecture

```
app-vault/
├── .github/workflows/          # Automated CI/CD APK build workflows
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml # Permissions & Application entry points
│   │   └── java/io/github/project/vault/
│   │       ├── core/
│   │       │   ├── backup/     # BackupEngine & tar streaming archive pipeline
│   │       │   ├── cloud/      # Google Drive REST API & CloudProvider interface
│   │       │   ├── crypto/     # AES-256-GCM cipher streaming & PBKDF2
│   │       │   ├── restore/    # RestoreEngine, UID remapping & SELinux fixer
│   │       │   ├── root/       # RootShell, asynchronous process pipes & SU management
│   │       │   ├── session/    # SSAID, Keystore token & permission handlers
│   │       │   └── telephony/  # Dual-mode SQLite + ContentProvider Calls & SMS engine
│   │       ├── data/
│   │       │   ├── model/      # AppInfo, BackupMetadata, TelephonyModels
│   │       │   └── repository/ # AppRepository, BackupRepository, TelephonyRepository
│   │       └── ui/
│   │           ├── components/ # Frosted glass cards, dialogs, badges & sliders
│   │           ├── navigation/ # Screen routes & single-line bottom bar
│   │           ├── screens/    # AppList, Restore, Telephony, Cloud, Settings
│   │           └── theme/      # Material 3 colors, dynamic Monet, AMOLED dark
└── media/                      # App branding, icons, and showcase screenshots
```

---

## 🚀 Building from Source

### Prerequisites
* **JDK 17** or newer
* **Android SDK** (API Level 34 / Build Tools 34.0.0)

### Clone & Build
```bash
git clone https://github.com/KaKE-567/FrostBackup.git
cd FrostBackup

# Build Debug APK
./gradlew assembleDebug

# Build Optimized Release APK (R8/ProGuard)
./gradlew assembleRelease
```
The compiled APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 📥 Download

Official stable releases are distributed exclusively through GitHub Releases:

👉 **[Download Latest FrostBackup APK](https://github.com/KaKE-567/FrostBackup/releases/latest)**

---

## 🙏 Credits & Acknowledgements

FrostBackup stands on the shoulders of giants in the Android open-source and rooting communities. Special thanks and sincere credit to:

* **[XayahSuSuSu](https://github.com/XayahSuSuSu)** for **[Android-DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup)** — an enormous inspiration for modern FOSS Android data backup UI paradigms, user workflows, and dedication to open-source Android tooling.
* **[CoolApk@落叶凄凉TEL](http://www.coolapk.com/u/2277637)** for **[speed-backup](https://github.com/YAWAsau/backup_script)** — who originally conceived and proved the concepts of lightning-fast Android root backup shell scripts.
* **[John Wu (@topjohnwu)](https://github.com/topjohnwu)** for **[Magisk](https://github.com/topjohnwu/Magisk)** — which revolutionized modern Android systemless rooting.
* **[Weishu (@tiann)](https://github.com/tiann)** for **[KernelSU](https://github.com/tiann/KernelSU)** — pioneering kernel-level root access and providing the aesthetic inspiration for our customization controls.
* **[bmax121 (@bmax121)](https://github.com/bmax121)** for **[APatch](https://github.com/bmax121/APatch)** — the next-generation kernel patcher.
* **The [Android Open Source Project (AOSP)](https://source.android.com)** & **Jetpack Compose Team** — for the modern Android platform and reactive UI toolkit.
* **[NeoBackup](https://github.com/NeoApplications/Neo-Backup)** & **[OAndBackupX](https://github.com/machiav3lli/oandbackupx)** — for early proof-of-concepts in Android application archiving.
* All community testers and ROM developers across XDA and Telegram who help test and refine modern Android backup integrity.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See the [LICENSE](LICENSE) file for complete terms.

```
Copyright (C) 2024-2026 KaKE-567 & FrostBackup Contributors

FrostBackup is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

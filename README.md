<div align="center">

<div align="center">
  <img src="https://github.com/sujxlsahu/PurrfectSnap/blob/dev/banner.jpg" alt="PurrfectSnap" width="100%"/>
</div>



# PurrfectSnap

### An Xposed module meant to redefine your Snapchat experience! Works on both non-rooted and rooted devices!

<br>

[![Release](https://img.shields.io/github/v/release/sujxlsahu/PurrfectSnap?include_prereleases&style=for-the-badge&color=cba6f7&labelColor=1e1e2e)](https://github.com/sujxlsahu/PurrfectSnap/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/sujxlsahu/PurrfectSnap/total?style=for-the-badge&color=f5bde6&labelColor=1e1e2e)](https://github.com/sujxlsahu/PurrfectSnap/releases)
[![License](https://img.shields.io/badge/License-GPL_3.0-f5a97f?style=for-the-badge&labelColor=1e1e2e)](https://github.com/sujxlsahu/PurrfectSnap/blob/dev/LICENSE)

[Installation](#installation) • [Features](#features) • [Build](#build-from-source) • [Community](#community)

</div>

<br>

---

## Overview

PurrfectSnap is built on the foundation of SnapEnhance, pushing boundaries with innovative features and a refined user experience. This isn't just another fork; it's a complete reimagining of what's possible.

We're committed to active development, bringing you powerful tools that actually matter. Every feature is designed with real users in mind, not just for the sake of adding to a feature list.

<br>

## Philosophy

This project exists because we believe in continuous innovation. We're grateful to the original SnapEnhance team for their groundbreaking work, and we're building on that legacy by exploring new possibilities and listening to what the community actually wants.

No aggressive donation requests. No minimal changes disguised as “major updates.” Just genuine development driven by a passion for creating something exceptional.

<br>

## Features

PurrfectSnap offers deep control across media, privacy, automation, and UI:designed for both casual users and power users.

<table>
<tr>
<td width="50%">

### Media Downloader
Advanced media management with extensive customization options for downloading and organizing content.

**Core Capabilities**
- Custom save locations and path formatting
- Automatic downloads from selected sources
- Profile picture downloads
- Voice note capture with format control
- FFmpeg integration for advanced processing

**Smart Features**
- Duplicate prevention with override option
- Overlay merging for combined content
- Custom logging for tracking downloads
- Context menu integration

</td>
<td width="50%">

### User Interface
Complete control over your interface with deep customization options.

**Visual Customization**
- Custom themes including AMOLED mode
- Configurable icon styles
- Message preview customization
- Bootstrap override for default tabs

**Interface Control**
- Hide unwanted UI components
- Enhanced friend map nametags
- Snap preview options
- Streak expiration info display
- Vertical story viewer
- Message indicators and stealth mode display

</td>
</tr>
<tr>
<td width="50%">

### Messaging
Privacy-focused messaging features with advanced control over your conversations.

**Privacy Tools**
- Screenshot bypass
- Anonymous story viewing
- Hide typing notifications
- Hide Bitmoji presence
- Prevent story rewatch indicators

**Enhanced Features**
- Unlimited snap view time
- Auto mark as read
- Conversation pinning (unlimited)
- Message logger with whitelist/blacklist
- Better notifications with blacklist support
- Double tap actions and reactions
- Message retention policy bypass

</td>
<td width="50%">

### Global Settings
System-wide enhancements that improve your overall experience.

**Performance**
- Better location handling
- Media upload quality control
- Custom video playback rates
- Default volume controls

**Optimization**
- Ad blocking
- Metrics disabling
- Story section control
- Video length restriction bypass
- Snap splitting disable
- Telecom framework control

</td>
</tr>
<tr>
<td width="50%">

### Camera
Professional-grade camera controls for content creation.

**Recording Options**
- Custom frame rates (front/back)
- HEVC recording support
- Custom resolution override
- Force camera source encoding

**Creative Control**
- Immersive preview mode
- Black photo option
- Startup default camera selection

</td>
<td width="50%">

### Rules Engine
Automation system for complex workflows.

- Stealth mode rules
- Auto download conditions
- Auto save parameters
- Auto open snap rules
- Unsaveable message settings

</td>
</tr>
<tr>
<td width="50%">

### Experimental
Cutting-edge features for power users.

**Advanced Tools**
- Native hooks for deep customization
- Spoofing capabilities
- Story logger
- Call recorder
- Account switcher
- App lock
- End-to-end encryption
- My Eyes Only passcode bypass

**Developer Features**
- Better transcript
- Friend notes
- COF experiments
- Custom streaks format
- Prevent forced logout

</td>
<td width="50%">

### Scripting
Extensibility through custom scripts.

- Developer mode
- Module folder management
- Auto reload capability
- Integrated UI
- Log control options
- Optimization toggles

</td>
</tr>
</table>

<br>

**Additional Features**

> **Streaks Reminder** — Configurable interval notifications with remaining time display and group notification support

> **Friend Tracker** — Event recording with background operation and automatic purge management

<br>

> ⚠️Some features are intended for educational, recovery, or accessibility purposes. Misuse may violate Snapchat’s terms of service. 
> **We are not affiliated with Snap Inc. and are not responsible for any violations by the user. Our sole purpose is for education purposes only!**

<br>

---

<br>

## Installation

The guide is no longer needed!  
Just download and install PurrfectSnap from [here](https://github.com/sujxlsahu/PurrfectSnap/releases). The app automatically detects your device type and applies the appropriate setup.

<br>

---

<br>

## Community

Questions? Ideas? Found a bug? Our community is active and responsive.

**[Telegram Channel](https://t.me/purrfectsnap_official)** : Announcements, discussions, and support
**For the discussions group link, refer to the description of the channel!**

<br>

---

<br>

## Build from Source

We welcome contributions from developers who share our vision. Whether it's code, documentation, etc., contributions are always appreciated. Feel free to open a PR :)

### 1) Fork

1. Fork this repository on GitHub.
```

### 2) Generate a Release Keystore (Certificate)

Create a signing keystore (`.jks`) and keep it secure:

```bash
keytool -genkeypair -v \
  -keystore purrfectsnap-release.keystore \
  -storetype JKS \
  -alias purrfectsnap \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Optional local placement for Gradle signing:

```bash
mkdir -p ~/.android
cp purrfectsnap-release.keystore ~/.android/purrfectsnap-release.keystore
```

### 3) Convert Keystore to Base64

For GitHub Actions secret `PS_BASE_64`:

Linux/macOS:

```bash
base64 -w 0 purrfectsnap-release.keystore > keystore.base64
```

PowerShell (Windows):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("purrfectsnap-release.keystore")) | Set-Content -NoNewline keystore.base64
```

### 4) Add Required GitHub Secrets (Your Fork)

Go to:
`Settings -> Secrets and variables -> Actions -> New repository secret`

Create these exact secrets (from `.github/workflows/release.yml`):

- `PS_BASE_64` = contents of `keystore.base64`
- `PS_RELEASE_KEY_ALIAS` = `purrfectsnap` (or your chosen alias)
- `PS_RELEASE_KEY_PASSWORD` = key password from `keytool`
- `PS_RELEASE_STORE_PASSWORD` = keystore password from `keytool`

### 5) Run Release Workflow

1. Open `Actions` in your fork.
2. Run `PurrfectSnap Release CI` (`workflow_dispatch`).
3. Workflow builds and signs:
   - `armv8` release APK
   - `armv7` release APK
4. Workflow creates a GitHub Release and uploads APK assets.

### Notes

- CI uses `JDK 21 (Temurin)`.
- Signing requires all four secrets above.
- Keep keystore and passwords private. Rotate immediately if exposed.


## Credits

PurrfectSnap is built with exceptional open source tools. We do not collect any user information. However, please be aware that third-party libraries may collect data as described in their respective privacy policies.

**Core Dependencies**
- [SnapEnhance](https://github.com/rhunk/SnapEnhance) — The foundation
- [YukiHook](https://github.com/HighCapable/YukiHookAPI) — Framework integration
- [Jingmatrix Lspatch](https://github.com/JingMatrix/LSPatch) — Auto Patcher
- [Dobby](https://github.com/jmpews/Dobby) — Native hooking
- [O‑MVLL](https://github.com/open-obfuscator/o-mvll) — Obfuscation
- [hiddenapibypass](https://github.com/LSPosed/HiddenApiBypass) — Hidden API access
- [dexlib2 (smali)](https://github.com/JesusFreke/smali) — Dex parsing
- [Bouncy Castle](https://www.bouncycastle.org/) — Cryptography
- [apksig](https://android.googlesource.com/platform/tools/apksig/) — APK signing tools
- [Rust Android Gradle Plugin](https://github.com/mozilla/rust-android-gradle) — Native build tooling
- [Android Gradle Plugin](https://developer.android.com/build) — Build system
- [Kotlin](https://kotlinlang.org/) — Language
- [Compose Compiler](https://developer.android.com/jetpack/compose/compiler) — Compose compiler

**Media Processing**
- [ffmpeg-kit-full-gpl](https://github.com/arthenica/ffmpeg-kit) — Media manipulation
- [coil](https://github.com/coil-kt/coil) — Image loading
- [smart-exception-java](https://github.com/arthenica/ffmpeg-kit) — ffmpeg-kit support lib

**Scripting Engine**
- [rhino](https://github.com/mozilla/rhino) — JavaScript runtime
- [rhino-android](https://github.com/F43nd1r/rhino-android) — Android integration

**Utilities**
- [osmdroid](https://github.com/osmdroid/osmdroid) — Map functionality
- [libsu](https://github.com/topjohnwu/libsu) — Root operations
- [colorpicker-compose](https://github.com/skydoves/colorpicker-compose) — Color selection
- [OkHttp](https://github.com/square/okhttp) — Networking
- [Gson](https://github.com/google/gson) — JSON parsing
- [jsoup](https://github.com/jhy/jsoup) — HTML parsing
- [Fetch](https://github.com/tonyofrancis/Fetch) — Download manager
- [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) — Background tasks
- [Accompanist](https://github.com/google/accompanist) — Compose utilities
- [Guava](https://github.com/google/guava) — Core utilities
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) — Async
- [AndroidX DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile) — File access
- [AndroidX RecyclerView](https://developer.android.com/jetpack/androidx/releases/recyclerview) — Lists
- [AndroidX Navigation Compose](https://developer.android.com/jetpack/androidx/releases/navigation) — Navigation
- [Jetpack Compose / Material3](https://developer.android.com/jetpack/compose) — UI toolkit
- [AndroidX Activity KTX](https://developer.android.com/jetpack/androidx/releases/activity) — Activity helpers
- [Compose BOM](https://developer.android.com/jetpack/compose/bom) — Compose version alignment
- [Material Icons (Compose)](https://developer.android.com/jetpack/compose/designsystems/material#icons) — Icons

<br>

---

<div align="center">

<br>

Built with care, maintained with passion ❤️
Open source under GPL-3.0, Apache-2.0
<br>
<br>

</div>

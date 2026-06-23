<div align="center">
  <h1>RecorderX</h1>
  <p>A resilient, open-source recording solution featuring automated codec recovery and offline processing.</p>
  <img src=".github/assets/iconz.png" width="256" height="256" />
  <br>

  [![Latest Version](https://img.shields.io/badge/Version-v2.0.0-9575CD?style=flat&logo=github&logoColor=white)](https://github.com/snap24/RecorderX/releases)
  ![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)
  ![Android](https://img.shields.io/badge/API-29%2B-3DDC84?style=flat&logo=android&logoColor=white)
  [![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat&logo=apache&logoColor=white)](LICENSE)
</div>

---
<h3>RecorderX is an application designed for high-fidelity screen capture.</h3>

## Versions

- v2.0.0 (Latest): Live-Reboot Watchdog, Encoder Fallback Mechanism, and System + Mic Audio Recording.
- v1.1.0: 4K/120FPS Support, AMOLED Lavender UI, and Live Thumbnail Notifications.
- v1.0.0: Initial stable release with H.264/HEVC support.

## Core Features

- High Resolution Capture: Support for 4K (UHD), 2K (QHD), and standard definitions.
- Enhanced Framerates: Native support for 90 FPS and 120 FPS recording modes.
- Advanced Codecs: Integrated support for H.264 (AVC), H.265 (HEVC), and AV1.
- Audio Management: Capture of Microphone, System audio, or both (Mic + System) simultaneously.
- Post-Capture Feedback: Automated thumbnail generation and system notification on session completion.
- Complete Privacy: Operates entirely offline with absolutely no internet permissions or telemetry.

## Advanced Capabilities

- Live-Reboot Watchdog: Features a self-healing encoder loop that seamlessly catches encoding failures and restarts the session internally, ensuring Android 14+ MediaProjection tokens are never invalidated.
- Custom ROM & GSI Compatibility: Bypasses faulty hardware checks found in standard Android environments, providing stable recording on spoofed or heavily modified custom ROMs.
- SoC Graceful Degradation: Automatically detects hardware bottlenecks (like AV1 encoding limits on weaker CPUs) and triggers encoder fallbacks without crashing the application.

<details>
<summary><h3><b>Interface Gallery</b></h3></summary>
<br>
<div align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpeg" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpeg" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpeg" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpeg" width="200" />
</div>
</details>

## Technical Configuration

- Video Bitrate: Configurable up to 40 Mbps (CBR/VBR support).
- Audio Fidelity: Adjustable sample rates from 64kbps to 320kbps.
- Storage Path: All recordings are stored locally in `/Movies/RecorderX`.
- Naming Conventions: Support for custom filename templates using date and timestamp variables.

## Build Requirements

1. Clone: `git clone https://github.com/snap24/RecorderX.git`
2. Environment: Android Studio Koala+, JDK 17.
3. Target: Minimum SDK 29 (Android 10), Target SDK 34 (Android 14).
4. Execution: Run `./gradlew assembleRelease` for optimized production binaries.

## Available On

<a href="https://f-droid.org/packages/com.zygisk_enc.RecorderX"><img src=".github/assets/badge_fdroid.png" height="60" alt="Get it on F-Droid" /></a>
<a href="https://github.com/snap24/RecorderX/releases"><img src=".github/assets/badge_github.png" height="60" alt="Get it on GitHub" /></a>

## License
<a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge&logo=apache" height="40" alt="Apache 2.0"></a>

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

---
<div align="center">
  Maintained by Chinmai H B
</div>

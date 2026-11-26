# Vidiio 2.0.2 Release Notes 🚀

**Release Version:** `v2.0.2`  
**Target SDK:** Android 14 (API 37) | **Min SDK:** Android 7.0 / Fire OS 6 (API 24)  
**Release Date:** November 26, 2026  

---

## 🌟 What's New in Vidiio 2.0.2

### 1. 🔴 Live Stream Playback Engine Refactoring
- **HLS Live Edge Opening:** Configured ExoPlayer and Media3 to open live TV channels, IPTV feeds, and PenguPlay sports events directly at the **live edge** (`C.TIME_UNSET`) instead of position 0 of a DVR window.
- **LIVE Transport Badge:** Replaced `0:00 / 0:00` timer on live streams with a bright **`LIVE`** indicator badge and disabled seek bar for live feeds.
- **Stream Auto-Recovery:** Upgraded `androidx.media3` to **1.11.1** for automatic live window recovery.

### 2. 📻 Rewritten IPTV Playlist & Live TV Engine
- **Custom HTTP Headers:** Added support for per-channel headers defined in M3U playlists (`#EXTVLCOPT` and `#EXTHTTP`).
- **Category Grouping:** Correctly parses `#EXTGRP` and `group-title` attributes to categorize IPTV channels into Sports, News, Entertainment, Movies, and Kids.
- **Staged Channel Loading:** Publishes channel rows in progressive batches so the Live TV screen loads immediately.

### 3. 🛡️ Built-in International VPN Locations
- **1-Tap Global Server Nodes:** Encrypt scraper, torrent tracker, and stream traffic through 5 fast proxy nodes:
  - 🇺🇸 **Charlotte, NC** (United States)
  - 🇨🇦 **Montreal** (Canada)
  - 🇬🇧 **London** (United Kingdom)
  - 🇩🇪 **Frankfurt** (Germany)
  - 🇯🇵 **Tokyo** (Japan)

### 4. 👤 User Profiles & Kids Mode
- **Isolated Per-Profile Watchlists & History:** Room DB schema v6 with separate Watchlists, Continue Watching, and playback history per profile.
- **Kids Mode Filtering:** Filters catalogs and live feeds to G/PG, Animation, and Disney/Cartoon content.
- **Profile Switcher:** Convenient profile creation and switching dialogs with custom avatar colors.

### 5. 🔊 Default English Audio Track Selection
- Automatically selects the English audio track for dual-audio anime, foreign films, and multi-lingual releases on playback startup.

### 6. 📖 Updated Project Documentation
- Complete rewrite of **`README.md`** with architecture overview, feature list, build instructions, and APK guide.

---

## 📦 Shipped 2.0.2 APK Files

| APK Name | Target Devices | Size |
| :--- | :--- | :--- |
| **`vidiio-v2.0.2-universal.apk`** | All Fire TV Sticks, Google TV, Android TV Boxes & Phones | **117 MB** |
| **`vidiio-v2.0.2-arm64-v8a.apk`** | 64-bit Fire TV 4K / Max, Shield TV, Modern Android TV & Phones | **32.9 MB** |
| **`vidiio-v2.0.2-armeabi-v7a.apk`** | 32-bit Fire TV Stick Lite / 3rd Gen / Legacy TV Sticks | **33.4 MB** |
| **`vidiio-v2.0.2-x86_64.apk`** | Android x86_64 TV Emulators / PC Android OS | **34.1 MB** |

---

## 💻 Local File Locations

- **Universal APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.2-universal.apk`
- **64-Bit ARM APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.2-arm64-v8a.apk`
- **Release Directory:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\release\`

---

## 🌐 Source Code & Releases

- **GitHub Repository:** [github.com/ominixisboss/vidiio](https://github.com/ominixisboss/vidiio)
- **Git Release Tag:** `v2.0.2`

# Vidiio 2.0.3 Release Notes 🚀

**Release Version:** `v2.0.3`  
**Target SDK:** Android 14 (API 37) | **Min SDK:** Android 7.0 / Fire OS 6 (API 24)  
**Release Date:** November 28, 2026  

---

## 🌟 What's New in Vidiio 2.0.3

### 1. 🏷️ Smart Stream Link Badges & Quality Indicators
- **High-Quality Badges:** Automatically identifies and labels stream links with crisp visual badges:
  - **`RD+ Cached`** (Debrid cached streams)
  - **`4K UHD`** (4K 2160p resolution)
  - **`DV` / `HDR`** (Dolby Vision & HDR10+)
  - **`Atmos 7.1`** (Dolby Atmos & 7.1 Surround)
  - **`REMUX`** (Uncompressed Remux quality)
  - **`👥 Seeders`** (Live seeders count)

### 2. 🚫 CAM & Theater Recording Filter
- **CAM / TS Rip Filter:** Added a toggle setting under **Playback Settings** (`Filter Out CAM / TS Rips`). When enabled (ON by default), low-quality CAM, HDCAM, and Telesync theater recordings are automatically hidden.

### 3. 💬 Multi-Addon Subtitle Auto-Search
- Concurrently queries OpenSubtitles v3, SubSource, SubDL, and all installed subtitle addons at the same time, automatically selecting matching subtitle tracks on playback start.

### 4. 📺 Interactive EPG Timeline Grid & Xtream Codes IPTV
- **EPG Timeline Grid:** View live TV shows in a full-screen time grid (`7:00 PM`, `7:30 PM`, `8:00 PM`...) with channel logos and live indicator badges. Toggle between **EPG Grid** and **Schedule List** on the TV Guide screen.
- **Xtream Codes IPTV:** Added **Add Xtream Codes IPTV Account** in Settings -> Live TV to log in via Server URL, Username & Password with automated category mapping and EPG loading.

### 5. 🏴‍☠️ Updated One Pace Stremio Addon
- Updated One Pace Stremio addon manifest URL to `https://onepace.e6ip.com/eyJkZWJyaWRTZXJ2aWNlcyI6W3sic2VydmljZSI6InAycCJ9XX0=/manifest.json`.

### 6. 🔍 Multi-ID Stream Addon Querying
- Concurrently queries Stremio stream addons using IMDb IDs (`tt0944947:1:1`), TMDb IDs (`tmdb:1399:1:1`), and Addon-Native IDs.

---

## 📦 Shipped 2.0.3 APK Files

| APK Name | Target Devices | Size |
| :--- | :--- | :--- |
| **`vidiio-v2.0.3-universal.apk`** | All Fire TV Sticks, Google TV, Android TV Boxes & Phones | **117 MB** |
| **`vidiio-v2.0.3-arm64-v8a.apk`** | 64-bit Fire TV 4K / Max, Shield TV, Modern Android TV & Phones | **32.9 MB** |
| **`vidiio-v2.0.3-armeabi-v7a.apk`** | 32-bit Fire TV Stick Lite / 3rd Gen / Legacy TV Sticks | **33.4 MB** |
| **`vidiio-v2.0.3-x86_64.apk`** | Android x86_64 TV Emulators / PC Android OS | **34.1 MB** |

---

## 💻 Local File Locations

- **Universal APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.3-universal.apk`
- **64-Bit ARM APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.3-arm64-v8a.apk`
- **Release Directory:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\release\`

---

## 🌐 Source Code & Releases

- **GitHub Repository:** [github.com/ominixisboss/vidiio](https://github.com/ominixisboss/vidiio)
- **Git Release Tag:** `v2.0.3`

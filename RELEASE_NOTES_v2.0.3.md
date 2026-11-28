# Vidiio 2.0.3 Release Notes 🚀

**Release Version:** `v2.0.3`  
**Target SDK:** Android 14 (API 37) | **Min SDK:** Android 7.0 / Fire OS 6 (API 24)  
**Release Date:** November 28, 2026  

---

## 🌟 What's New in Vidiio 2.0.3

### 1. 🏷️ Provider Source Badges & Quality Indicators
- **Provider Badges:** Displays clear source badges on every stream link:
  - **`PlavioHD`** (Multi-source 4K/HD aggregator)
  - **`Torrentio`** (BitTorrent & Debrid streams)
  - **`KnightCrawler`** (Fast multi-source provider)
  - **`CyberFlix`** / **`Comet`** / **`MediaFusion`** (Stream addons)
  - **`Knaben`** / **`TorrentGalaxy`** / **`1337x`** / **`YTS`** (Torrent scrapers)
- **Quality Badges:** Visual tags for **`4K UHD`**, **`DV`** (Dolby Vision), **`HDR`**, **`Atmos 7.1`**, **`REMUX`**, **`RD+ Cached`**, and live seeders count.

### 2. 🚫 CAM & Theater Recording Filter
- **CAM / TS Rip Filter:** Added a toggle setting under **Playback Settings** (`Filter Out CAM / TS Rips`). When enabled (ON by default), low-quality CAM, HDCAM, and Telesync theater recordings are automatically hidden.

### 3. 🍿 PlavioHD Integration & Prioritization
- **Bundled PlavioHD Addon:** Pre-configured PlavioHD addon (`https://hd.plavio.site/u/.../manifest.json`) aggregating 50+ providers (*4KHDHub, 4KMirror, AniKage, AniZone, Castle, CineCalidad, CineVood, Cinezo, Codestream, Dexter, Dulo, Einthusan, HDHindi, HDHub4u, HDMovie4, HackStore, HindMovie, HindiLinks, Kinara, KissKH, KmMovies, LaMovie, MalluMV, Mapple, MaxVOD, MovieBox, MovieLinkBD, Movies4u, MoviesBazar, MoviesDrive, MoviesHunt, MoviesMod, Movy, MultiMovies, NetMirror, Nxsha, Obejrzyj, OndaSpanish, Play4u, RogMovies, SoraStream, SpaceDom, TamilBlasters, ToonWorld4All, UHDMovies, VAplayer, VegaMovies, VidBolt, VidFast, Vidlink, Vidrock, Way2Movies, WorldFree4u, Zeefliz4k*).
- **Prioritize PlavioHD Links:** Toggle setting under **Settings -> Addons** lists PlavioHD streams at the top.

### 4. 📺 Interactive EPG Timeline Grid & Xtream Codes IPTV
- **EPG Timeline Grid:** View live TV shows in a full-screen time grid (`7:00 PM`, `7:30 PM`, `8:00 PM`...) with channel logos and live indicator badges. Toggle between **EPG Grid** and **Schedule List** on the TV Guide screen.
- **Xtream Codes IPTV:** Added **Add Xtream Codes IPTV Account** in Settings -> Live TV to log in via Server URL, Username & Password with automated category mapping and EPG loading.

### 5. 💬 Multi-Addon Subtitle Auto-Search
- Concurrently queries OpenSubtitles v3, SubSource, SubDL, and all installed subtitle addons at the same time, automatically selecting matching subtitle tracks on playback start.

### 6. 🛡️ Built-in International VPN Locations
- **1-Tap Global Server Nodes:** Encrypt scraper, torrent tracker, and stream traffic through 5 fast proxy nodes:
  - 🇺🇸 **Charlotte, NC** (United States)
  - 🇨🇦 **Montreal** (Canada)
  - 🇬🇧 **London** (United Kingdom)
  - 🇩🇪 **Frankfurt** (Germany)
  - 🇯🇵 **Tokyo** (Japan)

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

# Vidiio 🍿

**Vidiio** is a high-performance, native streaming media player built with **Jetpack Compose for TV**, **ExoPlayer / Media3**, and **Kotlin Coroutines**. It brings Movies, TV Shows, Anime, and Live TV together into a fast, D-Pad remote optimized interface for Fire TV, Google TV, Android TV, and mobile devices.

---

## ✨ Features & Highlights

### 🎬 Universal Content Hub
* **Movies & TV Shows:** Trending titles, top-rated lists, popular shelves, and instant search powered by TMDb, Trakt, and Simkl metadata.
* **Anime Experience:** Integrated AniList metadata, episode guides, and automatic English audio/subtitles for dual-audio anime.
* **Live TV & EPG Grid:** Interactive time-grid TV Guide with channel logos, live badges, and Xtream Codes IPTV API integration.

### 🏷️ Smart Stream Link Badges & Quality Indicators
* **Provider Badges:** Displays clear source badges on every link:
  * **`PlavioHD`** (Multi-source 4K/HD aggregator)
  * **`Torrentio`** (BitTorrent & Debrid torrent streams)
  * **`KnightCrawler`** (Fast multi-source torrent provider)
  * **`CyberFlix`** (Catalog & stream provider)
  * **`Comet`** / **`MediaFusion`** (High-speed torrent & live streams)
  * **`Knaben`** / **`TorrentGalaxy`** / **`1337x`** / **`YTS`** (Direct torrent scrapers)
* **Quality Badges:** Visual tags for **`4K UHD`**, **`DV`** (Dolby Vision), **`HDR`**, **`Atmos 7.1`**, **`REMUX`**, **`RD+ Cached`**, and live seeder counts.
* **CAM / TS Rip Filter:** Toggle in Playback Settings automatically hides low-quality CAM and theater recordings.

### 🔌 Stremio Addon Ecosystem & Remote Pairing
* **Bundled Stream Addons:** Pre-configured with Torrentio, KnightCrawler, CyberFlix, Comet, MediaFusion, and PlavioHD out-of-the-box.
* **Community Addon Store:** Browse and install community Stremio addons with 1 tap.
* **Add from Computer/Phone:** Paste addon manifest links or M3U playlists remotely from any phone or PC browser using a local URL and 4-digit PIN.
* **Automatic Failover:** Dead magnet links automatically failover to working sources.

### 📺 Interactive EPG Timeline Grid & Xtream Codes IPTV
* **EPG Timeline Grid:** Full-screen time-slot grid (`7:00 PM`, `7:30 PM`, `8:00 PM`...) with channel logos and live program badges. Toggle between **EPG Grid** and **Schedule List** on the TV Guide screen.
* **Xtream Codes API:** Log in with your IPTV provider via Server URL, Username & Password in Settings -> Live TV.

### 🛡️ Built-in International VPN (1-Tap Connection)
* **1-Tap Global Server Nodes:** Encrypt scrapers, streams, and live TV traffic through built-in fast proxy/VPN locations:
  * 🇺🇸 **Charlotte, NC** (United States)
  * 🇨🇦 **Montreal** (Canada)
  * 🇬🇧 **London** (United Kingdom)
  * 🇩🇪 **Frankfurt** (Germany)
  * 🇯🇵 **Tokyo** (Japan)

### 👤 User Profiles & Kids Mode
* **Isolated Watch History:** Separate Watchlists, Continue Watching, and playback history per profile.
* **Kids Mode Filtering:** Filters catalogs and live feeds to family-friendly, G/PG, Animation, and Disney/Cartoon content.
* **Profile Switcher UI:** Convenient profile selection and creation dialogs with custom avatar colors.

### 🔊 Subtitles & Default Audio Selection
* **Default English Audio:** Automatically selects the English audio track for dual-audio anime and foreign releases on startup.
* **Multi-Addon Subtitle Search:** Concurrently queries OpenSubtitles v3, SubSource, SubDL, and installed subtitle addons.

### 📺 TV-Native UI & Remote Navigation
* **Clean Focus Highlights:** Built for Fire TV remotes and Android TV D-Pads with clear focus state indicators and zero focus traps.
* **Fire OS 6+ Support:** `minSdk = 24` ensures 100% compatibility with Fire OS 6, 7, 8, Google TV, and Android TV devices.

---

## 📦 APK Downloads & Releases

Pre-compiled production APKs are available on the [GitHub Releases](https://github.com/ominixisboss/vidiio/releases) page:

* **`vidiio-v2.0.3-universal.apk`** — Universal APK compatible with all Fire TV Sticks, Google TV, Android TV boxes, and phones.
* **`vidiio-v2.0.3-arm64-v8a.apk`** — 64-bit APK for Fire TV 4K, Shield TV, and modern Android TV devices.
* **`vidiio-v2.0.3-armeabi-v7a.apk`** — 32-bit APK for Fire TV Stick Lite and legacy devices.

---

## 🛠️ Building from Source

### Prerequisites
* **Android Studio Ladybug (2024.2.1+)** or newer.
* **JDK 17**.
* **Android SDK 35 / API 37 Build Tools**.

### Build Commands
```bash
# Clone the repository
git clone https://github.com/ominixisboss/vidiio.git
cd vidiio

# Build Debug APK
./gradlew app:assembleDebug

# Build Release APKs
./gradlew app:assembleRelease
```

---

## 📜 License
Made with ❤️ by OOZI for family and friends. Powered by Jetpack Compose & Kotlin.

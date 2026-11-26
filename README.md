# Vidiio 🍿

**Vidiio** is a high-performance, native streaming media player built with **Jetpack Compose for TV**, **ExoPlayer / Media3**, and **Kotlin Coroutines**. It brings Movies, TV Shows, Anime, and Live TV together into a fast, D-Pad remote optimized interface for Fire TV, Google TV, Android TV, and mobile devices.

---

## ✨ Features & Highlights

### 🎬 Universal Content Hub
* **Movies & TV Shows:** Trending titles, top-rated lists, popular shelves, and instant search powered by TMDb, Trakt, and Simkl metadata.
* **Anime Experience:** Integrated AniList metadata, episode guides, and automatic English audio/subtitles for dual-audio anime.
* **Live TV & Sports:** Built-in IPTV channel playlists, sports scores, and native HLS/M3U8 live-edge streaming.

### 🔌 Stremio Addon & Scraper Ecosystem
* **Community Addon Store:** Browse and install community Stremio addons with 1 tap.
* **Custom Addon Input:** Paste any custom Stremio manifest URL directly or pair remotely from a computer/phone browser with a 4-digit PIN.
* **Torrent & Scraper Integration:** Direct torrent magnet streaming via embedded TorrServer engine, paired with Knaben, TorrentGalaxy, 1337x, YTS, and scraper providers.
* **Automatic Failover:** Dead magnet links or metadata timeouts automatically failover to the next working stream source.

### 🛡️ Built-in International VPN (1-Tap Connection)
* **1-Tap Global Nodes:** Encrypt scrapers, streams, and live TV traffic through fast built-in proxy/VPN locations:
  * 🇺🇸 **Charlotte, NC** (United States)
  * 🇨🇦 **Montreal** (Canada)
  * 🇬🇧 **London** (United Kingdom)
  * 🇩🇪 **Frankfurt** (Germany)
  * 🇯🇵 **Tokyo** (Japan)

### 👤 User Profiles & Kids Mode
* **Isolated Watch History:** Separate Watchlists, Continue Watching, and playback history per profile.
* **Kids Mode Filtering:** Kids profiles automatically filter catalogs and streams to family-friendly, G/PG, Animation, and Disney/Cartoon content.
* **Profile Switcher UI:** Convenient profile selection and creation dialogs with custom avatar colors.

### 🔊 Subtitles & Default Audio Selection
* **Default English Audio:** Automatically selects the English audio track for dual-audio anime and foreign releases.
* **Captions & SubDL:** Subtitle appearance customization (text size, colors, edge styles) and SubDL API key support.

### 📺 TV-Native UI & Remote Navigation
* **Clean Focus Highlights:** Built for Fire TV remotes and Android TV D-Pads with clear focus state indicators and zero focus traps.
* **Fire OS 6+ Support:** `minSdk = 24` ensures 100% compatibility with Fire OS 6, 7, 8, Google TV, and Android TV devices.

---

## 📦 APK Downloads & Releases

Pre-compiled production APKs are available on the [GitHub Releases](https://github.com/ominixisboss/vidiio/releases) page:

* **`vidiio-v2.0.2-universal.apk`** — Universal APK compatible with all Fire TV Sticks, Google TV, Android TV boxes, and phones.
* **`vidiio-v2.0.2-arm64-v8a.apk`** — 64-bit APK for Fire TV 4K, Shield TV, and modern Android TV devices.
* **`vidiio-v2.0.2-armeabi-v7a.apk`** — 32-bit APK for Fire TV Stick Lite and legacy devices.

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

# Vidiio 2.0.1 Release Notes 🚀

**Release Version:** `v2.0.1`  
**Target SDK:** Android 14 (API 37) | **Min SDK:** Android 7.0 / Fire OS 6 (API 24)  
**Release Date:** November 17, 2026  

---

## 🌟 Key Highlights & New Features

### 1. 🎨 Redesigned TV Settings Menu & TV-Native Focus Navigation
- **Unified Category Cards:** Simplified settings into clean, intuitive sections matching modern TV UI design (*App Settings & Profiles*, *Playback Settings*, *Subtitles & Audio*, *Integrations & Metadata Sources*, *Privacy & VPN*).
- **Smooth D-Pad Focus:** Removed outer container highlight boxes in sub-pages. Every setting item is now an independently focusable row with crisp highlight feedback and zero D-Pad focus traps.

### 2. 🛡️ Built-in International VPN (1-Tap Connection)
- **Built-in USA & Global VPN Server Locations:** Integrated high-speed VPN endpoints with 1-tap switching right inside Settings:
  - 🇺🇸 **Charlotte, NC** (United States)
  - 🇨🇦 **Montreal** (Canada)
  - 🇬🇧 **London** (United Kingdom)
  - 🇩🇪 **Frankfurt** (Germany)
  - 🇯🇵 **Tokyo** (Japan)
- **Automatic Traffic Encapsulation:** Routes scrapers, Stremio addon queries, torrent trackers, stream health checks, and live TV feeds through fast encrypted proxy nodes.

### 3. 👤 User Profiles & Kids Mode
- **Isolated Watch History & Watchlists:** Create multiple user profiles (e.g., *Main Profile*, *Mom*, *Dad*, *Kids*). Switching profiles automatically reloads that profile's unique Watchlist, Continue Watching, and playback history.
- **Kids Profile Mode:** Toggleable Kids Mode filters Home, Movies, TV, and Live TV streams to family-friendly, G/PG, Animation, Disney, and Cartoon content.
- **Profile Switcher UI:** Convenient profile selection and creation dialogs with custom avatar colors and Kids Mode toggles.

### 4. 🔊 Default English Audio Auto-Selection
- **Subtitles & Audio Unification:** Grouped subtitle appearance, SubDL API key, and audio track preferences together in a single section.
- **Default English Audio:** Automatically selects the English audio track for dual-audio anime, foreign films, and multi-lingual releases on playback startup.

### 5. 🔑 Custom Stremio Metadata Addon URL Selector
- **Custom Metadata Addon Support:** Expanded **Metadata Source** selection dialog (*TMDb*, *Trakt*, *Simkl*, *Addons*).
- **Direct Manifest URL Input:** Tapping **Addons** lets users paste any custom Stremio metadata manifest URL (e.g. Cinemeta, Anime Kitsu, CyberFlix, TMDB addon) to set it active with 1 click.

### 6. 🔄 Automatic Torrent Failover
- **Silent Failover on Dead Torrents:** When a magnet link times out or encounters a metadata fetch error, Vidiio automatically marks the failed magnet link as `DEAD` and silently switches to the next available working stream source.

### 7. 🔥 Fire TV OS 6+ Compatibility
- **MinSDK 24:** Natively supports Fire OS 6 (Fire TV Stick 4K 1st Gen), Fire OS 7 (Fire TV Stick Lite / 3rd Gen), Fire OS 8 (4K Max 2nd Gen), and all Google TV / Android TV devices.

---

## 📦 Shipped APK Files

| APK Name | Target Devices | Size |
| :--- | :--- | :--- |
| **`vidiio-v2.0.1-universal.apk`** | All Fire TV Sticks, Google TV, Android TV Boxes & Phones | **117 MB** |
| **`vidiio-v2.0.1-arm64-v8a.apk`** | 64-bit Fire TV 4K / Max, Shield TV, Modern Android TV & Phones | **32.9 MB** |
| **`vidiio-v2.0.1-armeabi-v7a.apk`** | 32-bit Fire TV Stick Lite / 3rd Gen / Legacy TV Sticks | **33.4 MB** |
| **`vidiio-v2.0.1-x86_64.apk`** | Android x86_64 TV Emulators / PC Android OS | **34.1 MB** |

---

## 💻 Local File Locations

- **Universal APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.1-universal.apk`
- **64-Bit ARM APK:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\vidiio-v2.0.1-arm64-v8a.apk`
- **Release Directory:** `C:\Users\Roberto Jr\AndroidStudioProjects\vidiio\release\`

---

## 🌐 Source Code & Releases

- **GitHub Repository:** [github.com/ominixisboss/vidiio](https://github.com/ominixisboss/vidiio)
- **Git Release Tag:** `v2.0.1`

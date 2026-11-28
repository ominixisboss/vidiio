package com.ominix.vidiio.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.ominix.vidiio.data.repository.AppTheme
import com.ominix.vidiio.data.repository.ColorTheme
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.data.repository.MediaPlayerChoice
import com.ominix.vidiio.data.repository.ProxyConfig
import com.ominix.vidiio.data.repository.ProxyType
import com.ominix.vidiio.data.repository.SettingsRepository
import com.ominix.vidiio.data.repository.SubtitleStyle
import com.ominix.vidiio.ui.player.ExternalPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val addonManager: com.ominix.vidiio.data.stremio.AddonManager,
    private val context: Context,
    private val profileRepository: com.ominix.vidiio.data.repository.ProfileRepository? = null
) : ViewModel() {

    val allProfiles: StateFlow<List<com.ominix.vidiio.data.model.UserProfile>> =
        profileRepository?.allProfiles?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            ?: MutableStateFlow(emptyList())

    val activeProfileId: StateFlow<String> =
        settingsRepository.activeProfileIdFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    fun switchProfile(id: String) {
        viewModelScope.launch {
            profileRepository?.switchProfile(id)
        }
    }

    fun createProfile(name: String, colorHex: Long, isKids: Boolean) {
        viewModelScope.launch {
            profileRepository?.createProfile(name, colorHex, isKids)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileRepository?.deleteProfile(id)
        }
    }

    val playbackQuality: StateFlow<String> = settingsRepository.playbackQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Auto")

    val mediaPlayer: StateFlow<MediaPlayerChoice> = settingsRepository.mediaPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MediaPlayerChoice.INTERNAL)

    val theme: StateFlow<AppTheme> = settingsRepository.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.DARK)

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.colorThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.RED)

    val homeStyle: StateFlow<HomeStyle> = settingsRepository.homeStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStyle.VIDIIO)

    val avoidCameraCutout: StateFlow<Boolean> = settingsRepository.avoidCameraCutoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val prioritizePengu: StateFlow<Boolean> = settingsRepository.prioritizePenguFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val mouseToggle: StateFlow<Boolean> = settingsRepository.mouseToggleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val subtitlesEnabledByDefault: StateFlow<Boolean> = settingsRepository.subtitlesEnabledByDefaultFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val proxyEnabled: StateFlow<Boolean> = settingsRepository.proxyEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val proxyType: StateFlow<ProxyType> = settingsRepository.proxyTypeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProxyType.SOCKS5)
    val proxyHost: StateFlow<String> = settingsRepository.proxyHostFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val proxyPort: StateFlow<Int> = settingsRepository.proxyPortFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val proxyUser: StateFlow<String> = settingsRepository.proxyUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val proxyPass: StateFlow<String> = settingsRepository.proxyPassFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _proxyTestResult = MutableStateFlow<String?>(null)
    val proxyTestResult: StateFlow<String?> = _proxyTestResult.asStateFlow()

    val selectedSources: StateFlow<Set<String>> = settingsRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), setOf("vidsrc", "videasy", "vadapav", "vuflix", "cinejoy", "movy", "a111477", "knaben", "tg"))

    val stremioAddons: StateFlow<Set<String>> = settingsRepository.stremioAddonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Playlists and streams the viewer added to Live TV. */
    val liveLinks: StateFlow<List<com.ominix.vidiio.data.repository.LiveLink>> = settingsRepository.liveLinksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun addLiveLinks(links: List<com.ominix.vidiio.data.repository.LiveLink>) {
        if (links.isNotEmpty()) settingsRepository.addLiveLinks(links)
    }

    /** Stremio addons added for Live TV only. */
    val liveAddons: StateFlow<Set<String>> = settingsRepository.liveAddonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val metadataProvider: StateFlow<com.ominix.vidiio.data.repository.MetadataProvider> = settingsRepository.metadataProviderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ominix.vidiio.data.repository.MetadataProvider.TMDB)

    fun setMetadataProvider(provider: com.ominix.vidiio.data.repository.MetadataProvider) {
        viewModelScope.launch {
            settingsRepository.setMetadataProvider(provider)
        }
    }

    val preferredAudioLanguage: StateFlow<String> = settingsRepository.preferredAudioLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun setPreferredAudioLanguage(lang: String) {
        viewModelScope.launch {
            settingsRepository.setPreferredAudioLanguage(lang)
        }
    }

    val excludeCamStreams: StateFlow<Boolean> = settingsRepository.excludeCamStreamsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setExcludeCamStreams(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setExcludeCamStreams(enabled)
        }
    }

    val pinnedCatalogs: StateFlow<Set<String>> = settingsRepository.pinnedCatalogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun togglePinCatalog(catalogName: String) {
        viewModelScope.launch {
            settingsRepository.togglePinCatalog(catalogName)
        }
    }

    fun toggleCharlotteVpn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.enableCharlotteVpn(enabled)
        }
    }

    val xtreamAccounts: StateFlow<List<com.ominix.vidiio.data.live.XtreamAccount>> = settingsRepository.xtreamAccountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addXtreamAccount(account: com.ominix.vidiio.data.live.XtreamAccount) {
        viewModelScope.launch {
            settingsRepository.addXtreamAccount(account)
        }
    }

    fun removeXtreamAccount(id: String) {
        viewModelScope.launch {
            settingsRepository.removeXtreamAccount(id)
        }
    }

    val metadataAddons: StateFlow<Set<String>> = settingsRepository.metadataAddonsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun addMetadataAddon(url: String) {
        viewModelScope.launch {
            settingsRepository.addMetadataAddon(url)
        }
    }

    fun removeMetadataAddon(url: String) {
        viewModelScope.launch {
            settingsRepository.removeMetadataAddon(url)
        }
    }

    val addonStoreEntries = MutableStateFlow<List<com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry>>(emptyList())
    val isAddonStoreLoading = MutableStateFlow(false)

    fun loadAddonStore() {
        if (addonStoreEntries.value.isNotEmpty() || isAddonStoreLoading.value) return
        viewModelScope.launch {
            isAddonStoreLoading.value = true
            addonStoreEntries.value = addonManager.getAddonStoreEntries()
            isAddonStoreLoading.value = false
        }
    }

    fun installAddonFromStore(url: String, isLive: Boolean = false) {
        viewModelScope.launch {
            if (isLive) settingsRepository.addLiveAddons(listOf(url))
            else addonManager.installAddon(url)
        }
    }

    fun uninstallAddonFromStore(url: String) {
        viewModelScope.launch {
            addonManager.uninstallAddon(url)
        }
    }

    suspend fun addLiveAddons(urls: List<String>) {
        if (urls.isNotEmpty()) settingsRepository.addLiveAddons(urls)
    }

    fun removeLiveAddon(url: String) {
        viewModelScope.launch { settingsRepository.removeLiveAddon(url) }
    }

    fun removeLiveLink(url: String) {
        viewModelScope.launch { settingsRepository.removeLiveLink(url) }
    }

    val subdlApiKey: StateFlow<String?> = settingsRepository.subdlApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Subtitle appearance, applied to the player's SubtitleView. */
    val subtitleStyle: StateFlow<SubtitleStyle> = settingsRepository.subtitleStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleStyle())

    /** True: ASS tracks keep their own styling. False: they follow the settings above. */
    val assNativeStyling: StateFlow<Boolean> = settingsRepository.assNativeStylingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAssNativeStyling(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAssNativeStyling(enabled) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        viewModelScope.launch { settingsRepository.setSubtitleStyle(style) }
    }

    /** Preferred subtitle languages, most-wanted first. */
    val subtitleLanguages: StateFlow<List<String>> = settingsRepository.subtitleLanguagesFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsRepository.DEFAULT_SUBTITLE_LANGUAGES
        )

    fun setPlaybackQuality(quality: String) {
        viewModelScope.launch {
            settingsRepository.setPlaybackQuality(quality)
        }
    }

    fun setMediaPlayer(choice: MediaPlayerChoice) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayer(choice)
        }
    }

    fun isVlcInstalled(): Boolean = ExternalPlayer.isVlcInstalled(context)

    fun openVlcInPlayStore() = ExternalPlayer.openVlcInPlayStore(context)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch {
            settingsRepository.setColorTheme(theme)
        }
    }

    fun setHomeStyle(style: HomeStyle) {
        viewModelScope.launch {
            settingsRepository.setHomeStyle(style)
        }
    }



    fun setAvoidCameraCutout(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAvoidCameraCutout(enabled)
        }
    }

    fun setPrioritizePengu(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPrioritizePengu(enabled) }
    }

    val prioritizePlavioHd: StateFlow<Boolean> = settingsRepository.prioritizePlavioHdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPrioritizePlavioHd(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPrioritizePlavioHd(enabled) }
    }

    fun setMouseToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMouseToggle(enabled)
        }
    }

    fun setSubtitlesEnabledByDefault(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSubtitlesEnabledByDefault(enabled)
        }
    }

    fun saveProxy(enabled: Boolean, type: ProxyType, host: String, port: Int, user: String, pass: String) {
        viewModelScope.launch {
            settingsRepository.setProxy(enabled, type, host, port, user, pass)
        }
    }

    fun clearProxyTestResult() { _proxyTestResult.value = null }

    /** Fetches the public IP through the given proxy so the user can confirm it works. */
    fun testProxy(type: ProxyType, host: String, port: Int, user: String, pass: String) {
        viewModelScope.launch {
            _proxyTestResult.value = "Testing…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val cfg = ProxyConfig(type, host.trim(), port, user.trim().ifBlank { null }, pass.ifBlank { null })
                    val jType = if (type == ProxyType.SOCKS5) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
                    val builder = okhttp3.OkHttpClient.Builder()
                        .proxy(java.net.Proxy(jType, java.net.InetSocketAddress.createUnresolved(cfg.host, cfg.port)))
                        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    if (type == ProxyType.HTTP && !cfg.username.isNullOrBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(cfg.username, cfg.password ?: ""))
                                .build()
                        }
                    }
                    if (type == ProxyType.SOCKS5 && !cfg.username.isNullOrBlank()) {
                        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                            override fun getPasswordAuthentication() =
                                java.net.PasswordAuthentication(cfg.username, (cfg.password ?: "").toCharArray())
                        })
                    }
                    val client = builder.build()
                    val req = okhttp3.Request.Builder().url("https://api.ipify.org?format=text").build()
                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) "Connected — public IP: ${r.body?.string()?.trim()}"
                        else "Proxy responded ${r.code}"
                    }
                }.getOrElse { "Failed: ${it.message ?: it.javaClass.simpleName}" }
            }
            _proxyTestResult.value = result
        }
    }

    fun toggleSource(source: String) {
        viewModelScope.launch {
            val currentSources = selectedSources.value.toMutableSet()
            if (currentSources.contains(source)) {
                if (currentSources.size > 1) {
                    currentSources.remove(source)
                }
            } else {
                currentSources.add(source)
            }
            settingsRepository.setSources(currentSources)
        }
    }

    fun addStremioAddon(url: String) {
        viewModelScope.launch {
            val currentAddons = stremioAddons.value.toMutableSet()
            currentAddons.add(url)
            settingsRepository.setStremioAddons(currentAddons)
        }
    }

    /** Adds several links at once (from the computer page); reads the stored set fresh so batches don't overwrite each other. */
    suspend fun addStremioAddons(urls: List<String>) {
        val current = settingsRepository.stremioAddonsFlow.first().toMutableSet()
        current.addAll(urls)
        settingsRepository.setStremioAddons(current)
    }

    fun removeStremioAddon(url: String) {
        viewModelScope.launch {
            val currentAddons = stremioAddons.value.toMutableSet()
            currentAddons.remove(url)
            settingsRepository.setStremioAddons(currentAddons)
        }
    }

    fun setSubdlApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.setSubdlApiKey(apiKey)
        }
    }

    /** Accepts a comma-separated list, e.g. "en, es, fr". Order is the preference order. */
    fun setSubtitleLanguages(raw: String) {
        viewModelScope.launch {
            settingsRepository.setSubtitleLanguages(raw.split(","))
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }
}

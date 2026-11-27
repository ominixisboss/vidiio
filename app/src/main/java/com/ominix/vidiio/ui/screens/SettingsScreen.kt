package com.ominix.vidiio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ominix.vidiio.BuildConfig
import com.ominix.vidiio.data.repository.AppTheme
import com.ominix.vidiio.data.repository.ColorTheme
import com.ominix.vidiio.data.repository.HomeStyle
import com.ominix.vidiio.data.repository.MediaPlayerChoice
import com.ominix.vidiio.data.repository.SubtitleEdge
import com.ominix.vidiio.data.repository.SubtitleStyle
import com.ominix.vidiio.ui.components.tvClickable
import com.ominix.vidiio.ui.tv.LocalTvPageFocus
import com.ominix.vidiio.ui.tv.rememberDialogMenuHandoff
import com.ominix.vidiio.ui.tv.rememberDialogReturnFocus
import com.ominix.vidiio.ui.tv.tvDialogFocus
import com.ominix.vidiio.ui.tv.tvDialogMenuToRail
import com.ominix.vidiio.ui.theme.*
import com.ominix.vidiio.ui.viewmodel.SettingsViewModel

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingCardRow(
    title: String,
    icon: ImageVector,
    statusText: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvClickable(onClick = onClick, shape = shape),
        shape = shape,
        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            // Status below the title: at 1.5x fonts a right-aligned status squeezed titles such as
            // "App Settings, Profiles & Appearance" into an ellipsis with no way to read them.
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!statusText.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackQuality by viewModel.playbackQuality.collectAsState()
    val mediaPlayer by viewModel.mediaPlayer.collectAsState()
    val isVlcInstalled = remember { viewModel.isVlcInstalled() }
    val theme by viewModel.theme.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val homeStyle by viewModel.homeStyle.collectAsState()
    val avoidCameraCutout by viewModel.avoidCameraCutout.collectAsState()
    val mouseToggle by viewModel.mouseToggle.collectAsState()
    val prioritizePengu by viewModel.prioritizePengu.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    val stremioAddons by viewModel.stremioAddons.collectAsState()
    val liveLinks by viewModel.liveLinks.collectAsState()
    val liveAddons by viewModel.liveAddons.collectAsState()
    val subdlApiKey by viewModel.subdlApiKey.collectAsState()
    val subtitleLanguages by viewModel.subtitleLanguages.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()
    val subtitlesEnabledByDefault by viewModel.subtitlesEnabledByDefault.collectAsState()
    val assNativeStyling by viewModel.assNativeStyling.collectAsState()
    val proxyEnabled by viewModel.proxyEnabled.collectAsState()
    val proxyType by viewModel.proxyType.collectAsState()
    val proxyHost by viewModel.proxyHost.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val proxyUser by viewModel.proxyUser.collectAsState()
    val proxyPass by viewModel.proxyPass.collectAsState()
    val metadataProvider by viewModel.metadataProvider.collectAsState()
    val metadataAddons by viewModel.metadataAddons.collectAsState()
    val preferredAudioLanguage by viewModel.preferredAudioLanguage.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()

    val addonStoreEntries by viewModel.addonStoreEntries.collectAsState()
    val isAddonStoreLoading by viewModel.isAddonStoreLoading.collectAsState()
    var showAddonStoreSheet by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Hands focus back to the row a dialog was opened from, or to the page when that row is gone.
    val returnFocus = rememberDialogReturnFocus()
    val focusPage = LocalTvPageFocus.current
    // A dialog opened from another dialog takes that inner row with it; keep the row behind it so
    // leaving the side rail does not land on the page's first control.
    val handOffDialogFocus = rememberDialogMenuHandoff()

    // One section open at a time keeps the page short, so the remote never has far to scroll.
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }

    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateProfileDialog by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var newProfileIsKids by rememberSaveable { mutableStateOf(false) }

    var showMetadataProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showAddMetadataDialog by rememberSaveable { mutableStateOf(false) }
    var metadataToRemove by remember { mutableStateOf<String?>(null) }
    var metadataUrlToAdd by rememberSaveable { mutableStateOf("") }

    var showProxyDialog by rememberSaveable { mutableStateOf(false) }
    var showAddAddonDialog by rememberSaveable { mutableStateOf(false) }
    var showAddFromPcDialog by rememberSaveable { mutableStateOf(false) }
    var showAddLiveLinkDialog by rememberSaveable { mutableStateOf(false) }
    var liveLinkToRemove by remember { mutableStateOf<com.ominix.vidiio.data.repository.LiveLink?>(null) }
    var liveAddonToRemove by remember { mutableStateOf<String?>(null) }
    var addonToRemove by remember { mutableStateOf<String?>(null) }
    var focusAddAfterRemoval by remember { mutableStateOf(false) }
    var addonUrlToAdd by rememberSaveable { mutableStateOf("") }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var apiKeyToSet by rememberSaveable { mutableStateOf("") }
    var showSubtitleLanguagesDialog by rememberSaveable { mutableStateOf(false) }
    var subtitleLanguagesToSet by rememberSaveable { mutableStateOf("") }

    // Text typed in a dialog survives Menu closing it for the side rail; Cancel and Save start clean.
    var createProfileNameDraft by rememberSaveable { mutableStateOf("") }
    var liveLinkNameDraft by rememberSaveable { mutableStateOf("") }
    var liveLinkUrlDraft by rememberSaveable { mutableStateOf("") }
    var proxyDraftActive by rememberSaveable { mutableStateOf(false) }
    var proxyDraftTypeName by rememberSaveable { mutableStateOf("") }
    var proxyDraftHost by rememberSaveable { mutableStateOf("") }
    var proxyDraftPort by rememberSaveable { mutableStateOf("") }
    var proxyDraftUser by rememberSaveable { mutableStateOf("") }
    var proxyDraftPass by rememberSaveable { mutableStateOf("") }
    var addonStoreSearchDraft by rememberSaveable { mutableStateOf("") }

    fun clearProxyDraft() {
        proxyDraftActive = false
    }

    // Reopening after Menu keeps what was typed; otherwise start from the saved proxy settings.
    fun openProxyDialog() {
        if (!proxyDraftActive) {
            proxyDraftActive = true
            proxyDraftTypeName = proxyType.name
            proxyDraftHost = proxyHost
            proxyDraftPort = if (proxyPort > 0) proxyPort.toString() else ""
            proxyDraftUser = proxyUser
            proxyDraftPass = proxyPass
        }
        showProxyDialog = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (openSection == null) {
                // --- GENERAL ---
                item(key = "hdr-general") { SectionHeader("GENERAL") }
                item(key = "sec-app-appearance") {
                    SettingCardRow(
                        title = "App Settings, Profiles & Appearance",
                        icon = Icons.Rounded.AccountCircle,
                        statusText = "${colorTheme.name.lowercase().replaceFirstChar { it.titlecase() }} · ${homeStyle.spec(MaterialTheme.colorScheme.primary).label}",
                        onClick = { openSection = "app" }
                    )
                }
                item(key = "sec-playback") {
                    SettingCardRow(
                        title = "Playback Settings",
                        icon = Icons.Rounded.PlayCircle,
                        statusText = "$playbackQuality · ${if (mediaPlayer == MediaPlayerChoice.VLC) "VLC" else "ExoPlayer"}",
                        onClick = { openSection = "playback" }
                    )
                }
                item(key = "sec-subtitles") {
                    SettingCardRow(
                        title = "Subtitles & Audio Settings",
                        icon = Icons.Rounded.Subtitles,
                        statusText = "${if (subtitlesEnabledByDefault) "English Subs" else "Subs Off"} · ${if (preferredAudioLanguage == "en") "English Audio" else "Original Audio"}",
                        onClick = { openSection = "subtitles" }
                    )
                }

                // --- INTEGRATIONS & SOURCES ---
                item(key = "hdr-integrations") { SectionHeader("INTEGRATIONS & SOURCES") }
                item(key = "sec-metadata") {
                    SettingCardRow(
                        title = "Metadata Source",
                        icon = Icons.Rounded.Category,
                        statusText = metadataProvider.label,
                        onClick = { showMetadataProviderDialog = true }
                    )
                }
                item(key = "sec-addons") {
                    SettingCardRow(
                        title = "Addons",
                        icon = Icons.Rounded.Extension,
                        statusText = "${stremioAddons.size} Installed",
                        onClick = { openSection = "addons" }
                    )
                }
                item(key = "sec-live") {
                    SettingCardRow(
                        title = "Live TV Links & Addons",
                        icon = Icons.Rounded.LiveTv,
                        statusText = "${liveAddons.size + liveLinks.size} Active",
                        onClick = { openSection = "live" }
                    )
                }
                item(key = "sec-sources") {
                    SettingCardRow(
                        title = "Scrapers & Providers",
                        icon = Icons.Rounded.Search,
                        statusText = "${selectedSources.size} Selected",
                        onClick = { openSection = "sources" }
                    )
                }
                item(key = "sec-privacy") {
                    SettingCardRow(
                        title = "Privacy & Proxy",
                        icon = Icons.Rounded.Shield,
                        statusText = when {
                            proxyEnabled && proxyHost.contains("charlotte") -> "Charlotte VPN On"
                            proxyEnabled -> "Custom Proxy On"
                            else -> "Off"
                        },
                        onClick = { openSection = "privacy" }
                    )
                }
            } else {
                item(key = "back-to-sections") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .tvClickable(onClick = { openSection = null }, shape = RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back to All Settings", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (openSection == "app" || openSection == "appearance") {
                    item(key = "hdr-app-profiles") { SectionHeader("USER PROFILES") }
                    item(key = "app-profile-item") {
                        val activeName = allProfiles.firstOrNull { it.id == activeProfileId }?.name ?: "Main Profile"
                        PreferenceItem(
                            title = "Switch Profile",
                            summary = "Current: $activeName · Create & manage profiles",
                            icon = Icons.Rounded.AccountCircle,
                            onClick = { showProfileDialog = true }
                        )
                    }
                    item(key = "hdr-app-theme") { SectionHeader("PROFILES & APPEARANCE") }
                    item(key = "app-color") {
                        PreferenceItem(
                            title = "Color Theme",
                            summary = colorTheme.name.lowercase().replaceFirstChar { it.titlecase() },
                            icon = Icons.Rounded.Palette,
                            onClick = null
                        )
                    }
                    item(key = "app-color-row") {
                        ColorThemeSelectionRow(
                            currentColorTheme = colorTheme,
                            onColorThemeSelected = { viewModel.setColorTheme(it) }
                        )
                    }
                    item(key = "app-layout") {
                        PreferenceItem(
                            title = "Home Layout",
                            summary = homeStyle.spec(MaterialTheme.colorScheme.primary).label + " — restyles the Home tab",
                            icon = Icons.Rounded.Dashboard,
                            onClick = null
                        )
                    }
                    item(key = "app-layout-row") {
                        HomeStyleSelectionRow(
                            current = homeStyle,
                            onSelected = { viewModel.setHomeStyle(it) }
                        )
                    }
                    item(key = "hdr-app-prefs") { SectionHeader("APP PREFERENCES") }
                    item(key = "app-cutout") {
                        SwitchPreferenceItem(
                            title = "Keep camera hole clear",
                            summary = "Player fills the screen but stops short of the front camera",
                            icon = Icons.Rounded.AspectRatio,
                            checked = avoidCameraCutout,
                            onCheckedChange = { viewModel.setAvoidCameraCutout(it) }
                        )
                    }
                    item(key = "app-mouse") {
                        SwitchPreferenceItem(
                            title = "Mouse Toggle",
                            summary = "Enable virtual pointer overlay for TV navigation",
                            icon = Icons.Rounded.Mouse,
                            checked = mouseToggle,
                            onCheckedChange = { viewModel.setMouseToggle(it) }
                        )
                    }
                }

                if (openSection == "playback") {
                    item(key = "hdr-pb") { SectionHeader("PLAYBACK SETTINGS") }
                    item(key = "pb-player") {
                        PreferenceItem(
                            title = "Preferred Media Player",
                            summary = when (mediaPlayer) {
                                MediaPlayerChoice.INTERNAL -> "Internal Player (ExoPlayer)"
                                MediaPlayerChoice.VLC -> if (isVlcInstalled) "VLC Media Player" else "VLC Media Player (Not Installed)"
                            },
                            icon = Icons.Rounded.Tv,
                            onClick = null
                        )
                    }
                    item(key = "pb-player-row") {
                        MediaPlayerSelectionRow(
                            currentChoice = mediaPlayer,
                            isVlcInstalled = isVlcInstalled,
                            onChoiceSelected = { choice -> viewModel.setMediaPlayer(choice) },
                            onInstallVlc = { viewModel.openVlcInPlayStore() }
                        )
                    }
                    item(key = "pb-qual") {
                        PreferenceItem(
                            title = "Default Quality",
                            summary = playbackQuality,
                            icon = Icons.Rounded.HighQuality,
                            onClick = null
                        )
                    }
                    item(key = "pb-qual-row") {
                        QualitySelectionRow(
                            currentQuality = playbackQuality,
                            onQualitySelected = { viewModel.setPlaybackQuality(it) }
                        )
                    }
                    item(key = "pb-cache") {
                        PreferenceItem(
                            title = "Clear Cache",
                            summary = "Clear image and data cache",
                            icon = Icons.Rounded.DeleteSweep,
                            onClick = { viewModel.clearCache() }
                        )
                    }
                }

                if (openSection == "subtitles") {
                    item(key = "hdr-sub") { SectionHeader("SUBTITLES & CAPTIONS") }
                    item(key = "sub-enable") {
                        SwitchPreferenceItem(
                            title = "Subtitles",
                            summary = if (subtitlesEnabledByDefault) {
                                "Shown by default when you start playing something"
                            } else {
                                "Off by default; turn them on per-video from the player"
                            },
                            icon = Icons.Rounded.Subtitles,
                            checked = subtitlesEnabledByDefault,
                            onCheckedChange = { viewModel.setSubtitlesEnabledByDefault(it) }
                        )
                    }
                    item(key = "sub-lang") {
                        PreferenceItem(
                            title = "Subtitle languages",
                            summary = subtitleLanguages.joinToString(", ") { it.uppercase() },
                            icon = Icons.Rounded.Language,
                            onClick = {
                                subtitleLanguagesToSet = subtitleLanguages.joinToString(", ")
                                showSubtitleLanguagesDialog = true
                            }
                        )
                    }
                    item(key = "sub-key") {
                        PreferenceItem(
                            title = "SubDL API Key",
                            summary = if (subdlApiKey.isNullOrEmpty()) "Not set" else "••••••••",
                            icon = Icons.Rounded.Key,
                            onClick = {
                                apiKeyToSet = subdlApiKey ?: ""
                                showApiKeyDialog = true
                            }
                        )
                    }
                    item(key = "sub-ass") {
                        SwitchPreferenceItem(
                            title = "Keep ASS/SSA styling",
                            summary = if (assNativeStyling) {
                                "Fancy subtitles keep their own fonts and positioning."
                            } else {
                                "Fancy subtitles are shown as plain text so appearance settings apply."
                            },
                            icon = Icons.Rounded.Style,
                            checked = assNativeStyling,
                            onCheckedChange = { viewModel.setAssNativeStyling(it) }
                        )
                    }
                    item(key = "sub-style") {
                        SubtitleAppearanceSection(
                            style = subtitleStyle,
                            onStyleChange = { viewModel.setSubtitleStyle(it) }
                        )
                    }
                    item(key = "hdr-aud") { SectionHeader("AUDIO TRACK SETTINGS") }
                    item(key = "aud-eng") {
                        SwitchPreferenceItem(
                            title = "Default English Audio",
                            summary = if (preferredAudioLanguage == "en") {
                                "Automatically select English audio track for dual-audio anime and foreign releases"
                            } else {
                                "Play original audio track by default"
                            },
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            checked = preferredAudioLanguage == "en",
                            onCheckedChange = { enabled ->
                                viewModel.setPreferredAudioLanguage(if (enabled) "en" else "original")
                            }
                        )
                    }
                }

                if (openSection == "privacy") {
                    item(key = "hdr-priv") { SectionHeader("PRIVACY & PROXY") }
                    item(key = "priv-charlotte") {
                        val isCharlotteOn = proxyEnabled && proxyHost.contains("charlotte")
                        SwitchPreferenceItem(
                            title = "Built-in VPN (Charlotte, NC)",
                            summary = if (isCharlotteOn) {
                                "Connected to Charlotte, NC (USA) server"
                            } else {
                                "Route scrapers, streams and live TV traffic through Charlotte, NC server"
                            },
                            icon = Icons.Rounded.Shield,
                            checked = isCharlotteOn,
                            onCheckedChange = { viewModel.toggleCharlotteVpn(it) }
                        )
                    }
                    item(key = "priv-vpn") {
                        val isCustomOn = proxyEnabled && !proxyHost.contains("charlotte")
                        SwitchPreferenceItem(
                            title = "Custom Proxy (SOCKS5 / HTTP)",
                            summary = when {
                                !isCustomOn -> "Off — enter custom proxy host & credentials below"
                                proxyHost.isBlank() || proxyPort <= 0 -> "Unconfigured — open Proxy settings"
                                else -> "${proxyType.name} · $proxyHost:$proxyPort"
                            },
                            icon = Icons.Rounded.VpnKey,
                            checked = isCustomOn,
                            onCheckedChange = {
                                viewModel.saveProxy(it, proxyType, proxyHost, proxyPort, proxyUser, proxyPass)
                            }
                        )
                    }
                    item(key = "priv-cfg") {
                        PreferenceItem(
                            title = "Custom Proxy settings",
                            summary = "Host, port, credentials — test connection",
                            icon = Icons.Rounded.Tune,
                            onClick = { openProxyDialog() }
                        )
                    }
                }

                if (openSection == "sources") {
                    item(key = "hdr-src") { SectionHeader("SCRAPERS & PROVIDERS") }
                    val sources = listOf(
                        "knaben" to "Knaben",
                        "tg" to "TorrentGalaxy"
                    )
                    items(sources) { (id, name) ->
                        SourceToggleItem(
                            name = name,
                            enabled = selectedSources.contains(id),
                            onToggle = { viewModel.toggleSource(id) }
                        )
                    }
                }

                if (openSection == "addons") {
                    item(key = "hdr-addons") { SectionHeader("STREMIO ADDONS") }
                    item(key = "addon-pengu") {
                        SwitchPreferenceItem(
                            title = "Prioritize Pengu links",
                            summary = "List and auto-play PenguPlay streams first",
                            icon = Icons.Rounded.Extension,
                            checked = prioritizePengu,
                            onCheckedChange = { viewModel.setPrioritizePengu(it) }
                        )
                    }
                    items(stremioAddons.toList(), key = { "ad-$it" }) { addonUrl ->
                        AddonRow(addonUrl = addonUrl, onRemove = { addonToRemove = addonUrl })
                    }
                    item(key = "addon-store") {
                        PreferenceItem(
                            title = "Addon Store",
                            summary = "Browse & install community addons with one tap",
                            icon = Icons.Rounded.Store,
                            onClick = {
                                viewModel.loadAddonStore()
                                showAddonStoreSheet = true
                            }
                        )
                    }
                    item(key = "addon-url") {
                        PreferenceItem(
                            title = "Add Stremio Addon (Manifest URL)",
                            summary = "Paste manifest URL",
                            icon = Icons.Rounded.Add,
                            onClick = { showAddAddonDialog = true }
                        )
                    }
                    item(key = "addon-pc") {
                        PreferenceItem(
                            title = "Add from your computer or phone",
                            summary = "Open browser URL and enter 4-digit PIN",
                            icon = Icons.Rounded.Computer,
                            onClick = { showAddFromPcDialog = true }
                        )
                    }
                }

                if (openSection == "live") {
                    item(key = "hdr-live") { SectionHeader("LIVE TV LINKS & ADDONS") }
                    items(liveAddons.sortedBy { addonLabel(it).lowercase() }, key = { "la-$it" }) { url ->
                        PreferenceItem(
                            title = addonLabel(url),
                            summary = "Live TV addon \u00b7 OK to remove",
                            icon = Icons.Rounded.Extension,
                            onClick = { liveAddonToRemove = url }
                        )
                    }
                    items(liveLinks, key = { "ll-${it.url}" }) { link ->
                        PreferenceItem(
                            title = link.name,
                            summary = link.url,
                            icon = Icons.Rounded.LiveTv,
                            onClick = { liveLinkToRemove = link }
                        )
                    }
                    item(key = "live-add") {
                        PreferenceItem(
                            title = "Add link or addon",
                            summary = "A sports or TV addon (manifest.json), an M3U playlist or a stream link",
                            icon = Icons.Rounded.Add,
                            onClick = { showAddLiveLinkDialog = true }
                        )
                    }
                    item(key = "live-pc") {
                        PreferenceItem(
                            title = "Add from your computer or phone",
                            summary = "Open browser URL and enter 4-digit PIN",
                            icon = Icons.Rounded.Computer,
                            onClick = { showAddFromPcDialog = true }
                        )
                    }
                }

                if (openSection == "about") {
                    item(key = "hdr-about") { SectionHeader("ABOUT VIDIIO") }
                    item(key = "about-card") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                SelectionContainer {
                                    Column {
                                        Text(
                                            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Commit ${BuildConfig.GIT_SHA}${if (BuildConfig.DEBUG) " · debug" else ""}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val updater = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.ominix.vidiio.VidiioApplication).appUpdater
                                val updateScope = androidx.compose.runtime.rememberCoroutineScope()
                                var updateStatus by remember { mutableStateOf<String?>(null) }
                                TextButton(onClick = {
                                    updateStatus = "Checking…"
                                    updateScope.launch {
                                        val update = updater.check()
                                        updateStatus = if (update == null) "You're on the latest version." else null
                                        updater.offered.value = update
                                    }
                                }) { Text("Check for updates") }
                                updateStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "I made this app for my family and my homies. Enjoy! ❤️",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Movies, shows and anime in one place, powered by Stremio addons and multiple sources.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Made by OOZI with Jetpack Compose", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        val shape = RoundedCornerShape(20.dp)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showProfileDialog = false; returnFocus() }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = { handOffDialogFocus(); showProfileDialog = false }),
                shape = shape,
                color = Color(0xFF1E222A),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    // A landscape phone has ~450dp of height: the profile list has to scroll
                    // rather than run past the bottom of the dialog.
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "User Profiles",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    allProfiles.ifEmpty {
                        listOf(com.ominix.vidiio.data.model.UserProfile(id = "default", name = "Main Profile", isDefault = true))
                    }.forEach { profile ->
                        val isChosen = profile.id == activeProfileId
                        val itemShape = RoundedCornerShape(12.dp)
                        var itemFocused by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isChosen) Modifier.tvDialogFocus() else Modifier)
                                .onFocusChanged { itemFocused = it.isFocused }
                                .tvClickable(
                                    onClick = {
                                        viewModel.switchProfile(profile.id)
                                        showProfileDialog = false
                                        returnFocus()
                                        android.widget.Toast.makeText(context, "Switched to ${profile.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = itemShape
                                ),
                            shape = itemShape,
                            color = if (isChosen || itemFocused) Color.White else Color.White.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(profile.avatarColorHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (profile.isKids) Icons.Rounded.ChildCare else Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = profile.name,
                                            color = if (isChosen || itemFocused) Color.Black else Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (profile.isKids) {
                                            Text(
                                                text = "Kids Mode",
                                                color = if (isChosen || itemFocused) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                if (isChosen) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Active",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvClickable(
                                onClick = {
                                    showProfileDialog = false
                                    showCreateProfileDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Create New Profile", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCreateProfileDialog) {
        // Hoisted out of the dialog: Menu closes it to show the rail and must not lose the name.
        val createName = createProfileNameDraft
        var createIsKids by remember { mutableStateOf(false) }
        var selectedColorHex by remember { mutableStateOf(0xFFE50914) }

        val avatarColors = listOf(0xFFE50914, 0xFF0063E5, 0xFF1CE783, 0xFF9C27B0, 0xFFFF9800, 0xFF00BCD4)

        AlertDialog(
            onDismissRequest = { createProfileNameDraft = ""; showCreateProfileDialog = false; returnFocus() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Create New Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createProfileNameDraft = it },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Alex, Mom, Kids") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .tvDialogFocus(opensKeyboard = true)
                            .tvDialogMenuToRail(onCloseDialog = { handOffDialogFocus(); showCreateProfileDialog = false })
                    )

                    Text("Avatar Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        avatarColors.forEach { colorHex ->
                            val chosen = selectedColorHex == colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .border(
                                        if (chosen) 3.dp else 0.dp,
                                        if (chosen) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { selectedColorHex = colorHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (chosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Kids Profile Mode", fontWeight = FontWeight.Bold)
                            Text("Filters content to G/PG & Kids shows", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = createIsKids,
                            onCheckedChange = { createIsKids = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = createName.trim()
                        if (input.isNotBlank()) {
                            viewModel.createProfile(input, selectedColorHex, createIsKids)
                            createProfileNameDraft = ""
                            showCreateProfileDialog = false
                            returnFocus()
                            android.widget.Toast.makeText(context, "Profile '$input' created", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { createProfileNameDraft = ""; showCreateProfileDialog = false; returnFocus() }) { Text("Cancel") }
            }
        )
    }

    if (showMetadataProviderDialog) {
        val shape = RoundedCornerShape(20.dp)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMetadataProviderDialog = false; returnFocus() }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = { handOffDialogFocus(); showMetadataProviderDialog = false }),
                shape = shape,
                color = Color(0xFF1E222A),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Metadata Source",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    com.ominix.vidiio.data.repository.MetadataProvider.entries.forEach { option ->
                        val isChosen = option == metadataProvider
                        val itemShape = RoundedCornerShape(12.dp)
                        var itemFocused by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isChosen) Modifier.tvDialogFocus() else Modifier)
                                .onFocusChanged { itemFocused = it.isFocused }
                                .tvClickable(
                                    onClick = {
                                        viewModel.setMetadataProvider(option)
                                        showMetadataProviderDialog = false
                                        returnFocus()
                                    },
                                    shape = itemShape
                                ),
                            shape = itemShape,
                            color = if (isChosen || itemFocused) Color.White else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isChosen || itemFocused) Color.Black else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = if (isChosen || itemFocused) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isChosen) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (metadataProvider == com.ominix.vidiio.data.repository.MetadataProvider.ADDONS) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvClickable(
                                    onClick = {
                                        showMetadataProviderDialog = false
                                        showAddMetadataDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Paste Custom Metadata Addon URL", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showAddMetadataDialog) {
        AlertDialog(
            onDismissRequest = { showAddMetadataDialog = false; returnFocus() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Add Metadata Addon") },
            text = {
                OutlinedTextField(
                    value = metadataUrlToAdd,
                    onValueChange = { metadataUrlToAdd = it },
                    label = { Text("Manifest URL (e.g. Cinemeta / Kitsu)") },
                    placeholder = { Text("https://anime-kitsu.strem.fun/manifest.json") },
                    modifier = Modifier.fillMaxWidth()
                        .tvDialogFocus(opensKeyboard = true)
                        .tvDialogMenuToRail(onCloseDialog = { handOffDialogFocus(); showAddMetadataDialog = false }),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = metadataUrlToAdd.trim()
                        if (input.isNotBlank()) {
                            viewModel.addMetadataAddon(input)
                            viewModel.setMetadataProvider(com.ominix.vidiio.data.repository.MetadataProvider.ADDONS)
                            metadataUrlToAdd = ""
                            showAddMetadataDialog = false
                            returnFocus()
                            android.widget.Toast.makeText(context, "Metadata addon added", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Add & Set Active") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMetadataDialog = false; returnFocus() }) { Text("Cancel") }
            }
        )
    }

    // Dialogs preserved
    addonToRemove?.let { url ->
        AlertDialog(
            onDismissRequest = { addonToRemove = null; returnFocus() },
            modifier = Modifier.tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = { addonToRemove = null }),
            title = { Text("Remove this addon?") },
            text = { Text(addonLabel(url)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeStremioAddon(url)
                        addonToRemove = null
                        focusAddAfterRemoval = true
                        // The row this came from is gone, so there is no control to go back to.
                        focusPage?.invoke()
                    },
                    modifier = Modifier.tvDialogFocus()
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { addonToRemove = null; returnFocus() }) { Text("Keep") }
            }
        )
    }

    if (showAddAddonDialog) {
        AlertDialog(
            onDismissRequest = { showAddAddonDialog = false; returnFocus() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Add Stremio Addon") },
            text = {
                OutlinedTextField(
                    value = addonUrlToAdd,
                    onValueChange = { addonUrlToAdd = it },
                    label = { Text("Manifest URL") },
                    modifier = Modifier.fillMaxWidth()
                        .tvDialogFocus(opensKeyboard = true)
                        .tvDialogMenuToRail(onCloseDialog = { showAddAddonDialog = false }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = addonUrlToAdd.trim()
                        if (input.isNotBlank()) {
                            if (input.contains("stremio-addons.net", ignoreCase = true)) {
                                viewModel.loadAddonStore()
                                showAddonStoreSheet = true
                            } else {
                                viewModel.addStremioAddon(input)
                                returnFocus()
                            }
                            addonUrlToAdd = ""
                            showAddAddonDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddonDialog = false; returnFocus() }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showAddFromPcDialog) {
        AddFromComputerDialog(
            onLinks = { addons, live, liveAddonUrls ->
                if (addons.isNotEmpty()) viewModel.addStremioAddons(addons)
                viewModel.addLiveLinks(live)
                viewModel.addLiveAddons(liveAddonUrls)
            },
            onDismiss = { showAddFromPcDialog = false; returnFocus() },
            onMenuClose = { showAddFromPcDialog = false }
        )
    }

    liveLinkToRemove?.let { link ->
        AlertDialog(
            onDismissRequest = { liveLinkToRemove = null; returnFocus() },
            modifier = Modifier.tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = { liveLinkToRemove = null }),
            title = { Text("Remove this link?") },
            text = { Text("${link.name}\n${link.url}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeLiveLink(link.url)
                        liveLinkToRemove = null
                        focusPage?.invoke()
                    },
                    modifier = Modifier.tvDialogFocus()
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { liveLinkToRemove = null; returnFocus() }) { Text("Keep") }
            }
        )
    }

    liveAddonToRemove?.let { url ->
        AlertDialog(
            onDismissRequest = { liveAddonToRemove = null; returnFocus() },
            modifier = Modifier.tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = { liveAddonToRemove = null }),
            title = { Text("Remove this Live TV addon?") },
            text = { Text(addonLabel(url)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeLiveAddon(url)
                        liveAddonToRemove = null
                        focusPage?.invoke()
                    },
                    modifier = Modifier.tvDialogFocus()
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { liveAddonToRemove = null; returnFocus() }) { Text("Keep") }
            }
        )
    }

    if (showAddonStoreSheet) {
        AddonStoreSheet(
            entries = addonStoreEntries,
            isLoading = isAddonStoreLoading,
            installedAddons = stremioAddons + liveAddons,
            searchQuery = addonStoreSearchDraft,
            onSearchQueryChange = { addonStoreSearchDraft = it },
            onInstall = { url ->
                viewModel.installAddonFromStore(url)
                android.widget.Toast.makeText(context, "Addon installed", android.widget.Toast.LENGTH_SHORT).show()
            },
            onUninstall = { url ->
                viewModel.uninstallAddonFromStore(url)
                android.widget.Toast.makeText(context, "Addon removed", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { addonStoreSearchDraft = ""; showAddonStoreSheet = false; returnFocus() },
            onMenuClose = { showAddonStoreSheet = false }
        )
    }

    if (showAddLiveLinkDialog) {
        AddLiveLinkDialog(
            name = liveLinkNameDraft,
            onNameChange = { liveLinkNameDraft = it },
            url = liveLinkUrlDraft,
            onUrlChange = { liveLinkUrlDraft = it },
            onAdd = { name, url ->
                // A Stremio manifest is a Live TV addon; anything else is a playlist or stream.
                val normalized = url.replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
                if (normalized.substringBefore('?').endsWith("manifest.json")) viewModel.addLiveAddons(listOf(normalized))
                else viewModel.addLiveLinks(listOf(com.ominix.vidiio.data.repository.LiveLink(name.ifBlank { url }, url)))
            },
            onDismiss = {
                liveLinkNameDraft = ""
                liveLinkUrlDraft = ""
                showAddLiveLinkDialog = false
                returnFocus()
            },
            onMenuClose = { showAddLiveLinkDialog = false }
        )
    }

    if (showSubtitleLanguagesDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleLanguagesDialog = false; returnFocus() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Subtitle languages") },
            text = {
                Column {
                    Text(
                        "Two-letter codes, most wanted first. The order decides what " +
                            "providers are asked for and how the subtitle list is sorted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = subtitleLanguagesToSet,
                        onValueChange = { subtitleLanguagesToSet = it },
                        label = { Text("e.g. en, es, fr") },
                        modifier = Modifier.fillMaxWidth()
                            .tvDialogFocus(opensKeyboard = true)
                            .tvDialogMenuToRail(onCloseDialog = { showSubtitleLanguagesDialog = false }),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSubtitleLanguages(subtitleLanguagesToSet)
                        showSubtitleLanguagesDialog = false
                        returnFocus()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleLanguagesDialog = false; returnFocus() }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false; returnFocus() },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Set Subdl API Key") },
            text = {
                OutlinedTextField(
                    value = apiKeyToSet,
                    onValueChange = { apiKeyToSet = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                        .tvDialogFocus(opensKeyboard = true)
                        .tvDialogMenuToRail(onCloseDialog = { showApiKeyDialog = false }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSubdlApiKey(apiKeyToSet)
                        showApiKeyDialog = false
                        returnFocus()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false; returnFocus() }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showProxyDialog) {
        ProxySettingsDialog(
            draft = ProxyDraft(
                type = com.ominix.vidiio.data.repository.ProxyType.valueOf(proxyDraftTypeName),
                host = proxyDraftHost,
                port = proxyDraftPort,
                user = proxyDraftUser,
                pass = proxyDraftPass
            ),
            onDraftChange = {
                proxyDraftTypeName = it.type.name
                proxyDraftHost = it.host
                proxyDraftPort = it.port
                proxyDraftUser = it.user
                proxyDraftPass = it.pass
            },
            testResult = viewModel.proxyTestResult.collectAsState().value,
            onTest = { d -> viewModel.testProxy(d.type, d.host, d.port.toIntOrNull() ?: 0, d.user, d.pass) },
            onSave = { d ->
                viewModel.saveProxy(true, d.type, d.host, d.port.toIntOrNull() ?: 0, d.user, d.pass)
                viewModel.clearProxyTestResult()
                clearProxyDraft()
                showProxyDialog = false
                returnFocus()
            },
            onDismiss = { clearProxyDraft(); viewModel.clearProxyTestResult(); showProxyDialog = false; returnFocus() },
            onMenuClose = { viewModel.clearProxyTestResult(); showProxyDialog = false }
        )
    }
}

/** One Live TV addon, playlist or stream link, typed (or pasted) on the device. */
@Composable
private fun AddLiveLinkDialog(
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    onAdd: suspend (String, String) -> Unit,
    onDismiss: () -> Unit,
    onMenuClose: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val valid = listOf("http://", "https://", "stremio://").any { url.trim().startsWith(it, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link or addon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().tvDialogMenuToRail(onMenuClose))
                OutlinedTextField(value = url, onValueChange = onUrlChange, label = { Text("Addon, playlist or stream link") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvDialogFocus(opensKeyboard = true).tvDialogMenuToRail(onMenuClose))
                Text(
                    "Addon links end in manifest.json. Playlists (M3U) and stream links (.m3u8, .mpd) also work. Web page links won't play.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                scope.launch {
                    onAdd(name.trim(), url.trim())
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Shows the address and code for adding addon links from a computer's browser, and keeps the
 * little web page running only while this dialog is open.
 */
@Composable
private fun AddFromComputerDialog(
    onLinks: suspend (List<String>, List<com.ominix.vidiio.data.repository.LiveLink>, List<String>) -> Unit,
    onDismiss: () -> Unit,
    onMenuClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val server = remember { com.ominix.vidiio.data.remote.AddonLinkServer(com.ominix.vidiio.data.remote.AddonLinkServer.newPin()) }
    val address = remember { server.address() }
    var added by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(server) {
        server.serve(onLinks = { addons, live, liveAddonUrls ->
            onLinks(addons, live, liveAddonUrls)
            val count = addons.size + live.size + liveAddonUrls.size
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                added += count
                android.widget.Toast.makeText(context, "Added $count link${if (count == 1) "" else "s"}", android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = onMenuClose),
        title = { Text("Add from your computer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (address == null) {
                    Text("This device isn't connected to a local network.")
                } else {
                    Text("On a computer on the same Wi-Fi, open:")
                    Text(address, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Then paste your addon or Live TV links and enter this code:")
                    Text(server.pin, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                    Text(
                        if (added == 0) "Keep this open while you add them." else "$added added so far.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvDialogFocus()) { Text("Done") }
        }
    )
}

@Composable
fun ExpandableSettingsSection(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val contentFocusRequester = remember { FocusRequester() }
    val headerFocusRequester = remember { FocusRequester() }

    // Back closes the open section and puts focus back on its header, instead of leaving Settings.
    androidx.activity.compose.BackHandler(enabled = expanded) {
        onExpandedChange(false)
        runCatching { headerFocusRequester.requestFocus() }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(30)
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(headerFocusRequester)
                .tvClickable(onClick = { onExpandedChange(!expanded) }, shape = shape, focusScale = 1.02f),
            shape = shape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!summary.isNullOrBlank()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // No expand/collapse animation: re-measuring a tall section every frame is what made opening a
                // menu stutter on a TV stick. It simply appears.
                if (expanded) {
                    Column(modifier = Modifier.focusRequester(contentFocusRequester)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        content()
                    }
                }
            }
        }
    }
}

/** What is in the Proxy dialog, hoisted so Menu closing it for the rail keeps the typed values. */
private data class ProxyDraft(
    val type: com.ominix.vidiio.data.repository.ProxyType,
    val host: String,
    val port: String,
    val user: String,
    val pass: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxySettingsDialog(
    draft: ProxyDraft,
    onDraftChange: (ProxyDraft) -> Unit,
    testResult: String?,
    onTest: (ProxyDraft) -> Unit,
    onSave: (ProxyDraft) -> Unit,
    onDismiss: () -> Unit,
    onMenuClose: () -> Unit
) {
    val type = draft.type
    val host = draft.host
    val port = draft.port

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Gray,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = Color.Gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Proxy (VPN)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ominix.vidiio.data.repository.ProxyType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { onDraftChange(draft.copy(type = t)) },
                            label = { Text(if (t == com.ominix.vidiio.data.repository.ProxyType.SOCKS5) "SOCKS5" else "HTTP") }
                        )
                    }
                }
                OutlinedTextField(host, { onDraftChange(draft.copy(host = it)) }, label = { Text("Host") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvDialogFocus(opensKeyboard = true).tvDialogMenuToRail(onMenuClose), colors = fieldColors)
                OutlinedTextField(port, { onDraftChange(draft.copy(port = it.filter(Char::isDigit).take(5))) }, label = { Text("Port") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().tvDialogMenuToRail(onMenuClose), colors = fieldColors)
                OutlinedTextField(draft.user, { onDraftChange(draft.copy(user = it)) }, label = { Text("Username (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvDialogMenuToRail(onMenuClose), colors = fieldColors)
                OutlinedTextField(draft.pass, { onDraftChange(draft.copy(pass = it)) }, label = { Text("Password (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvDialogMenuToRail(onMenuClose), colors = fieldColors)
                TextButton(
                    onClick = { onTest(draft) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Test connection") }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Restart the app after saving for the change to take effect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Save & enable") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStyleSelectionRow(
    current: HomeStyle,
    onSelected: (HomeStyle) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeStyle.entries.forEach { style ->
            FilterChip(
                selected = current == style,
                onClick = { onSelected(style) },
                label = { Text(style.spec(accent).label) }
            )
        }
    }
}

@Composable
fun ColorThemeSelectionRow(
    currentColorTheme: ColorTheme,
    onColorThemeSelected: (ColorTheme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorTheme.entries.forEach { theme ->
            val isSelected = currentColorTheme == theme
            val color = when (theme) {
                ColorTheme.RED -> VidiioRed
                ColorTheme.BLUE -> VidiioBlue
                ColorTheme.GREEN -> VidiioGreen
                ColorTheme.PURPLE -> VidiioPurple
                ColorTheme.ORANGE -> VidiioOrange
                ColorTheme.TEAL -> VidiioTeal
                ColorTheme.PINK -> VidiioPink
                ColorTheme.INDIGO -> VidiioIndigo
                ColorTheme.GOLD -> VidiioGold
                ColorTheme.MONO -> VidiioMono
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, RoundedCornerShape(10.dp))
                    .tvClickable(onClick = { onColorThemeSelected(theme) }, shape = RoundedCornerShape(10.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenceItem(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        },
        modifier = if (onClick != null) Modifier.tvClickable(onClick = onClick, shape = RoundedCornerShape(12.dp), focusScale = 1.02f) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )
        },
        modifier = Modifier.tvClickable(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(12.dp), focusScale = 1.02f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}



@Composable
fun MediaPlayerSelectionRow(
    currentChoice: MediaPlayerChoice,
    isVlcInstalled: Boolean,
    onChoiceSelected: (MediaPlayerChoice) -> Unit,
    onInstallVlc: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                MediaPlayerChoice.INTERNAL to "Internal (ExoPlayer)",
                MediaPlayerChoice.VLC to "VLC Player"
            )
            options.forEach { (choice, label) ->
                val isSelected = currentChoice == choice
                val shape = RoundedCornerShape(24.dp)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .tvClickable(onClick = { onChoiceSelected(choice) }, shape = shape, focusScale = 1.05f),
                    shape = shape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        if (currentChoice == MediaPlayerChoice.VLC && !isVlcInstalled) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VLC app is not installed on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onInstallVlc) {
                        Text("Install VLC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QualitySelectionRow(
    currentQuality: String,
    onQualitySelected: (String) -> Unit
) {
    val qualities = listOf("Auto", "1080p", "720p", "480p")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        qualities.forEach { quality ->
            val isSelected = currentQuality == quality
            val shape = RoundedCornerShape(24.dp)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .tvClickable(onClick = { onQualitySelected(quality) }, shape = shape, focusScale = 1.05f),
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = if (isSelected) null else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = quality,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SourceToggleItem(
    name: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            )
        },
        modifier = Modifier.tvClickable(onClick = onToggle, shape = RoundedCornerShape(12.dp), focusScale = 1.02f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * Subtitle appearance controls, with a live preview.
 */
@Composable
private fun SubtitleAppearanceSection(
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Subtitle appearance",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "The quick brown fox",
                color = Color(style.textColor),
                fontSize = (15 * style.textScale).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color(style.backgroundColor), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        val textSizeOptions = listOf(
            0.75f to "Small (75%)",
            1.00f to "Normal (100%)",
            1.25f to "Large (125%)",
            1.50f to "Extra Large (150%)",
            2.00f to "Huge (200%)"
        )
        var expandedTextSize by remember { mutableStateOf(false) }
        val currentOptionLabel = textSizeOptions.find { (scale, _) ->
            kotlin.math.abs(scale - style.textScale) < 0.08f
        }?.second ?: "${(style.textScale * 100).toInt()}%"

        Column {
            Text(
                "Text size",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvClickable(
                            onClick = { expandedTextSize = !expandedTextSize },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentOptionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (expandedTextSize) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Text size dropdown",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedTextSize,
                    onDismissRequest = { expandedTextSize = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    textSizeOptions.forEach { (scale, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (kotlin.math.abs(scale - style.textScale) < 0.08f) FontWeight.Bold else FontWeight.Normal,
                                    color = if (kotlin.math.abs(scale - style.textScale) < 0.08f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onStyleChange(style.copy(textScale = scale))
                                expandedTextSize = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Text colour", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SUBTITLE_TEXT_COLORS.forEach { argb ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(argb), RoundedCornerShape(8.dp))
                        .tvClickable(onClick = { onStyleChange(style.copy(textColor = argb)) }, shape = RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (style.textColor == argb) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = if (argb == 0xFFFFFFFFL) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Background", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUBTITLE_BACKGROUNDS.forEach { entry ->
                FilterChip(
                    selected = style.backgroundColor == entry.second,
                    onClick = { onStyleChange(style.copy(backgroundColor = entry.second)) },
                    label = { Text(entry.first, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Edge style", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            SubtitleEdge.entries.forEach { edge ->
                FilterChip(
                    selected = style.edge == edge,
                    onClick = { onStyleChange(style.copy(edge = edge)) },
                    label = { Text(subtitleEdgeLabel(edge), fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { onStyleChange(SubtitleStyle()) }) { Text("Reset to defaults") }
    }
}

private fun subtitleEdgeLabel(edge: SubtitleEdge): String =
    edge.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private val SUBTITLE_TEXT_COLORS = listOf(
    0xFFFFFFFFL, 0xFFFFEB3BL, 0xFF00E5FFL, 0xFF69F0AEL, 0xFFFF8A80L, 0xFF000000L
)

private val SUBTITLE_BACKGROUNDS = listOf(
    "None" to 0x00000000L,
    "Dim" to 0x80000000L,
    "Solid" to 0xFF000000L
)

/** A short, safe name for an addon link: its site. The path often holds the user's settings or a private token. */
private fun addonLabel(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url.take(40)

/**
 * One installed addon: a single focus stop for the remote (OK asks to remove it), showing the site only -
 * the full link can run to hundreds of characters and may contain a private token.
 */
@Composable
private fun AddonRow(addonUrl: String, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .tvClickable(onClick = onRemove, shape = shape, focusScale = 1.02f)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(addonLabel(addonUrl), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Press OK to remove", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonStoreSheet(
    entries: List<com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry>,
    isLoading: Boolean,
    installedAddons: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onDismiss: () -> Unit,
    onMenuClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var webPromptUrl by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val returnFocus = rememberDialogReturnFocus()

    val categories = listOf("All", "Movies & Series", "Sports & Live", "Anime", "Subtitles", "Scrapers")

    val filteredEntries = remember(entries, searchQuery, selectedCategory) {
        entries.filter { entry ->
            val manifest = entry.manifest
            val types = manifest.types.orEmpty().map { it.lowercase() }
            val nameMatches = manifest.name.contains(searchQuery, ignoreCase = true) ||
                    (manifest.description?.contains(searchQuery, ignoreCase = true) == true)

            val categoryMatches = when (selectedCategory) {
                "Movies & Series" -> types.any { it == "movie" || it == "series" || it == "tv" }
                "Sports & Live" -> types.any { it == "sport" || it == "sports" || it == "live" || it == "events" }
                "Anime" -> types.any { it == "anime" }
                "Subtitles" -> manifest.resources.orEmpty().any { r -> r.toString().contains("subtitles") }
                "Scrapers" -> manifest.resources.orEmpty().any { r -> r.toString().contains("stream") }
                else -> true
            }
            nameMatches && categoryMatches
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            // Menu opens the rail from any control in the store, not only the search field.
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .tvDialogMenuToRail(fromAnyControl = true, onCloseDialog = onMenuClose),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Addon Store", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("stremio-addons.net", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.tvDialogFocus()) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search addons...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).tvDialogMenuToRail(onMenuClose),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading directory...", color = Color.Gray)
                        }
                    }
                } else if (filteredEntries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No addons found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredEntries, key = { it.transportUrl ?: it.manifest.id }) { entry ->
                            AddonStoreCard(
                                entry = entry,
                                isInstalled = installedAddons.any { it.contains(entry.manifest.id, ignoreCase = true) || (entry.transportUrl != null && it == entry.transportUrl) },
                                onInstall = { url -> onInstall(url) },
                                onUninstall = { url -> onUninstall(url) },
                                onPromptWeb = { name, webUrl -> webPromptUrl = name to webUrl }
                            )
                        }
                    }
                }
            }
        }
    }

    webPromptUrl?.let { (name, webUrl) ->
        AlertDialog(
            onDismissRequest = { webPromptUrl = null; returnFocus() },
            title = { Text("Configure $name") },
            text = {
                Text("This addon offers custom options on its website. Would you like to open $webUrl in your browser or install directly?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl))
                        context.startActivity(intent)
                        webPromptUrl = null
                        returnFocus()
                    },
                    modifier = Modifier.tvDialogFocus()
                ) { Text("Open Website") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        webPromptUrl = null
                        returnFocus()
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddonStoreCard(
    entry: com.ominix.vidiio.data.model.stremio.StremioDirectoryEntry,
    isInstalled: Boolean,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onPromptWeb: (String, String) -> Unit
) {
    val manifest = entry.manifest
    val transportUrl = entry.transportUrl ?: "https://${manifest.id}.strem.fun/manifest.json"
    val logoUrl = manifest.logo

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!logoUrl.isNullOrBlank()) {
                        coil.compose.AsyncImage(model = logoUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(manifest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    manifest.version?.let { v ->
                        Text("v$v", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (isInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF2E7D32).copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Installed", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = { onUninstall(transportUrl) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Remove", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = { onInstall(transportUrl) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Install")
                    }
                }
            }

            if (!manifest.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val webConfigUrl = remember(transportUrl) {
                if (transportUrl.contains("/manifest.json")) {
                    val base = transportUrl.removeSuffix("/manifest.json") + "/configure"
                    base
                } else null
            }
            if (webConfigUrl != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.tvClickable(onClick = { onPromptWeb(manifest.name, webConfigUrl) }, shape = RoundedCornerShape(6.dp)).padding(4.dp)
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configure on website", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

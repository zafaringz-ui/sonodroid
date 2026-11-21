package dev.sonora.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.secure.CredentialStore
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.Provider
import dev.sonora.core.model.ProviderAuth
import dev.sonora.core.model.QualityPreference
import dev.sonora.design.component.DemoDataBadge
import dev.sonora.design.theme.SonoraAccent

@Composable
fun SettingsRoute(
    core: SonoraCoreClient,
    credentials: CredentialStore,
    onOpenQuality: (Provider) -> Unit,
    onSignIn: (Provider) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(core, credentials))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbar,
        onOpenQuality = onOpenQuality,
        onSignIn = onSignIn,
        onOpenDownloads = onOpenDownloads,
        onSignOut = viewModel::signOut,
        onQualityPreference = viewModel::setQualityPreference,
        onCrossfade = viewModel::setCrossfade,
        onGapless = viewModel::setGapless,
        onNormalize = viewModel::setNormalizeLoudness,
        onNetwork = viewModel::setNetwork,
        onAccent = viewModel::setAccent,
        onDynamicColor = viewModel::setDynamicColor,
        resolvedQualityLabel = { provider ->
            viewModel.resolvedQuality(provider)?.label ?: "Offline"
        },
    )
}

/** Screens 23–35: the settings hub, with quality pages on their own route. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenQuality: (Provider) -> Unit,
    onSignIn: (Provider) -> Unit,
    onOpenDownloads: () -> Unit,
    onSignOut: (Provider) -> Unit,
    onQualityPreference: (Provider, QualityPreference) -> Unit,
    onCrossfade: (Int) -> Unit,
    onGapless: (Boolean) -> Unit,
    onNormalize: (Boolean) -> Unit,
    onNetwork: (NetworkKind) -> Unit,
    onAccent: (SonoraAccent) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    resolvedQualityLabel: (Provider) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item(key = "title") {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isDemo) DemoDataBadge()
                }
                Text(
                    text = "Version ${state.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "accounts-header") {
            SectionTitle("Accounts", Icons.Rounded.Person)
        }
        items(state.auth, key = { "auth-${it.provider.slug}" }) { account ->
            AccountRow(
                auth = account,
                onSignIn = { onSignIn(account.provider) },
                onSignOut = { onSignOut(account.provider) },
                publicMode = state.isPublicMode,
            )
        }

        item(key = "quality-header") { SectionTitle("Audio quality", Icons.Rounded.GraphicEq) }
        item(key = "quality-rows") {
            Column {
                Provider.entries.forEach { provider ->
                    NavigationRow(
                        title = provider.label,
                        subtitle = "Now: ${resolvedQualityLabel(provider)}",
                        onClick = { onOpenQuality(provider) },
                    )
                }
            }
        }

        item(key = "playback-header") { SectionTitle("Playback", Icons.Rounded.GraphicEq) }
        item(key = "playback-controls") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ToggleRow(
                    title = "Gapless playback",
                    subtitle = "Join tracks with no silence between them",
                    checked = state.gapless,
                    onCheckedChange = onGapless,
                )
                ToggleRow(
                    title = "Normalise loudness",
                    subtitle = "Target -14 LUFS, matching the desktop engine",
                    checked = state.normalizeLoudness,
                    onCheckedChange = onNormalize,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Crossfade: ${state.crossfadeSeconds}s",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = state.crossfadeSeconds.toFloat(),
                    onValueChange = { onCrossfade(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                )
            }
        }

        item(key = "network-header") { SectionTitle("Network", Icons.Rounded.WifiOff) }
        item(key = "network") {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NetworkKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.network == kind,
                        onClick = { onNetwork(kind) },
                        label = {
                            Text(
                                when (kind) {
                                    NetworkKind.WIFI -> "Wi-Fi"
                                    NetworkKind.CELLULAR -> "Mobile"
                                    NetworkKind.OFFLINE -> "Offline"
                                },
                            )
                        },
                    )
                }
            }
        }

        item(key = "appearance-header") { SectionTitle("Appearance", Icons.Rounded.Palette) }
        item(key = "accent") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SonoraAccent.entries.forEach { accent ->
                        AssistChip(
                            onClick = { onAccent(accent) },
                            label = { Text(accent.label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                ToggleRow(
                    title = "Material You",
                    subtitle = "Use the wallpaper palette on Android 12+",
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
        }

        item(key = "storage-header") { SectionTitle("Storage", Icons.Rounded.Download) }
        item(key = "storage") {
            Column {
                NavigationRow(
                    title = "Downloads",
                    subtitle = "${state.downloads.size} items · encrypted at rest",
                    onClick = onOpenDownloads,
                )
            }
        }

        item(key = "about-header") { SectionTitle("About", Icons.Rounded.Info) }
        item(key = "about") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    text = "Sonora for Android is a GPL-3.0 client for Deezer and YouTube Music. " +
                        "It is not affiliated with either service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (state.isDemo) {
                        "Running on demo data. Build the Rust core and enable -Psonora.rust=true for the native bridge."
                    } else {
                        "Native Rust core connected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "snackbar") { SnackbarHost(hostState = snackbarHostState) }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AccountRow(
    auth: ProviderAuth,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    publicMode: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(auth.provider.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when (auth.state) {
                    AuthState.SIGNED_IN ->
                        "${auth.account?.displayName ?: "Signed in"} · ${auth.account?.plan?.label ?: "Free"}"
                    AuthState.CONNECTING -> "Connecting…"
                    AuthState.NEEDS_ATTENTION -> auth.detail ?: "Needs attention"
                    AuthState.SIGNED_OUT -> when {
                        publicMode && auth.provider == Provider.DEEZER -> "Public previews · sign in for full tracks"
                        publicMode -> "Not available in this public build"
                        else -> "Not signed in"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!publicMode || auth.provider == Provider.DEEZER) {
            TextButton(onClick = if (auth.state == AuthState.SIGNED_IN) onSignOut else onSignIn) {
                Text(if (auth.state == AuthState.SIGNED_IN) "Sign out" else "Sign in")
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

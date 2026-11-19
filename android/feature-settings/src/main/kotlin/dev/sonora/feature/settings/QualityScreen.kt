package dev.sonora.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Provider
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.StreamQuality
import dev.sonora.design.theme.SonoraTheme

@Composable
fun QualityRoute(
    core: SonoraCoreClient,
    providerSlug: String,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(core))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val provider = Provider.fromSlug(providerSlug)

    QualityScreen(
        provider = provider,
        ladder = core.qualityLadder(provider),
        preference = state.preferences[provider] ?: QualityPreference.AUTO,
        resolved = viewModel.resolvedQuality(provider),
        downloadQuality = viewModel.downloadQuality(provider),
        planLabel = state.auth.firstOrNull { it.provider == provider }?.account?.plan?.label
            ?: "Free",
        networkLabel = when (state.network) {
            dev.sonora.core.model.NetworkKind.WIFI -> "Wi-Fi"
            dev.sonora.core.model.NetworkKind.CELLULAR -> "Mobile data"
            dev.sonora.core.model.NetworkKind.OFFLINE -> "Offline"
        },
        onBack = onBack,
        onPreference = { preference -> viewModel.setQualityPreference(provider, preference) },
    )
}

/** Screen 25: per-provider audio quality, with the resolved result spelled out. */
@Composable
fun QualityScreen(
    provider: Provider,
    ladder: List<StreamQuality>,
    preference: QualityPreference,
    resolved: StreamQuality?,
    downloadQuality: StreamQuality?,
    planLabel: String,
    networkLabel: String,
    onBack: () -> Unit,
    onPreference: (QualityPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "${provider.label} quality",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Plan: $planLabel · Network: $networkLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Streaming now at",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = resolved?.label ?: "Paused while offline",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.waveform,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Downloads at",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = downloadQuality?.label ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text("Preference", style = MaterialTheme.typography.titleSmall)
            }
        }

        item(key = "preferences") {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QualityPreference.entries.forEach { option ->
                    FilterChip(
                        selected = option == preference,
                        onClick = { onPreference(option) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        item(key = "ladder-header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Available on this plan", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Auto keeps lossless on Wi-Fi and drops to 320 kbps on mobile data. " +
                        "Data saver always picks the lowest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(ladder, key = { it.label }) { quality ->
            val isCurrent = resolved != null && quality.tier == resolved.tier
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isCurrent, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(quality.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = buildString {
                            append(quality.codec.uppercase())
                            append(" · ")
                            append(quality.bitrateKbps)
                            append(" kbps")
                            if (quality.sampleRateHz > 0) {
                                append(" · ")
                                append(quality.sampleRateHz / 1000)
                                append(" kHz")
                            }
                            if (quality.bitDepth > 0) {
                                append(" · ")
                                append(quality.bitDepth)
                                append("-bit")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "In use",
                        tint = colors.waveform,
                    )
                }
            }
        }
    }
}

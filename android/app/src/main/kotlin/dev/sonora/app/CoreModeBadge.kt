package dev.sonora.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Debug-only badge naming the active core, e.g. "Demo core" or "Rust core (live)".
 *
 * The point is that a fallback can never be mistaken for the native core: the label
 * is always visible and, when the app fell back, names the reason. Kept out of
 * release builds entirely — [CoreModeBadgeHost] is the only caller and it is gated
 * on `BuildConfig.DEBUG`.
 */
@Composable
fun CoreModeBadge(
    selection: CoreSelection,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (selection.mode) {
        CoreMode.RUST_LIVE -> colors.tertiaryContainer to colors.onTertiaryContainer
        CoreMode.RUST_FIXTURES -> colors.secondaryContainer to colors.onSecondaryContainer
        CoreMode.DEMO -> colors.errorContainer to colors.onErrorContainer
    }

    val text = selection.reason?.let { reason -> "${selection.label} — ${reason.name.lowercase().replace('_', ' ')}" }
        ?: selection.label

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = "Active core: $text. ${selection.detail.orEmpty()}" },
    )
}

/**
 * Renders [CoreModeBadge] in debug builds and nothing at all otherwise, so release
 * configuration cannot accidentally ship the label.
 */
@Composable
fun CoreModeBadgeHost(
    selection: CoreSelection,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG) return
    CoreModeBadge(selection = selection, modifier = modifier)
}

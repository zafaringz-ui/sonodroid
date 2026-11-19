package dev.sonora.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Cover art with a deterministic gradient fallback.
 *
 * The fallback matters: demo data and offline downloads often have no URL, and a
 * flat grey rectangle reads as a bug. Colours are derived from the title so the same
 * album always gets the same tile.
 */
@Composable
fun Artwork(
    title: String,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    cornerRadius: Dp = 12.dp,
) {
    val gradient = rememberArtworkGradient(title)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl.isNullOrBlank()) {
            Text(
                text = initialsOf(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
            )
        } else {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private val ArtworkPalette = listOf(
    listOf(Color(0xFF1F4A4F), Color(0xFF0E2230)),
    listOf(Color(0xFF4A2C2A), Color(0xFF231110)),
    listOf(Color(0xFF2C3A55), Color(0xFF111827)),
    listOf(Color(0xFF3B2F4E), Color(0xFF171122)),
    listOf(Color(0xFF2F4A32), Color(0xFF101E12)),
    listOf(Color(0xFF4A3F22), Color(0xFF1F1A0C)),
)

@Composable
private fun rememberArtworkGradient(seed: String): List<Color> = remember(seed) {
    ArtworkPalette[(seed.hashCode() and 0x7FFF_FFFF) % ArtworkPalette.size]
}

private fun initialsOf(title: String): String = title
    .split(' ', '-', '(', ')', '/')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "♪" }

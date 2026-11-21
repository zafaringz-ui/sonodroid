package dev.sonora.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Sonora's colour system.
 *
 * The desktop app ships 18 JSON themes; the Android port maps a small set of accent
 * palettes onto Material 3 schemes and adds Material You dynamic colour on Android
 * 12+. Importing all 18 JSON themes is Phase 5 work — the plumbing is already here
 * (`SonoraAccent`), so adding them is data, not architecture.
 */

enum class SonoraAccent(val label: String) {
    TIDE("Tide"),
    AMBER("Amber"),
    IRIS("Iris"),
    MOSS("Moss"),
}

@Immutable
data class SonoraColors(
    val nowPlayingTop: Color,
    val nowPlayingBottom: Color,
    val waveform: Color,
    val spectrumLow: Color,
    val spectrumHigh: Color,
    val surfaceRaised: Color,
    val isDark: Boolean,
)

private val LocalSonoraColors = staticCompositionLocalOf {
    SonoraColors(
        nowPlayingTop = Color(0xFF16323A),
        nowPlayingBottom = Color(0xFF090C10),
        waveform = Color(0xFF7FD1C1),
        spectrumLow = Color(0xFF7FD1C1),
        spectrumHigh = Color(0xFFF0B37E),
        surfaceRaised = Color(0xFF141C26),
        isDark = true,
    )
}

object SonoraTheme {
    val colors: SonoraColors
        @Composable get() = LocalSonoraColors.current
}

private fun darkScheme(accent: SonoraAccent) = darkColorScheme(
    primary = when (accent) {
        SonoraAccent.TIDE -> Color(0xFF7FD1C1)
        SonoraAccent.AMBER -> Color(0xFFF0B37E)
        SonoraAccent.IRIS -> Color(0xFFB9A8FF)
        SonoraAccent.MOSS -> Color(0xFFA8D08D)
    },
    onPrimary = Color(0xFF04231D),
    primaryContainer = Color(0xFF15403A),
    onPrimaryContainer = Color(0xFFC7F3E8),
    secondary = Color(0xFF9BB8FF),
    onSecondary = Color(0xFF0A1A33),
    secondaryContainer = Color(0xFF1B2A44),
    onSecondaryContainer = Color(0xFFD6E2FF),
    tertiary = Color(0xFFF0B37E),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE7EDF3),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFE7EDF3),
    surfaceVariant = Color(0xFF1B2530),
    onSurfaceVariant = Color(0xFFA7B4C1),
    surfaceContainer = Color(0xFF121A23),
    outline = Color(0xFF3A4756),
    outlineVariant = Color(0xFF232E3A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0705),
)

private fun lightScheme(accent: SonoraAccent) = lightColorScheme(
    primary = when (accent) {
        SonoraAccent.TIDE -> Color(0xFF0F766E)
        SonoraAccent.AMBER -> Color(0xFF9A5B12)
        SonoraAccent.IRIS -> Color(0xFF4F3EB8)
        SonoraAccent.MOSS -> Color(0xFF3F6B2B)
    },
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE8),
    onPrimaryContainer = Color(0xFF04231D),
    secondary = Color(0xFF2F5DA8),
    onSecondary = Color.White,
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF121A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121A21),
    surfaceVariant = Color(0xFFE4EBF1),
    onSurfaceVariant = Color(0xFF4A5A68),
    outline = Color(0xFF9AA9B7),
    error = Color(0xFFB3261E),
)

@Composable
fun SonoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: SonoraAccent = SonoraAccent.TIDE,
    dynamicColor: Boolean = true,
    font: SonoraFont = SonoraFont.SANS,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> darkScheme(accent)
        else -> lightScheme(accent)
    }

    val extended = SonoraColors(
        nowPlayingTop = if (darkTheme) Color(0xFF14303A) else Color(0xFFDCEDF0),
        nowPlayingBottom = if (darkTheme) Color(0xFF080B0F) else Color(0xFFF2F6F8),
        waveform = colorScheme.primary,
        spectrumLow = colorScheme.primary,
        spectrumHigh = colorScheme.tertiary,
        surfaceRaised = if (darkTheme) Color(0xFF141C26) else Color(0xFFFFFFFF),
        isDark = darkTheme,
    )

    CompositionLocalProvider(LocalSonoraColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = sonoraTypography(SonoraUiPreferences.fontFamily(font)),
            content = content,
        )
    }
}

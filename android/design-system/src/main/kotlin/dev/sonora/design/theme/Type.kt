package dev.sonora.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 type scale tuned for a music app: slightly tighter display sizes and
 * heavier titles so long track names stay readable at a glance.
 *
 * Inter (assets/fonts in the desktop app) is not bundled yet; switching the family
 * here is the only change needed once the TTF files land in `res/font`.
 */
val SonoraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

fun sonoraTypography(fontFamily: FontFamily): Typography = SonoraTypography.copy(
    displayLarge = SonoraTypography.displayLarge.copy(fontFamily = fontFamily),
    displayMedium = SonoraTypography.displayMedium.copy(fontFamily = fontFamily),
    displaySmall = SonoraTypography.displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = SonoraTypography.headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = SonoraTypography.headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = SonoraTypography.headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = SonoraTypography.titleLarge.copy(fontFamily = fontFamily),
    titleMedium = SonoraTypography.titleMedium.copy(fontFamily = fontFamily),
    titleSmall = SonoraTypography.titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = SonoraTypography.bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = SonoraTypography.bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = SonoraTypography.bodySmall.copy(fontFamily = fontFamily),
    labelLarge = SonoraTypography.labelLarge.copy(fontFamily = fontFamily),
    labelMedium = SonoraTypography.labelMedium.copy(fontFamily = fontFamily),
    labelSmall = SonoraTypography.labelSmall.copy(fontFamily = fontFamily),
)

/** Shared spacing and motion tokens. */
object SonoraDimens {
    const val ScreenPadding = 20
    const val Gutter = 16
    const val CardCorner = 16
    const val RowHeight = 64
    const val MiniPlayerHeight = 64
}

object SonoraMotion {
    const val FastMs = 120
    const val StandardMs = 220
    const val SlowMs = 380
}

package dev.sonora.design.util

import java.util.Locale

/** `3:07`, or `1:02:11` for long items. Negative input is treated as zero. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** `41.8 MB` style sizes for the downloads screen. */
fun formatBytes(bytes: Long): String {
    val kb = bytes / 1_024.0
    val mb = kb / 1_024.0
    val gb = mb / 1_024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

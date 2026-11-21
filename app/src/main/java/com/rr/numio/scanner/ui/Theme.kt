package com.rr.numio.scanner.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

/** Folded Paper colours, from the official Numio palette. */
data class Paper(
    val bgTop: Color,
    val bgFold: Color,
    val card: Color,
    val fold: Color,
    val ink: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val onAccent: Color,
    val bar: Color,
    val barInk: Color,
    val dark: Boolean,
)

val LightPaper = Paper(
    bgTop = Color(0xFFF6F4EE), bgFold = Color(0xFFEEEAE0), card = Color(0xFFFFFFFF), fold = Color(0xFFE4DED0),
    ink = Color(0xFF191815), sub = Color(0xFF5E5A50), line = Color(0xFFD8D2C3),
    accent = Color(0xFFE9A800), accentInk = Color(0xFF7A5600), onAccent = Color(0xFF1A1400),
    bar = Color(0xFF191815), barInk = Color(0xFFF1EEE6), dark = false,
)

val DarkPaper = Paper(
    bgTop = Color(0xFF181715), bgFold = Color(0xFF0F0F0E), card = Color(0xFF23211D), fold = Color(0xFF3A362F),
    ink = Color(0xFFF1EEE6), sub = Color(0xFFABA597), line = Color(0xFF3A362F),
    accent = Color(0xFFFFC21A), accentInk = Color(0xFFFFC21A), onAccent = Color(0xFF1A1400),
    bar = Color(0xFF23211D), barInk = Color(0xFFF1EEE6), dark = true,
)

/** Amber used on the always-dark camera screen. */
val NumioAmber = Color(0xFFFFC21A)
val Coal = Color(0xFF0F0F0E)
val Cream = Color(0xFFF1EEE6)

val Display = FontFamily.Serif

val LocalPaper = staticCompositionLocalOf { LightPaper }

@Composable
fun PaperTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPaper else LightPaper
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bgFold, onBackground = p.ink,
            surface = p.card, onSurface = p.ink, surfaceContainerHigh = p.card,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bgTop, onBackground = p.ink,
            surface = p.card, onSurface = p.ink, surfaceContainerHigh = p.card,
        )
    }
    CompositionLocalProvider(LocalPaper provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Dark status-bar icons on light screens, light icons on dark ones. */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !lightIcons
            c.isAppearanceLightNavigationBars = !lightIcons
        }
        onDispose { }
    }
}

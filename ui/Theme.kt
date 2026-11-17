package com.mediaplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D7BFF), onPrimary = Color(0xFF1A0B4D),
    secondary = Color(0xFFFFB84D),
    background = Color(0xFF0C0B16), onBackground = Color(0xFFEDEBFF),
    surface = Color(0xFF15142A), onSurface = Color(0xFFEDEBFF),
    surfaceVariant = Color(0xFF211F3D), onSurfaceVariant = Color(0xFFB9B5DA),
    primaryContainer = Color(0xFF3A2D8A), onPrimaryContainer = Color(0xFFE6DEFF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B3FE0), onPrimary = Color.White,
    secondary = Color(0xFFE08A00),
    background = Color(0xFFF7F5FF), onBackground = Color(0xFF14122B),
    surface = Color.White, onSurface = Color(0xFF14122B),
    surfaceVariant = Color(0xFFEAE6FF), onSurfaceVariant = Color(0xFF55507A),
    primaryContainer = Color(0xFFE4DAFF), onPrimaryContainer = Color(0xFF22106B)
)

/** mode: 0 = النظام، 1 = فاتح، 2 = داكن */
@Composable
fun MediaPlayerTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    }
}

/** شعار التطبيق مرسوم برمجيًا (نفس تصميم أيقونة التطبيق) */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B1FA8)), Offset(0f, 0f), Offset(w, w)),
            cornerRadius = CornerRadius(w * 0.26f)
        )
        val tri = Path().apply {
            moveTo(w * 0.40f, w * 0.30f); lineTo(w * 0.72f, w * 0.50f); lineTo(w * 0.40f, w * 0.70f); close()
        }
        drawPath(tri, Brush.linearGradient(listOf(Color(0xFFFFD27A), Color(0xFFFFA928)), Offset(w * 0.4f, w * 0.3f), Offset(w * 0.72f, w * 0.7f)))
        listOf(0.12f to 1f, 0.20f to 0.85f).forEach { (r, a) ->
            drawArc(
                Color.White.copy(alpha = a), startAngle = 135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(w * (0.40f - r), w * (0.5f - r)), size = Size(w * r * 2, w * r * 2),
                style = Stroke(width = w * 0.035f, cap = StrokeCap.Round)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

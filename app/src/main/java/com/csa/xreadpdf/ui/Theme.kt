package com.csa.xreadpdf.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.csa.xreadpdf.R

/** Palette de l'icône « Fusée Partage ». */
object Brand {
    val Night = Color(0xFF2A1E3D)
    val NightSoft = Color(0xFF3A2C52)
    val Coral = Color(0xFFFF5A4E)
    val Sun = Color(0xFFFFC93C)
    val Sky = Color(0xFF4C8DFF)
    val Cream = Color(0xFFFFF6EE)
    val Ink = Color(0xFF1A1A1F)
}

private val Light = lightColorScheme(
    primary = Color(0xFFC7362C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Brand.Night,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF22163A),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Brand.Sun,
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3E6DF),
    onSurfaceVariant = Color(0xFF52443F),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFBEBE2),
    surfaceContainerHigh = Color(0xFFF5E5DC),
    surfaceContainerHighest = Color(0xFFEFDFD6),
    inverseSurface = Brand.Night,
    inverseOnSurface = Brand.Cream,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A7F),
    onPrimary = Color(0xFF5F1410),
    primaryContainer = Color(0xFF8C2A22),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFCDBDFF),
    onSecondary = Color(0xFF33275A),
    secondaryContainer = Color(0xFF3B2D57),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Brand.Sun,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF15101D),
    onBackground = Color(0xFFECE0E8),
    surface = Color(0xFF15101D),
    onSurface = Color(0xFFECE0E8),
    surfaceVariant = Color(0xFF3A3042),
    onSurfaceVariant = Color(0xFFCEC2D4),
    outline = Color(0xFF978C9D),
    outlineVariant = Color(0xFF4B4153),
    surfaceContainerLowest = Color(0xFF100B17),
    surfaceContainerLow = Color(0xFF1D1726),
    surfaceContainer = Color(0xFF221B2B),
    surfaceContainerHigh = Color(0xFF2C2436),
    surfaceContainerHighest = Color(0xFF372F41),
    inverseSurface = Brand.Cream,
    inverseOnSurface = Brand.Night,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun XreadPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Icônes de barre d'état claires (sur un en-tête sombre) ou selon le thème. */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    if (view.isInEditMode) return
    DisposableEffect(lightIcons, dark) {
        view.context.findActivity()?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !lightIcons && !dark
            c.isAppearanceLightNavigationBars = !dark
        }
        onDispose { }
    }
}

/** Le logo de l'app (la fusée) dans un carré arrondi violet. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brand.NightSoft),
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

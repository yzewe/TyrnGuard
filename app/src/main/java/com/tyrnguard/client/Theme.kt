package com.tyrnguard.client

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══ Типография ═══
val WDTTTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ═══ Дефолт: Глубокий Синий (Indigo / Blue) ═══
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176B74),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEEBF0),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF586249),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8C7),
    onSecondaryContainer = Color(0xFF161F0B),
    tertiary = Color(0xFF75546F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F3),
    onTertiaryContainer = Color(0xFF2C122A),
    background = Color(0xFFF6FBF8),
    surface = Color(0xFFF6FBF8),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F7F2),
    surfaceContainer = Color(0xFFEAF2EC),
    surfaceContainerHigh = Color(0xFFE4ECE6),
    surfaceContainerHighest = Color(0xFFDDE7E1),
    outlineVariant = Color(0xFFC0CAC3),
    error = Color(0xFFBA1A1A),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82D5DC),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF0B4F55),
    onPrimaryContainer = Color(0xFFBEEBF0),
    secondary = Color(0xFFC0CCAD),
    onSecondary = Color(0xFF2B341E),
    secondaryContainer = Color(0xFF414A33),
    onSecondaryContainer = Color(0xFFDCE8C7),
    tertiary = Color(0xFFE4BBD8),
    onTertiary = Color(0xFF43263F),
    tertiaryContainer = Color(0xFF5B3C56),
    onTertiaryContainer = Color(0xFFFFD7F3),
    background = Color(0xFF0E1512),
    surface = Color(0xFF0E1512),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0CAC3),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF141B18),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF222A26),
    surfaceContainerHighest = Color(0xFF2D3531),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
)

// ═══ Кастомные цвета ═══
object WDTTColors {
    val connected = Color(0xFF4CAF50)
    val warning = Color(0xFFFFA726)
    val terminalBg = Color(0xFF1E1E2E)
    val terminalBgDark = Color(0xFF000000)
    val terminalText = Color(0xFFCDD6F4)
    val terminalGreen = Color(0xFFA6E3A1)
    val terminalBlue = Color(0xFF89B4FA)
    val terminalRed = Color(0xFFF38BA8)
    val terminalCounter = Color(0xFF89DCEB)
    
    // Исправляет ошибку Unresolved reference: donate
    val donate = Color(0xFF8B3FFD) 
}

@Composable
fun WDTTTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark", "amoled" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 1. Получаем базовую палитру
    var colorScheme = when {
        dynamicColor && supportsDynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 2. AMOLED режим
    if (themeMode == "amoled") {
        colorScheme = colorScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF121212)
        )
    }

    // 3. Управление системными барами (StatusBar / NavigationBar)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            
            // Прозрачный или полупрозрачный NavigationBar
            val navBarColor = if (darkTheme) Color.Transparent else lerp(colorScheme.background, colorScheme.surface, 0.5f)
            window.navigationBarColor = navBarColor.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme, 
        typography = WDTTTypography, 
        content = content
    )
}

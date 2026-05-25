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
private val TyrnLightColorScheme = lightColorScheme(
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
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF8),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F7F2),
    surfaceContainer = Color(0xFFEAF2EC),
    surfaceContainerHigh = Color(0xFFE4ECE6),
    surfaceContainerHighest = Color(0xFFDDE7E1),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0CAC3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFEDF3EE),
    inversePrimary = Color(0xFF82D5DC),
)

private val TyrnDarkColorScheme = darkColorScheme(
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
    onBackground = Color(0xFFDDE4DF),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDDE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0CAC3),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF141B18),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF222A26),
    surfaceContainerHighest = Color(0xFF2D3531),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDDE4DF),
    inverseOnSurface = Color(0xFF2C322F),
    inversePrimary = Color(0xFF176B74),
)

private val ForestLightColorScheme = TyrnLightColorScheme.copy(
    primary = Color(0xFF386A20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F397),
    onPrimaryContainer = Color(0xFF062100),
    secondary = Color(0xFF52634B),
    secondaryContainer = Color(0xFFD5E8C9),
    tertiary = Color(0xFF386663),
    tertiaryContainer = Color(0xFFBCECE7),
    background = Color(0xFFF8FBF1),
    surface = Color(0xFFF8FBF1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F6EA),
    surfaceContainer = Color(0xFFECF0E4),
    surfaceContainerHigh = Color(0xFFE6EBDD),
    surfaceContainerHighest = Color(0xFFE0E5D7),
)

private val ForestDarkColorScheme = TyrnDarkColorScheme.copy(
    primary = Color(0xFF9ED67D),
    onPrimary = Color(0xFF0C3900),
    primaryContainer = Color(0xFF205107),
    onPrimaryContainer = Color(0xFFB9F397),
    secondary = Color(0xFFB9CCAF),
    secondaryContainer = Color(0xFF3B4B34),
    tertiary = Color(0xFFA0D0CC),
    tertiaryContainer = Color(0xFF1F4E4B),
    background = Color(0xFF11150F),
    surface = Color(0xFF11150F),
    surfaceContainerLowest = Color(0xFF0B0F09),
    surfaceContainerLow = Color(0xFF171B14),
    surfaceContainer = Color(0xFF1B2018),
    surfaceContainerHigh = Color(0xFF252A21),
    surfaceContainerHighest = Color(0xFF30352B),
)

private val VioletLightColorScheme = TyrnLightColorScheme.copy(
    primary = Color(0xFF7650A6),
    primaryContainer = Color(0xFFECDCFF),
    onPrimaryContainer = Color(0xFF290056),
    secondary = Color(0xFF665A6F),
    secondaryContainer = Color(0xFFEEDDF7),
    tertiary = Color(0xFF805158),
    tertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFFFFF7FD),
    surface = Color(0xFFFFF7FD),
    surfaceContainerLow = Color(0xFFF9F0F8),
    surfaceContainer = Color(0xFFF3EAF2),
    surfaceContainerHigh = Color(0xFFEDE4EC),
    surfaceContainerHighest = Color(0xFFE8DFE6),
)

private val VioletDarkColorScheme = TyrnDarkColorScheme.copy(
    primary = Color(0xFFD7BAFF),
    onPrimary = Color(0xFF461C74),
    primaryContainer = Color(0xFF5D378C),
    onPrimaryContainer = Color(0xFFECDCFF),
    secondary = Color(0xFFD2C1DA),
    secondaryContainer = Color(0xFF4D4356),
    tertiary = Color(0xFFF3B7C0),
    tertiaryContainer = Color(0xFF663A41),
    background = Color(0xFF171219),
    surface = Color(0xFF171219),
    surfaceContainerLowest = Color(0xFF110D13),
    surfaceContainerLow = Color(0xFF1F1A22),
    surfaceContainer = Color(0xFF241E27),
    surfaceContainerHigh = Color(0xFF2E2831),
    surfaceContainerHighest = Color(0xFF39323C),
)

private fun fixedColorScheme(palette: String, darkTheme: Boolean): ColorScheme {
    return when (palette) {
        "forest" -> if (darkTheme) ForestDarkColorScheme else ForestLightColorScheme
        "violet" -> if (darkTheme) VioletDarkColorScheme else VioletLightColorScheme
        else -> if (darkTheme) TyrnDarkColorScheme else TyrnLightColorScheme
    }
}

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
    dynamicColor: Boolean = false,
    palette: String = "tyrn",
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
        else -> fixedColorScheme(palette, darkTheme)
    }

    // 2. AMOLED режим
    if (themeMode == "amoled") {
        colorScheme = colorScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF121212),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF090909),
            surfaceContainerHigh = Color(0xFF101010),
            surfaceContainerHighest = Color(0xFF171717),
            onBackground = Color(0xFFE7E7E7),
            onSurface = Color(0xFFE7E7E7),
            onSurfaceVariant = Color(0xFFC9C9C9)
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

package com.example.arwalking.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ARBlueLight,
    secondary = ARGreenLight,
    tertiary = AROrangeLight,
    background = ARGray900,
    surface = ARGray800,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ARGray100,
    onSurface = ARGray100,
    primaryContainer = ARBlueDark,
    onPrimaryContainer = Color.White,
    secondaryContainer = ARGreenDark,
    onSecondaryContainer = Color.White,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ARBlue,
    secondary = ARGreen,
    tertiary = AROrange,
    background = ARGray50,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ARGray900,
    onSurface = ARGray900,
    primaryContainer = ARBlueLight,
    onPrimaryContainer = ARBlueDark,
    secondaryContainer = ARGreenLight,
    onSecondaryContainer = ARGreenDark,
    surfaceVariant = ARGray100,
    onSurfaceVariant = ARGray700,
    outline = ARGray400,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ARRedLight,
    onErrorContainer = ARRedDark
)

@Composable
fun ARWalkingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
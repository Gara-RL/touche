package be.touche.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF0E7C66)
private val Gold = Color(0xFFFFD166)

private val Light = lightColorScheme(
    primary = Green,
    secondary = Color(0xFF3D5A80),
    tertiary = Color(0xFFB7791F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5DD3B6),
    secondary = Color(0xFF98C1D9),
    tertiary = Gold,
)

@Composable
fun ToucheTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

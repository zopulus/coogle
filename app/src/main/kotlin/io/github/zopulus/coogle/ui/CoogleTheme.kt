// Material component conventions follow tiann/KernelSU (GPL-3.0); see NOTICE.txt.
package io.github.zopulus.coogle.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

internal val LocalUiStyle = staticCompositionLocalOf { UiStyle.default }

@Composable
fun CoogleTheme(style: UiStyle, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark) {
        val activity = context as? Activity ?: return@LaunchedEffect
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalUiStyle provides style) {
        if (style == UiStyle.COUI) {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            COUITheme(controller, content = content)
        } else {
            MaterialExpressiveTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                motionScheme = MotionScheme.expressive(),
                content = content
            )
        }
    }
}

package dev.hori.browser.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Hides the Android system bars (status + navigation) while web content is
 * fullscreen, e.g. a YouTube player. [DisposableEffect] restores them on
 * exit, on tab switch away from a fullscreen tab, and on composition loss.
 */
@Composable
fun FullScreenSystemBarsEffect(fullScreen: Boolean) {
    val activity = LocalContext.current.findActivity()

    DisposableEffect(activity, fullScreen) {
        val window = activity?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (fullScreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        // Showing already-visible bars is a no-op, so unconditional restore
        // is safe for the non-fullscreen effect instances too.
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Finds the hosting Activity from a Compose context. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
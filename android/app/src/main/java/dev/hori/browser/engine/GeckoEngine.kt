package dev.hori.browser.engine

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Owns the single process-wide [GeckoRuntime].
 * One runtime serves every tab session (see GeckoSessionManager).
 */
object GeckoEngine {

    @Volatile
    private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .build()
            ).also { runtime = it }
        }
}
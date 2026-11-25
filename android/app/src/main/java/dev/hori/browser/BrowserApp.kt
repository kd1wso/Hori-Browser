package dev.hori.browser

import android.app.Application
import dev.hori.browser.bridge.HoriCore

/**
 * Process-wide singletons: the Rust core and the Gecko engine.
 * Both are created once per process, not per Activity.
 */
class BrowserApp : Application() {

    lateinit var core: HoriCore
        private set

    override fun onCreate() {
        super.onCreate()
        core = HoriCore.create(filesDir.absolutePath)
    }
}
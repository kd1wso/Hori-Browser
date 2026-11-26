package dev.hori.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hori.browser.browser.BrowserScreen
import dev.hori.browser.browser.BrowserViewModel
import dev.hori.browser.browser.HoriTheme
import dev.hori.browser.engine.OrientationLockController

class MainActivity : ComponentActivity() {

    private lateinit var vm: BrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BrowserApp
        val initialUrl = intent?.data?.toString()

        vm = ViewModelProvider(
            this,
            viewModelFactory { initializer { BrowserViewModel(app, app.core) } },
        )[BrowserViewModel::class.java]

        // Web content orientation locks (YouTube fullscreen) need the host
        // Activity; hand it to both the lock controller and the session manager.
        OrientationLockController.attach(this)
        vm.sessions.activity = this

        setContent {
            HoriTheme {
                BrowserScreen(vm)
            }
            // Handle a VIEW intent that started us cold.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (initialUrl != null) vm.openUrl(initialUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { vm.openUrl(it) }
    }
}
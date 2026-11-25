package dev.hori.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hori.browser.browser.BrowserScreen
import dev.hori.browser.browser.BrowserViewModel
import dev.hori.browser.browser.HoriTheme

class MainActivity : ComponentActivity() {

    private var viewModel: BrowserViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BrowserApp
        val initialUrl = intent?.data?.toString()

        setContent {
            val vm: BrowserViewModel = viewModel(factory = BrowserViewModel.factory(app.core))
            viewModel = vm
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
        intent.data?.toString()?.let { viewModel?.openUrl(it) }
    }
}
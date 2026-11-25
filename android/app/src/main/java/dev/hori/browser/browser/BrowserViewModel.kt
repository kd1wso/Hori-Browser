package dev.hori.browser.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.hori.browser.bridge.HoriCore
import dev.hori.browser.engine.GeckoSessionManager
import dev.hori.browser.engine.TabState
import kotlinx.coroutines.flow.StateFlow

/**
 * UI state holder. Navigation decisions come from Rust ([HoriCore]),
 * web sessions from [GeckoSessionManager].
 */
class BrowserViewModel(
    application: Application,
    private val core: HoriCore,
) : AndroidViewModel(application) {

    val sessions = GeckoSessionManager(application)

    /** Text currently being edited in the address bar (null = not editing). */
    var addressEditing by mutableStateOf<String?>(null)

    var showTabs by mutableStateOf(false)
        private set

    init {
        // Start with one tab on the home page.
        sessions.newTab(core.homeUrl())
    }

    fun observeTab(id: Long): StateFlow<TabState>? = sessions.stateFor(id)

    /** Called when the user submits the address bar. */
    fun submitAddress(input: String) {
        val target = when (val kind = core.classifyInput(input)) {
            is HoriCore.AddressInput.Url -> kind.url
            is HoriCore.AddressInput.Search -> kind.url
        }
        addressEditing = null
        sessions.load(target)
    }

    fun openUrl(url: String) {
        sessions.load(url)
    }

    fun startEditingAddress(current: String) {
        addressEditing = current
    }

    fun cancelEditingAddress() {
        addressEditing = null
    }

    fun newTab() {
        sessions.newTab(core.homeUrl())
        showTabs = false
    }

    fun selectTab(id: Long) {
        sessions.selectTab(id)
        showTabs = false
    }

    fun closeTab(id: Long) {
        sessions.closeTab(id)
    }

    fun toggleTabs() {
        showTabs = !showTabs
    }

    companion object {
        fun factory(core: HoriCore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: error("Application missing")
                    return BrowserViewModel(app, core) as T
                }
            }
    }
}
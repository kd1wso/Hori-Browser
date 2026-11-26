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

    var showSettings by mutableStateOf(false)
        private set

    /** Editable copies of persisted settings, mirrored for the UI. */
    var homeUrlField by mutableStateOf(core.homeUrl())
    var searchEngineField by mutableStateOf(core.searchEngine())

    /** Transient feedback shown in the settings screen. */
    var settingsMessage by mutableStateOf<String?>(null)
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

    /** Navigates the active tab to the configured home page. */
    fun goHome() {
        sessions.load(core.homeUrl())
    }

    fun startEditingAddress(current: String) {
        addressEditing = current
    }

    fun cancelEditingAddress() {
        addressEditing = null
    }

    /** Clears the address field but keeps editing mode and focus. */
    fun clearEditingAddress() {
        addressEditing = ""
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

    // --- Settings ---------------------------------------------------------

    fun openSettings() {
        // Refresh fields from storage each time the screen is opened.
        homeUrlField = core.homeUrl()
        searchEngineField = core.searchEngine()
        settingsMessage = null
        showSettings = true
    }

    fun closeSettings() {
        showSettings = false
        settingsMessage = null
    }

    fun onHomeUrlFieldChange(value: String) {
        homeUrlField = value
        settingsMessage = null
    }

    fun onSearchEngineFieldChange(value: String) {
        searchEngineField = value
        settingsMessage = null
    }

    /** Validates and persists the home page. Returns true on success. */
    fun saveHomeUrl(): Boolean {
        val input = homeUrlField.trim()
        if (input.isEmpty()) {
            settingsMessage = "Start page cannot be empty."
            return false
        }
        return runCatching {
            when (val kind = core.classifyInput(input)) {
                is HoriCore.AddressInput.Url -> {
                    core.setSetting("home_url", kind.url)
                    homeUrlField = kind.url
                    settingsMessage = "Start page saved."
                    true
                }
                is HoriCore.AddressInput.Search -> {
                    settingsMessage = "That doesn't look like a web address."
                    false
                }
            }
        }.getOrElse {
            settingsMessage = "Could not save: ${it.message}"
            false
        }
    }

    /** Validates the search template (must contain %s) and persists it. */
    fun saveSearchEngine(): Boolean {
        val template = searchEngineField.trim()
        if (!template.contains("%s")) {
            settingsMessage = "Search URL must contain %s where the query goes."
            return false
        }
        if (!template.startsWith("http://") && !template.startsWith("https://")) {
            settingsMessage = "Search URL must start with http:// or https://."
            return false
        }
        return runCatching {
            core.setSetting("search_engine", template)
        }.map {
            searchEngineField = template
            settingsMessage = "Search engine saved."
            true
        }.getOrElse {
            settingsMessage = "Could not save: ${it.message}"
            false
        }
    }

    /** Restores the built-in defaults for start page and search engine. */
    fun resetSettingsToDefaults() {
        runCatching {
            core.setSetting("home_url", DEFAULT_HOME_URL)
            core.setSetting("search_engine", DEFAULT_SEARCH_ENGINE)
        }
        homeUrlField = core.homeUrl()
        searchEngineField = core.searchEngine()
        settingsMessage = "Defaults restored."
    }

    companion object {
        // Mirrors core::settings defaults so "Reset" works even if storage had
        // custom values; kept in sync with the Rust side.
        const val DEFAULT_HOME_URL = "https://github.com/kd1wso"
        const val DEFAULT_SEARCH_ENGINE = "https://duckduckgo.com/?q=%s"

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
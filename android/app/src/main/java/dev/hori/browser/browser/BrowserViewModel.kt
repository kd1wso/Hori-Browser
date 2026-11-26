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

    /** Snapshot of a recent site for the start page. */
    data class RecentSite(val url: String, val title: String)

    /** A quick-link tile on the start page. */
    data class QuickLink(val url: String, val label: String, val glyph: String)

    val sessions = GeckoSessionManager(application) { url, title ->
        core.recordVisit(url, title)
        refreshHomeData()
    }

    /** Text currently being edited in the address bar (null = not editing). */
    var addressEditing by mutableStateOf<String?>(null)

    var showTabs by mutableStateOf(false)
        private set

    var showSettings by mutableStateOf(false)
        private set

    var showAbout by mutableStateOf(false)
        private set

    var showHistory by mutableStateOf(false)
        private set

    // --- Settings state ---------------------------------------------------

    var homeUrlField by mutableStateOf(core.homeUrl())
    var searchEngineField by mutableStateOf(core.searchEngine())
    var settingsMessage by mutableStateOf<String?>(null)
        private set

    var themeMode by mutableStateOf(core.themeMode())
        private set

    val searchEngines: List<HoriCore.Engine> = core.searchEngines()

    /** History entry count, refreshed when settings opens or data changes. */
    var historyCount by mutableStateOf(0)
        private set

    // --- Start page state -------------------------------------------------

    var recentSites by mutableStateOf<List<RecentSite>>(emptyList())
        private set

    /** Full history list shown in the History screen (newest first). */
    var historyEntries by mutableStateOf<List<RecentSite>>(emptyList())
        private set

    val quickLinks: List<QuickLink> = listOf(
        QuickLink("https://github.com/kd1wso", "GitHub", "GH"),
        QuickLink("https://duckduckgo.com", "Search", "DD"),
        QuickLink("https://www.wikipedia.org", "Wikipedia", "W"),
        QuickLink("https://news.ycombinator.com", "HN", "HN"),
        QuickLink("https://www.youtube.com", "YouTube", "YT"),
        QuickLink("https://mozilla.org", "Mozilla", "M"),
    )

    init {
        // Start with one tab on Hori's own start page.
        sessions.newTab(null)
        refreshHomeData()
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

    /** Runs a search from the start page's own search box. */
    fun searchFromHome(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        when (val kind = core.classifyInput(trimmed)) {
            is HoriCore.AddressInput.Url -> sessions.load(kind.url)
            is HoriCore.AddressInput.Search -> sessions.load(kind.url)
        }
    }

    fun openUrl(url: String) {
        sessions.load(url)
    }

    /** Shows Hori's start page in the active tab. */
    fun goHome() {
        sessions.showHome()
        refreshHomeData()
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
        sessions.newTab(null)
        showTabs = false
        refreshHomeData()
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

    private fun refreshHomeData() {
        val all = core.recentHistory(limit = 200).map { RecentSite(it.url, it.title) }
        historyEntries = all
        recentSites = all.take(12)
        historyCount = core.historyCount()
    }

    // --- Settings ---------------------------------------------------------

    fun openSettings() {
        // Refresh fields from storage each time the screen is opened.
        homeUrlField = core.homeUrl()
        searchEngineField = core.searchEngine()
        settingsMessage = null
        refreshHomeData()
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

    /** Applies one of the built-in search engines. */
    fun selectSearchEngine(engine: HoriCore.Engine) {
        searchEngineField = engine.urlTemplate
        runCatching { core.setSetting("search_engine", engine.urlTemplate) }
            .onSuccess { settingsMessage = "Search engine: ${engine.name}" }
            .onFailure { settingsMessage = "Could not save: ${it.message}" }
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

    fun setThemeMode(mode: HoriCore.ThemeMode) {
        themeMode = mode
        runCatching { core.setThemeMode(mode) }
            .onFailure { settingsMessage = "Could not save theme: ${it.message}" }
    }

    /** Clears Hori's browsing history (the recent-sites list). */
    fun clearHistory() {
        runCatching { core.clearHistory() }
            .onSuccess {
                refreshHomeData()
                settingsMessage = "History cleared."
            }
            .onFailure { settingsMessage = "Could not clear history: ${it.message}" }
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

    // --- History screen ---------------------------------------------------

    /** Opens the full history screen, refreshing entries first. */
    fun openHistory() {
        refreshHomeData()
        showHistory = true
    }

    fun closeHistory() {
        showHistory = false
    }

    // --- About ------------------------------------------------------------

    fun openAbout() {
        showAbout = true
    }

    fun closeAbout() {
        showAbout = false
    }

    fun coreVersion(): String = core.version()

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
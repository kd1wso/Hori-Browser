package dev.hori.browser.bridge

import dev.hori.core.BrowserCore
import dev.hori.core.InputKind
import dev.hori.core.SearchEngine

/**
 * Thin Kotlin facade over the UniFFI-generated [BrowserCore].
 *
 * Everything browser-logical (input classification, settings, history) lives
 * in Rust. This class only adapts types and provides safe defaults so the UI
 * never crashes on a core error.
 */
class HoriCore private constructor(private val core: BrowserCore) {

    /** Result of interpreting address-bar input. */
    sealed interface AddressInput {
        data class Url(val url: String) : AddressInput
        data class Search(val url: String, val query: String) : AddressInput
    }

    /** Theme preference as stored in the core. */
    enum class ThemeMode { SYSTEM, LIGHT, DARK;

        companion object {
            fun fromStored(raw: String?): ThemeMode = when (raw) {
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
        }

        val stored: String
            get() = when (this) {
                SYSTEM -> "system"
                LIGHT -> "light"
                DARK -> "dark"
            }
    }

    data class Engine(val id: String, val name: String, val urlTemplate: String)

    fun classifyInput(input: String): AddressInput =
        when (val kind = core.classifyInput(input)) {
            is InputKind.Url -> AddressInput.Url(kind.url)
            is InputKind.Search -> AddressInput.Search(kind.url, kind.query)
        }

    /** Search URL for [query] with the currently configured engine. */
    fun searchUrl(query: String): String = core.searchUrl(query)

    fun homeUrl(): String = core.homeUrl()

    fun searchEngine(): String = core.searchEngine()

    fun searchEngines(): List<Engine> =
        core.searchEngines().map { Engine(it.id, it.name, it.urlTemplate) }

    fun getSetting(key: String): String? = core.getSetting(key)

    fun setSetting(key: String, value: String) = core.setSetting(key, value)

    // --- Theme -----------------------------------------------------------

    fun themeMode(): ThemeMode = ThemeMode.fromStored(core.themeMode())

    fun setThemeMode(mode: ThemeMode) = core.setSetting("theme_mode", mode.stored)

    // --- History ---------------------------------------------------------

    data class History(
        val url: String,
        val title: String,
        val visitedAt: Long,
    )

    fun recordVisit(url: String, title: String) {
        if (url.isBlank() || !url.startsWith("http")) return
        runCatching { core.recordVisit(url, title) }
    }

    fun recentHistory(limit: Int = 12): List<History> =
        runCatching {
            core.recentHistory(limit.toUInt()).map {
                History(it.url, it.title, it.visitedAt)
            }
        }.getOrElse { emptyList() }

    fun historyCount(): Int = runCatching { core.historyCount().toInt() }.getOrDefault(0)

    fun clearHistory() {
        core.clearHistory()
    }

    fun clearBrowsingData() {
        core.clearBrowsingData()
    }

    fun version(): String = runCatching { core.version() }.getOrDefault("0.1.0")

    companion object {
        fun create(dataDir: String): HoriCore {
            // UniFFI's generated loader extracts libhori_core.so via JNA.
            return HoriCore(BrowserCore(dataDir))
        }
    }
}
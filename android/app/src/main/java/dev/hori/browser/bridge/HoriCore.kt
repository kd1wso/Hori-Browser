package dev.hori.browser.bridge

import dev.hori.core.BrowserCore
import dev.hori.core.InputKind

/**
 * Thin Kotlin facade over the UniFFI-generated [BrowserCore].
 *
 * Everything browser-logical (input classification, settings, later:
 * bookmarks/history/sync) lives in Rust. This class only adapts types
 * and provides safe defaults so the UI never crashes on a core error.
 */
class HoriCore private constructor(private val core: BrowserCore) {

    /** Result of interpreting address-bar input. */
    sealed interface AddressInput {
        data class Url(val url: String) : AddressInput
        data class Search(val url: String, val query: String) : AddressInput
    }

    fun classifyInput(input: String): AddressInput =
        when (val kind = core.classifyInput(input)) {
            is InputKind.Url -> AddressInput.Url(kind.url)
            is InputKind.Search -> AddressInput.Search(kind.url, kind.query)
        }

    fun homeUrl(): String = core.homeUrl()

    fun searchEngine(): String = core.searchEngine()

    fun getSetting(key: String): String? = core.getSetting(key)

    fun setSetting(key: String, value: String) = core.setSetting(key, value)

    companion object {
        fun create(dataDir: String): HoriCore {
            // UniFFI's generated loader extracts libhori_core.so via JNA.
            return HoriCore(BrowserCore(dataDir))
        }
    }
}
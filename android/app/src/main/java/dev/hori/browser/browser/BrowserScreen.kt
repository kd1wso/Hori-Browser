package dev.hori.browser.browser

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hori.browser.engine.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoView

/**
 * Root screen. Applies the theme (system/light/dark from settings) and switches
 * between the browser, Settings, History and About.
 */
@Composable
fun BrowserScreen(vm: BrowserViewModel) {
    // Theme follows the user's stored preference (system/light/dark).
    HoriTheme(themeMode = vm.themeMode) {
        when {
            vm.showAbout -> AboutScreen(coreVersion = vm.coreVersion(), onBack = vm::closeAbout)
            vm.showSettings -> SettingsScreen(
                vm = vm,
                onOpenHistory = vm::openHistory,
                onOpenBookmarks = vm::openBookmarks,
                onOpenDownloads = vm::openDownloads,
                onOpenAbout = vm::openAbout,
            )
            vm.showHistory -> HistoryScreen(
                entries = vm.historyEntries,
                onOpen = { url ->
                    vm.closeHistory()
                    vm.openUrl(url)
                },
                onClearAll = vm::clearHistory,
                onBack = vm::closeHistory,
            )
            vm.showBookmarks -> BookmarksScreen(
                bookmarks = vm.bookmarks,
                onOpen = { url ->
                    vm.closeBookmarks()
                    vm.openUrl(url)
                },
                onRemove = vm::removeBookmark,
                onBack = vm::closeBookmarks,
            )
            vm.showDownloads -> DownloadsScreen(
                downloads = vm.downloads.collectAsState().value,
                onOpen = vm::openDownload,
                onCancel = vm::cancelDownload,
                onRemove = vm::removeDownload,
                onClearFinished = vm::clearFinishedDownloads,
                onBack = vm::closeDownloads,
            )
            else -> BrowserContent(vm)
        }
    }
}

/** The browser itself: address bar, web content or start page, toolbar, tabs. */
@Composable
private fun BrowserContent(vm: BrowserViewModel) {
    val tabs by vm.sessions.tabList.collectAsState()
    val activeId by vm.sessions.activeId.collectAsState()
    val revision by vm.sessions.activeRevision.collectAsState()

    val activeStateFlow = remember(activeId) { vm.observeTab(activeId) }
    val fallbackFlow = remember { MutableStateFlow(TabState(id = 0)) }
    val active by remember(activeStateFlow) { activeStateFlow ?: fallbackFlow }.collectAsState()

    // While web content is fullscreen (e.g. a YouTube player), hide the
    // Android system bars too. DisposableEffect restores them on exit.
    FullScreenSystemBarsEffect(active.fullScreen)

    // System Back first leaves web fullscreen (matching major browsers),
    // before the normal in-page history navigation kicks in.
    BackHandler(enabled = active.fullScreen) {
        vm.sessions.exitFullScreen()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No safe-area padding in fullscreen: the video must reach
                // every physical edge of the screen.
                .then(if (active.fullScreen) Modifier else Modifier.safeDrawingPadding()),
        ) {

            if (!active.fullScreen) {
                AddressBar(
                    url = active.url,
                    title = active.title,
                    loading = active.loading,
                    editing = vm.addressEditing,
                    isBookmarked = vm.isCurrentBookmarked,
                    onStartEditing = vm::startEditingAddress,
                    onCancelEditing = vm::cancelEditingAddress,
                    onSubmit = vm::submitAddress,
                    onEditChange = { vm.addressEditing = it },
                    onClearEditing = vm::clearEditingAddress,
                    onHome = vm::goHome,
                    onToggleBookmark = { vm.toggleBookmarkCurrent(active.title) },
                )

                // Keep the bookmark star in sync with the active page.
                LaunchedEffect(active.url) { vm.onActiveUrlChanged(active.url) }

                // Thinner, primary-colored page-load bar.
                if (active.loading) {
                    LinearProgressIndicator(
                        progress = { active.progress / 100f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (active.showHome) {
                    // Hori's own start page replaces web content.
                    HomeScreen(
                        recentSites = vm.recentSites,
                        quickLinks = vm.quickLinks,
                        onSearch = vm::searchFromHome,
                        onOpenUrl = vm::openUrl,
                        onShowAllHistory = vm::openHistory,
                    )
                } else {
                    // One GeckoView for the whole app; switching tabs
                    // rebinds it to the active session.
                    AndroidView(
                        factory = { ctx ->
                            GeckoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                        update = { view ->
                            // Reads `revision` so tab switches re-run this.
                            val rev = revision
                            val session = vm.sessions.activeSession()
                            if (rev >= 0 && session != null && view.session !== session) {
                                view.setSession(session)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (!active.fullScreen) {
                BottomToolbar(
                    canGoBack = active.canGoBack,
                    canGoForward = active.canGoForward,
                    loading = active.loading,
                    tabCount = tabs.size,
                    onBack = vm.sessions::goBack,
                    onForward = vm.sessions::goForward,
                    onReload = vm.sessions::reload,
                    onStop = vm.sessions::stop,
                    onTabs = vm::toggleTabs,
                    onSettings = vm::openSettings,
                )
            }
        }

        if (!active.fullScreen && vm.showTabs) {
            TabsSheet(
                tabs = tabs,
                activeId = activeId,
                onSelect = vm::selectTab,
                onClose = vm::closeTab,
                onNew = vm::newTab,
                onDismiss = vm::toggleTabs,
            )
        }
    }
}
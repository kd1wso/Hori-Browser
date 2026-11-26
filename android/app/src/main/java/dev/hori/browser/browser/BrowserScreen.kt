package dev.hori.browser.browser

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import dev.hori.browser.engine.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoView

/**
 * Main browser screen: address bar on top, web content below, toolbar at the
 * bottom. The single GeckoView re-attaches to whichever session is active.
 */
@Composable
fun BrowserScreen(vm: BrowserViewModel) {
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
                    onStartEditing = vm::startEditingAddress,
                    onCancelEditing = vm::cancelEditingAddress,
                    onSubmit = vm::submitAddress,
                    onEditChange = { vm.addressEditing = it },
                    onClearEditing = vm::clearEditingAddress,
                )

                if (active.loading) {
                    LinearProgressIndicator(
                        progress = { active.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                // One GeckoView for the whole app; switching tabs rebinds it.
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
                        // Reads `revision` so tab switches re-run this block.
                        val rev = revision
                        val session = vm.sessions.activeSession()
                        if (rev >= 0 && session != null && view.session !== session) {
                            view.setSession(session)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!active.fullScreen) {
                BottomToolbar(
                    canGoBack = active.canGoBack,
                    canGoForward = active.canGoForward,
                    tabCount = tabs.size,
                    onBack = vm.sessions::goBack,
                    onForward = vm.sessions::goForward,
                    onReload = vm.sessions::reload,
                    onTabs = vm::toggleTabs,
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
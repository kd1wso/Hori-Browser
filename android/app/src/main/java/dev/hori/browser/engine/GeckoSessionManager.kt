package dev.hori.browser.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

/**
 * Tab state visible to the UI. Sessions themselves are Android objects and
 * stay here on the Kotlin side; Rust never sees them.
 */
data class TabState(
    val id: Long,
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: String? = null,
)

/**
 * Owns GeckoSessions, one per tab, on top of a shared GeckoRuntime.
 * Switching tabs = attaching another session to the single GeckoView.
 */
class GeckoSessionManager(context: Context) {

    private val runtime = GeckoEngine.runtime(context)

    private data class Tab(
        val session: GeckoSession,
        val state: MutableStateFlow<TabState>,
    )

    private val tabs = LinkedHashMap<Long, Tab>()
    private var nextId = 1L

    private val _tabList = MutableStateFlow<List<TabState>>(emptyList())
    val tabList: StateFlow<List<TabState>> = _tabList

    private val _activeId = MutableStateFlow(0L)
    val activeId: StateFlow<Long> = _activeId

    /** Increments whenever the active session changes; UI re-attaches the GeckoView. */
    private val _activeRevision = MutableStateFlow(0)
    val activeRevision: StateFlow<Int> = _activeRevision

    val activeTab: StateFlow<TabState>?
        get() = tabs[_activeId.value]?.state

    fun activeSession(): GeckoSession? = tabs[_activeId.value]?.session

    fun sessionFor(id: Long): GeckoSession? = tabs[id]?.session

    fun stateFor(id: Long): StateFlow<TabState>? = tabs[id]?.state

    /** Creates a tab, makes it active, optionally loads [url]. */
    fun newTab(url: String?): Long {
        val id = nextId++
        val session = GeckoSession()
        session.open(runtime)
        val state = MutableStateFlow(TabState(id = id, url = url ?: ""))
        val tab = Tab(session, state)
        attachDelegates(tab)
        tabs[id] = tab
        _activeId.value = id
        _activeRevision.value++
        publishList()
        if (url != null) session.loadUri(url)
        return id
    }

    fun selectTab(id: Long) {
        if (tabs.containsKey(id) && _activeId.value != id) {
            _activeId.value = id
            _activeRevision.value++
        }
    }

    fun closeTab(id: Long) {
        val tab = tabs.remove(id) ?: return
        val wasActive = _activeId.value == id
        tab.session.close()
        if (wasActive) {
            _activeId.value = tabs.keys.lastOrNull() ?: 0L
            _activeRevision.value++
        }
        if (tabs.isEmpty()) {
            newTab(null)
        }
        publishList()
    }

    fun load(url: String) {
        activeSession()?.loadUri(url)
    }

    fun reload() {
        activeSession()?.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
    }

    fun goBack() {
        activeSession()?.goBack(false)
    }

    fun goForward() {
        activeSession()?.goForward(false)
    }

    private fun publishList() {
        _tabList.value = tabs.values.map { it.state.value }
    }

    private fun update(id: Long, transform: (TabState) -> TabState) {
        val tab = tabs[id] ?: return
        tab.state.value = transform(tab.state.value)
        publishList()
    }

    private fun attachDelegates(tab: Tab) {
        val id = tab.state.value.id

        tab.session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                update(id) { it.copy(progress = progress, loading = progress in 1..99) }
            }

            override fun onPageStart(session: GeckoSession, url: String) {
                update(id) { it.copy(url = url, loading = true, error = null) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                update(id) { it.copy(loading = false, progress = 100) }
            }
        }

        tab.session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                update(id) { it.copy(title = title ?: "") }
            }
        }

        tab.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                // 0.1: allow everything. 0.4 hooks the ad/tracker filter here.
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                update(id) { it.copy(loading = false, error = error.toString()) }
                return null // show Gecko's built-in error page
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                update(id) { it.copy(url = url ?: it.url) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                update(id) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                update(id) { it.copy(canGoForward = canGoForward) }
            }
        }
    }
}
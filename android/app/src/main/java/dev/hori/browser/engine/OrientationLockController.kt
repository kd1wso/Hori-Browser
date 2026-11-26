package dev.hori.browser.engine

import android.app.Activity
import android.content.pm.ActivityInfo
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.OrientationController
import java.lang.ref.WeakReference

/**
 * Bridges GeckoView's orientation lock requests to the host Activity.
 *
 * Web content (a YouTube player, for example) calls `screen.orientation.lock()`.
 * GeckoRuntime converts that into an `ActivityInfo.SCREEN_ORIENTATION_*` value and
 * invokes [onOrientationLock]. Without a delegate GeckoView answers "Not supported"
 * and the screen never rotates — that was the bug.
 *
 * Behaviour:
 *  - fullscreen entry with no page lock: [SCREEN_ORIENTATION_FULL_USER] lets the
 *    device rotate into landscape when the user's auto-rotate is on, and stays put
 *    when it is off;
 *  - a page lock request is honoured verbatim (e.g. SENSOR_LANDSCAPE);
 *  - unlock / exit restores [SCREEN_ORIENTATION_UNSPECIFIED] (normal behaviour).
 */
object OrientationLockController : OrientationController.OrientationDelegate {

    private var activityRef: WeakReference<Activity>? = null

    /** True while web content explicitly holds an orientation lock. */
    private var pageLocked = false

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** Called when web content enters fullscreen: allow autorotation as a base. */
    fun beginFullScreen(activity: Activity) {
        attach(activity)
        if (!pageLocked) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    /** Called by GeckoRuntime on the UI thread when the page locks orientation. */
    override fun onOrientationLock(aOrientation: Int): GeckoResult<AllowOrDeny> {
        val activity = activityRef?.get()
            ?: return GeckoResult.fromValue(AllowOrDeny.DENY)
        pageLocked = true
        // aOrientation is already an ActivityInfo.SCREEN_ORIENTATION_* constant.
        activity.requestedOrientation = aOrientation
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
    }

    /** Called by GeckoRuntime on the UI thread when the page releases its lock. */
    override fun onOrientationUnlock() {
        pageLocked = false
        restore()
    }

    /** Returns the screen to the normal (unspecified / system) orientation. */
    fun restore() {
        pageLocked = false
        val activity = activityRef?.get() ?: return
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
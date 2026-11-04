package gb.frame

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * Edge-to-edge, full-screen window shared by every GB app. The system bars are hidden in games and on devices with a
 * physical controller (see [hideSystemBars]), so the bar colors never show and are not set.
 *
 * Google Play flags apps that target SDK 35 and still use the edge-to-edge window APIs that Android 15 deprecates
 * (`Window.setStatusBarColor`/`setNavigationBarColor`, the `shortEdges`/`default` cutout modes, the
 * `statusBarColor`/`navigationBarColor` theme attributes). androidx `enableEdgeToEdge()` still calls them on older
 * Android versions, so the apps call [apply] instead and must not use those APIs or `enableEdgeToEdge()`.
 */
object FullScreen {
    /** Call from `Activity.onCreate`, before setting the content. */
    fun apply(activity: Activity) {
        val window = activity.window
        // Draw under the camera cutout. ALWAYS is the non-deprecated mode on Android 11+; before it only the short
        // edges can be used (Android 15 treats every mode as ALWAYS for SDK 35 apps).
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Android 15+ is always edge-to-edge. Before it, lay out behind the bars with the stable layout flags, so the
        // content does not jump when a swipe shows the bars for a moment.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or LAYOUT_FLAGS
        }
        hideSystemBars(activity)
    }

    /**
     * Hides the status and navigation bars; a swipe shows them for a moment. Call again when the window regains focus.
     * The bars are only hidden in a game ([inGame]) or on a device with a physical controller, which has no need for
     * the on-screen navigation. On a touch-only device they stay visible in menus and the library.
     */
    fun hideSystemBars(activity: Activity, inGame: Boolean = false) {
        val window = activity.window
        if (!inGame && !hasPhysicalController(activity)) {
            showSystemBars(activity)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = LAYOUT_FLAGS or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** Makes the status and navigation bars visible again. */
    fun showSystemBars(activity: Activity) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = LAYOUT_FLAGS
        }
    }

    @Suppress("DEPRECATION")
    private const val LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
}

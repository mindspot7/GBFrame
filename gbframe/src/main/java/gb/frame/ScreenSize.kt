package gb.frame

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Real screen size in pixels. An immersive app's own metrics leave out the bars: a 720x720 screen reports 720x600,
 * so the largest whole scale that fits came out 2x instead of 3x. The window manager reports the whole display.
 */
fun realScreenSize(context: Context): Pair<Int, Int> {
    val m = DisplayMetrics()
    val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    @Suppress("DEPRECATION")
    if (wm != null) wm.defaultDisplay.getRealMetrics(m)
    else m.setTo(context.resources.displayMetrics)
    return m.widthPixels to m.heightPixels
}

package gb.frame

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/** Fits the actual app artwork into the existing UI frame, excluding launcher safe-zone padding. */
@Composable
fun rememberAppIconPainter(@DrawableRes resource: Int): Painter {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration, resource) { BitmapPainter(appIcon(resources, resource)) }
}

// Cropped icons by resource: the crop scans every pixel, and the library asks for the icon in several places while
// its first frames animate (each one a main-thread stall); computed once per process, also from a background thread
private val icons = java.util.concurrent.ConcurrentHashMap<Int, androidx.compose.ui.graphics.ImageBitmap>()

/** Decodes and crops the icon ahead of time (call off the main thread at startup). */
fun preloadAppIcon(resources: android.content.res.Resources, @DrawableRes resource: Int) { appIcon(resources, resource) }

private fun appIcon(resources: android.content.res.Resources, @DrawableRes resource: Int): androidx.compose.ui.graphics.ImageBitmap =
    icons.getOrPut(resource) {
        val bitmap = requireNotNull(BitmapFactory.decodeResource(resources, resource)) {
            "App icon must be a raster resource"
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((pixels[y * bitmap.width + x] ushr 24) > 0) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        val cropped = if (right >= left && bottom >= top)
            Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        else bitmap
        if (cropped !== bitmap) bitmap.recycle()
        cropped.asImageBitmap()
    }

/** What the app shows while it starts: its icon, centered (instead of a spinner). */
@Composable
fun AppStartScreen(@DrawableRes icon: Int) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.Image(rememberAppIconPainter(icon), null, androidx.compose.ui.Modifier.size(96.dp))
    }
}

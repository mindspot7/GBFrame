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

/** Fits the actual app artwork into the existing UI frame, excluding launcher safe-zone padding. */
@Composable
fun rememberAppIconPainter(@DrawableRes resource: Int): Painter {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(resources, configuration, resource) {
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
        BitmapPainter(cropped.asImageBitmap())
    }
}

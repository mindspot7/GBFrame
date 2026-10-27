package gb.frame

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/** Standard quick-menu save/load row; timestamp is epoch milliseconds. */
@Composable
fun QuickMenuStateSlot(id: String, label: String, time: Long?, thumbnail: Bitmap?, enabled: Boolean, onClick: () -> Unit) {
    QuickMenuItemContainer(id, enabled, onClick) {
        Box(Modifier.size(88.dp, 66.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
            if (thumbnail != null) Image(thumbnail.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            else Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) Color.White else palette.dim, fontWeight = FontWeight.SemiBold)
            Text(time?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Empty",
                style = MaterialTheme.typography.bodySmall, color = palette.dim)
        }
    }
}

package gb.frame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Screenshot card used inside the All in One library layout. */
@Composable
fun ResumeCard(e: ResumeEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = palette
    var shot by remember(e) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(e) {
        shot = withContext(Dispatchers.IO) { e.screenshot?.let { runCatching { DataFile(it.path).inputStream().use { stream -> android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap() } }.getOrNull() } }
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "scale")
    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(e.time))
    Column(modifier.scale(scale)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                .border(if (focused) 3.dp else 1.dp, if (focused) p.highlight else Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
        ) {
            shot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                ?: Icon(Icons.Default.Image, null, Modifier.align(Alignment.Center).size(40.dp), tint = Color.White.copy(alpha = .4f))
            Text(e.label, Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(e.title, color = if (focused) p.highlight else Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(date, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
    }
}

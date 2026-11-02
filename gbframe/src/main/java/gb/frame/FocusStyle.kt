package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Subtle but visible focus treatment shared by selectable rows in kit submenus. */
@Composable
fun Modifier.kitFocusHighlight(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(14.dp),
): Modifier {
    val p = palette
    val clipped = clip(shape)
    return if (focused) clipped
        .background(p.accent.copy(alpha = .18f))
        .border(1.dp, p.accent.copy(alpha = .42f), shape)
    else clipped
}

/** Shared action row for the library's per-game options dialog. */
@Composable
fun GameOptionsActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val p = palette
    val shape = RoundedCornerShape(14.dp)
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .kitFocusHighlight(focused, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (enabled) contentColor else p.dim, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

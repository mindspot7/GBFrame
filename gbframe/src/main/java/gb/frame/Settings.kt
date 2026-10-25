package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings page building blocks: a titled card ([SettingsGroup]) holding rows separated by [SettingsDivider]. */
@Composable
fun SettingsGroup(title: String?, icon: ImageVector?, content: @Composable ColumnScope.() -> Unit) {
    val p = palette
    Column {
        if (title != null) Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = p.highlight, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = p.highlight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = p.card, border = p.panelBorder, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = palette.border.copy(alpha = .55f))

@Composable
fun RowIcon(icon: ImageVector, warn: Boolean = false) {
    val c = if (warn) palette.warn else palette.accent
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = palette.dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun ActionRow(icon: ImageVector, title: String, sub: String?, action: String, onAction: () -> Unit, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, warn)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (warn) palette.warn else palette.dim,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceRow(title: String, sub: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.dim)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = p.highlight.copy(alpha = .25f), activeContentColor = MaterialTheme.colorScheme.onSecondary,
                        inactiveContainerColor = Color.Transparent, inactiveContentColor = p.dim, activeBorderColor = p.highlight.copy(alpha = .6f),
                        inactiveBorderColor = p.border),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
fun InfoRow(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = palette.dim)
    }
}

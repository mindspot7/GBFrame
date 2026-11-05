package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shared settings-page back button that establishes controller focus as soon as a category opens. */
@Composable
fun SettingsPageBackButton(pageKey: Any?, onBack: () -> Unit) {
    val requester = remember(pageKey) { FocusRequester() }
    val hasFocus = remember(pageKey) { mutableStateOf(false) }
    val view = LocalView.current
    val p = palette
    LaunchedEffect(pageKey) {
        repeat(8) {
            view.requestFocusFromTouch()
            runCatching { requester.requestFocus() }
            delay(50)
            if (hasFocus.value) return@LaunchedEffect
        }
    }
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).kitFocusHighlight(hasFocus.value, CircleShape)
        .focusRequester(requester).onFocusChanged { hasFocus.value = it.isFocused }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (hasFocus.value) p.onAccent else MaterialTheme.colorScheme.onSurface)
    }
}

/** Shared defaults and enforcement policy for optional game-specific patches. */
object GameHackPolicy {
    const val DEFAULT_ENABLED = true
    const val WIDESCREEN_DEFAULT_ENABLED = false

    /** Hardcore always wins over a saved toggle value. */
    fun shouldApply(enabled: Boolean, hardcoreActive: Boolean): Boolean = enabled && !hardcoreActive
}

/** Settings page building blocks: a titled card ([SettingsGroup]) holding rows separated by [SettingsDivider]. */
@Composable
fun SettingsGroup(title: String?, icon: ImageVector?, content: @Composable ColumnScope.() -> Unit) {
    val p = palette
    Column {
        if (title != null) Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = p.highlight, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            KitText(title, style = MaterialTheme.typography.labelLarge, color = p.highlight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, uppercase = true)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = p.card, border = p.panelBorder, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = palette.border.copy(alpha = .55f))

@Composable
fun RowIcon(icon: ImageVector, warn: Boolean = false, focused: Boolean = false) {
    val p = palette
    val c = if (focused) p.onAccent else if (warn) p.warn else p.accent
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ToggleRow(icon: ImageVector, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) =
    ToggleRow(icon, title, sub, value, enabled = true, onChange = onChange)

@Composable
fun ToggleRow(icon: ImageVector, title: String, sub: String?, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p = palette
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .kitFocusHighlight(focused, RoundedCornerShape(16.dp))
            .toggleable(value = value, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, focused = focused)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            KitText(title, style = MaterialTheme.typography.titleSmall, color = if (focused) p.onAccent else MaterialTheme.colorScheme.onSurface)
            if (sub != null) KitText(sub, style = MaterialTheme.typography.bodySmall, color = if (focused) p.onAccent.copy(alpha = .82f) else p.dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = null, enabled = enabled)
    }
}

/** Shared opt-in for core-provided widescreen game patches. The consumer owns patch lookup/application. */
@Composable
fun WidescreenGameHacksRow(value: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(
    Icons.Default.Tune,
    "Widescreen hack",
    "Uses built-in widescreen codes in supported games. It can show more of the scene, but may cause visual glitches. Your own cheats stay separate.",
    value,
    onChange,
)

/** Shared main-settings row for adaptive render skipping, independent of fast-forward and audio pacing. */
@Composable
fun AdaptiveGraphicsSettingsRow(value: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(
    Icons.Default.SkipNext,
    "Auto frame skip",
    "When the game slows down, skips drawing some frames so the CPU can keep running the game. This may improve speed, but motion looks less smooth. Audio timing stays normal.",
    value,
    onChange,
)

@Composable
fun ActionRow(icon: ImageVector, title: String, sub: String?, action: String, onAction: () -> Unit, warn: Boolean = false, subMaxLines: Int = 2) {
    var actionFocused by remember { mutableStateOf(false) }
    val p = palette
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, warn)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            KitText(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) KitText(sub, style = MaterialTheme.typography.bodySmall, color = if (warn) palette.warn else palette.dim,
                maxLines = subMaxLines, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(
            onClick = onAction,
            modifier = Modifier.onFocusChanged { actionFocused = it.isFocused }
                .then(if (actionFocused) Modifier.border(3.dp, p.highlight, RoundedCornerShape(12.dp)) else Modifier),
        ) { KitText(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceRow(title: String, sub: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = palette
    var focusedIndex by remember(title) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        KitText(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        if (sub != null) KitText(sub, style = MaterialTheme.typography.bodySmall, color = p.dim)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val focused = focusedIndex == i
                SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) focusedIndex = i else if (focusedIndex == i) focusedIndex = -1
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = if (focused) p.accent else p.highlight.copy(alpha = .25f),
                        activeContentColor = if (focused) p.onAccent else MaterialTheme.colorScheme.onSurface,
                        inactiveContainerColor = if (focused) p.accent else Color.Transparent,
                        inactiveContentColor = if (focused) p.onAccent else p.dim,
                        activeBorderColor = p.highlight.copy(alpha = .8f),
                        inactiveBorderColor = if (focused) p.highlight else p.border,
                    ),
                ) { KitText(label, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
fun InfoRow(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        KitText(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        KitText(sub, style = MaterialTheme.typography.bodySmall, color = palette.dim)
    }
}

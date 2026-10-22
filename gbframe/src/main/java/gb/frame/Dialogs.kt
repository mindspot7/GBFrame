package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Confirmation dialog drawn inside the app window (not a separate dialog window), so the activity's controller
 * mapping keeps working: A confirms, B/Back cancels, touch works on both buttons.
 */
@Composable
fun ConfirmDialog(
    title: String, message: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit, cancel: String = "Cancel",
) {
    val p = palette
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonA, Key.DirectionCenter, Key.Enter -> { onConfirm(); true }
                    Key.ButtonB -> { onCancel(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.fillMaxWidth(0.55f).widthIn(min = 240.dp, max = 360.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(28.dp), color = lerpPanel(p), border = p.panelBorder, shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(14.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text(cancel, color = p.accent) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, modifier = Modifier.focusRequester(fr), shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)) { Text(confirm) }
                }
            }
        }
    }
}

private fun lerpPanel(p: EmuPalette) = androidx.compose.ui.graphics.lerp(p.surface, Color.Black, 0.05f)

/** Controller button glyph with a label, Nintendo-style colors (A red, B yellow, X blue, Y green). */
@Composable
fun ButtonHint(button: String, color: Color, label: String, modifier: Modifier = Modifier.padding(start = 16.dp)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(button, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge)
    }
}

object PadColors {
    val A = Color(0xFFE5483B)
    val B = Color(0xFFF2C230)
    val X = Color(0xFF3B7BE5)
    val Y = Color(0xFF3BB273)
}

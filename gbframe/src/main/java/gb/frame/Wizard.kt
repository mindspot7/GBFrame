package gb.frame

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One page of the first-run wizard: a colorful badge, title and text, plus the step's own controls (buttons, status,
 * a [PresetPicker]...).
 */
class WizardStep(
    val icon: ImageVector, val title: String, val colors: List<Color>, val body: String,
    val content: @Composable ColumnScope.() -> Unit = {},
)

/** First-run setup wizard: progress bar, one page per step, Back/Skip and Next/Start. */
@Composable
fun SetupWizard(steps: List<WizardStep>, onFinish: () -> Unit, message: String? = null, onStepChange: () -> Unit = {}) {
    var step by remember { mutableIntStateOf(0) }
    // Short screens (e.g. 640x480 handhelds): smaller badge and spacing so the step's content stays visible
    val compact = LocalConfiguration.current.screenHeightDp < 560
    val pad = if (compact) 12.dp else 24.dp
    Column(Modifier.fillMaxSize().background(palette.backgroundBrush).safeDrawingPadding().padding(horizontal = 24.dp, vertical = pad)) {
        LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp, Alignment.CenterVertically),
        ) {
            val s = steps[step]
            WizardPage(s.icon, s.title, s.colors, s.body, compact)
            s.content(this)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step--; onStepChange() }) { Text("Back") } else TextButton(onClick = onFinish) { Text("Skip") }
            Button(onClick = { if (step < steps.size - 1) { step++; onStepChange() } else onFinish() }) {
                Text(if (step < steps.size - 1) "Next" else "Start")
            }
        }
    }
}

/** Rounded gradient badge with a soft glow, title and text. */
@Composable
fun WizardPage(icon: ImageVector, title: String, colors: List<Color>, body: String, compact: Boolean = false) {
    val k = if (compact) 0.55f else 1f
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(132.dp * k).background(Brush.radialGradient(listOf(colors[0].copy(alpha = .35f), Color.Transparent)), CircleShape))
        Box(Modifier.size(96.dp * k).background(Brush.linearGradient(colors), RoundedCornerShape(30.dp * k)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(52.dp * k), tint = Color.White)
        }
    }
    Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.Bold)
    if (body.isNotBlank()) Text(body, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center, color = Color.White.copy(alpha = .75f))
}

/** "Done" line under a wizard step (e.g. the chosen folder). */
@Composable
fun WizardStatus(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A quality preset the user can pick (e.g. High / Mid / Low). */
class PresetOption(val label: String, val description: String)

/** List of selectable preset cards with radio buttons; [selected] is the index of the current one (or -1 for custom). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PresetPicker(options: List<PresetOption>, selected: Int, onSelect: (Int) -> Unit) {
    options.forEachIndexed { i, p ->
        val sel = i == selected
        // The selected (recommended) preset is scrolled into view on short screens
        val bring = remember { BringIntoViewRequester() }
        LaunchedEffect(sel) { if (sel) { kotlinx.coroutines.delay(100); bring.bringIntoView() } }
        OutlinedCard(onClick = { onSelect(i) }, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring),
            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = sel, onClick = { onSelect(i) })
                Column {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(p.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

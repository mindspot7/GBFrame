package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Blocking, theme-aware progress shown while an asynchronous core opens or copies a game. */
@Composable
fun LaunchLoadingOverlay(
    message: String = "The first opening of a large archive can take a few minutes. The screen will stay on while it loads.",
    extractionProgress: Float? = null,
    extractingArchive: Boolean = false,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }
    val content = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxSize().zIndex(100f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { }
            .onPreviewKeyEvent { true },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (extractingArchive) {
                val barModifier = Modifier.widthIn(min = 220.dp, max = 320.dp)
                if (extractionProgress == null) {
                    LinearProgressIndicator(color = palette.highlight, modifier = barModifier)
                } else {
                    LinearProgressIndicator(
                        progress = { extractionProgress.coerceIn(0f, 1f) },
                        color = palette.highlight,
                        modifier = barModifier,
                    )
                }
                KitText(
                    extractionProgress?.let { "${(it.coerceIn(0f, 1f) * 100).toInt()}%" } ?: "Extracting archive…",
                    color = content,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                CircularProgressIndicator(color = palette.highlight, strokeWidth = 4.dp, modifier = Modifier.size(54.dp))
                KitText("Opening game…", color = content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            KitText(
                message,
                color = content.copy(alpha = .78f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp).padding(horizontal = 20.dp),
            )
        }
    }
}

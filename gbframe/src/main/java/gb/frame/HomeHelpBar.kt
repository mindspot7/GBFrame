package gb.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Fixed bottom line of the library with the controller buttons, Nintendo-style colored glyphs. The bar is right-aligned
 * and lists the buttons from the right as A, Y, X, B (the same in every app); labels can be translated.
 */
@Composable
fun HomeHelpBar(play: String = "Play", options: String = "Game options", menu: String = "Menu", quit: String = "Quit") {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .28f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
    ) {
        HelpGlyph("B", PadColors.B, quit)
        HelpGlyph("X", PadColors.X, menu)
        HelpGlyph("Y", PadColors.Y, options)
        HelpGlyph("A", PadColors.A, play)
    }
}

@Composable
private fun HelpGlyph(letter: String, tint: Color, label: String) {
    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(tint), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
        }
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

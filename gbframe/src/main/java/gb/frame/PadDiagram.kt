package gb.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One button on a [PadLayout]. Position ([x], [y], centre) is a fraction of the diagram's width/height; [w] and [h] are
 * fractions of the diagram's height (so buttons keep their shape at any width) unless [wOfWidth] is set.
 */
data class PadButton(
    val id: String, val label: String? = null, val icon: ImageVector? = null,
    val x: Float, val y: Float, val w: Float, val h: Float, val wOfWidth: Boolean = false,
    val shape: Shape = CircleShape, val color: Color = Color(0xFF4A4468),
)

/** A console controller drawn for button mapping: its body and buttons. */
data class PadLayout(
    val buttons: List<PadButton>,
    val body: DrawScope.() -> Unit = PadBodies.Classic,
    val aspectRatio: Float = 2.1f,
    val artwork: (@Composable () -> Painter)? = null,
)

object PadBodies {
    /** Generic two-grip controller with shoulder bumpers and a D-pad well on the left. */
    val Classic: DrawScope.() -> Unit = {
        val cw = size.width; val ch = size.height
        val body = Path().apply {
            moveTo(cw * .20f, ch * .18f)
            lineTo(cw * .80f, ch * .18f)
            cubicTo(cw * .98f, ch * .18f, cw * 1.0f, ch * .70f, cw * .90f, ch * .90f)
            cubicTo(cw * .84f, ch * 1.0f, cw * .70f, ch * .96f, cw * .64f, ch * .80f)
            lineTo(cw * .36f, ch * .80f)
            cubicTo(cw * .30f, ch * .96f, cw * .16f, ch * 1.0f, cw * .10f, ch * .90f)
            cubicTo(cw * 0f, ch * .70f, cw * .02f, ch * .18f, cw * .20f, ch * .18f)
            close()
        }
        drawRoundRect(Color(0xFF3A3652), Offset(cw * .12f, ch * .06f), Size(cw * .22f, ch * .16f), CornerRadius(18f))
        drawRoundRect(Color(0xFF3A3652), Offset(cw * .66f, ch * .06f), Size(cw * .22f, ch * .16f), CornerRadius(18f))
        drawPath(body, Color(0xFF26223D))
        drawPath(body, Color.White.copy(alpha = .18f), style = Stroke(width = 3f))
        drawCircle(Color(0xFF1C1930), radius = ch * .26f, center = Offset(cw * .23f, ch * .50f))
    }
}

/**
 * Controller diagram for button mapping: tap (or select with the controller) a button to remap it; the [highlight]ed
 * button is drawn in the palette's highlight color.
 */
@Composable
fun PadDiagram(layout: PadLayout, highlight: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val artwork = layout.artwork
        if (artwork != null) Image(artwork(), null, Modifier.fillMaxSize())
        else Canvas(Modifier.fillMaxSize()) { layout.body(this) }
        layout.buttons.forEach { b ->
            val bw = if (b.wOfWidth) w * b.w else h * b.w
            val bh = h * b.h
            val sel = highlight == b.id
            Box(
                Modifier.offset(w * b.x - bw / 2, h * b.y - bh / 2).size(bw, bh).clip(b.shape)
                    .background(if (sel) p.highlight else b.color)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else Color.White.copy(alpha = .25f), b.shape)
                    .clickable { onSelect(b.id) },
                contentAlignment = Alignment.Center,
            ) {
                if (b.icon != null) Icon(b.icon, b.id, tint = if (sel) Color.Black else Color.White, modifier = Modifier.size(bh * .8f))
                else if (b.label != null) Text(b.label, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold,
                    fontSize = (bh.value * .42f).sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Rounded rectangle shape helper for pad buttons. */
fun padRect(radiusDp: Int = 6) = RoundedCornerShape(radiusDp.dp)

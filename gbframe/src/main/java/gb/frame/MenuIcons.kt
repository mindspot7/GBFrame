package gb.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Multicolor illustrated menu icons (drawn, no tile background). */
enum class MenuIcon { System, Video, Audio, Performance, Controls, Extras, About, Quit }

private val Outline = Color(0xFF1A1A22)

@Composable
fun MenuIconView(icon: MenuIcon, size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        when (icon) {
            MenuIcon.System -> system(u)
            MenuIcon.Video -> video(u)
            MenuIcon.Audio -> audio(u)
            MenuIcon.Performance -> performance(u)
            MenuIcon.Controls -> controls(u)
            MenuIcon.Extras -> extras(u)
            MenuIcon.About -> about(u)
            MenuIcon.Quit -> quit(u)
        }
    }
}

private fun DrawScope.p(x: Float, y: Float, u: Float) = Offset(x * u, y * u)
private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, u: Float, brush: Brush, stroke: Boolean = true) {
    drawRoundRect(brush, p(x, y, u), Size(w * u, h * u), CornerRadius(r * u))
    if (stroke) drawRoundRect(Outline, p(x, y, u), Size(w * u, h * u), CornerRadius(r * u), style = Stroke(1.2f * u))
}
private fun v(a: Color, b: Color) = Brush.verticalGradient(listOf(a, b))

private fun DrawScope.system(u: Float) {
    val ys = listOf(5f, 11f, 17f); val knob = listOf(15f, 7f, 12f)
    ys.forEachIndexed { i, y ->
        drawLine(Color(0xFFE8EAF2), p(2f, y, u), p(i.let { if (i == 2) 13f else 21f }, y, u), 2.2f * u, StrokeCap.Round)
        drawCircle(Outline, 2.6f * u, p(knob[i], y, u)); drawCircle(Color(0xFFDDE0EA), 1.7f * u, p(knob[i], y, u))
    }
    // gear
    val c = p(18.5f, 18.5f, u)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        drawLine(Color(0xFFB8BCC8), c, Offset(c.x + (4.8f * u * Math.cos(a)).toFloat(), c.y + (4.8f * u * Math.sin(a)).toFloat()), 2.2f * u)
    }
    drawCircle(Color(0xFFB8BCC8), 3.6f * u, c); drawCircle(Outline, 1.4f * u, c)
}

private fun DrawScope.video(u: Float) {
    rr(1.5f, 3f, 21f, 14.5f, 2f, u, v(Color(0xFF5A5F70), Color(0xFF2E3140)))
    rr(3.3f, 4.8f, 17.4f, 10.9f, 1f, u, v(Color(0xFF3D4258), Color(0xFF23263A)), false)
    val tri = Path().apply { moveTo(10f * u, 7f * u); lineTo(15.5f * u, 10.2f * u); lineTo(10f * u, 13.4f * u); close() }
    drawPath(tri, v(Color(0xFF7BE36B), Color(0xFF2FA84A)))
    rr(9f, 17.5f, 6f, 2.5f, 0.5f, u, SolidColor(Color(0xFF4A4E5E)), false)
    rr(6f, 19.8f, 12f, 2f, 1f, u, v(Color(0xFF6A6F80), Color(0xFF3A3D4C)))
}

private fun DrawScope.audio(u: Float) {
    val body = Path().apply { moveTo(3f * u, 9f * u); lineTo(7f * u, 9f * u); lineTo(12f * u, 4.5f * u); lineTo(12f * u, 19.5f * u); lineTo(7f * u, 15f * u); lineTo(3f * u, 15f * u); close() }
    drawPath(body, v(Color(0xFFFFD46B), Color(0xFFE8902E))); drawPath(body, Outline, style = Stroke(1.2f * u))
    listOf(4f to Color(0xFF7FE3F0), 7f to Color(0xFF4FA8FF)).forEach { (r, col) ->
        drawArc(col, -50f, 100f, false, Offset((12f - r) * u, (12f - r) * u), Size(2 * r * u, 2 * r * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.performance(u: Float) {
    drawCircle(v(Color(0xFF4A4E60), Color(0xFF262836)), 11f * u, p(12f, 13f, u))
    drawCircle(Outline, 11f * u, p(12f, 13f, u), style = Stroke(1.2f * u))
    val arc = Offset(4f * u, 5f * u); val sz = Size(16f * u, 16f * u)
    listOf(Color(0xFF3DD16B), Color(0xFFB5E04A), Color(0xFFFFC53D), Color(0xFFFF7A3D), Color(0xFFE5383B)).forEachIndexed { i, c ->
        drawArc(c, 180f + i * 36f, 34f, false, arc, sz, style = Stroke(2.6f * u))
    }
    drawLine(Color(0xFFF2F2F2), p(12f, 13f, u), p(17f, 8f, u), 1.8f * u, StrokeCap.Round)
    drawCircle(Color(0xFFDDDDDD), 1.7f * u, p(12f, 13f, u))
}

private fun DrawScope.controls(u: Float) {
    rr(2f, 15f, 20f, 7f, 2.5f, u, v(Color(0xFF6A6F82), Color(0xFF34374A)))
    drawCircle(Color(0xFF3DD16B), 1.3f * u, p(17.5f, 18.5f, u)); drawCircle(Color(0xFF4FA8FF), 1.3f * u, p(6.5f, 18.5f, u))
    drawLine(Color(0xFF2A2C38), p(12f, 16f, u), p(12f, 8f, u), 2.4f * u)
    drawLine(Color(0xFFBFC3D0), p(12f, 16f, u), p(12f, 8f, u), 1.1f * u)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A8A), Color(0xFFD62828)), p(10.5f, 4.5f, u), 6f * u), 4.2f * u, p(12f, 6f, u))
    drawCircle(Outline, 4.2f * u, p(12f, 6f, u), style = Stroke(1.1f * u))
    drawCircle(Color.White.copy(alpha = .6f), 1.1f * u, p(10.6f, 4.6f, u))
}

private fun DrawScope.extras(u: Float) {
    val cup = Path().apply { moveTo(6f * u, 3f * u); lineTo(18f * u, 3f * u); lineTo(17f * u, 10f * u); quadraticTo(12f * u, 16f * u, 7f * u, 10f * u); close() }
    drawArc(Color(0xFFE8A13A), 90f, 180f, false, p(2f, 4f, u), Size(6f * u, 6f * u), style = Stroke(1.8f * u))
    drawArc(Color(0xFFE8A13A), -90f, 180f, false, p(16f, 4f, u), Size(6f * u, 6f * u), style = Stroke(1.8f * u))
    drawPath(cup, v(Color(0xFFFFE27A), Color(0xFFE8A13A))); drawPath(cup, Outline, style = Stroke(1.2f * u))
    rr(10.5f, 13.5f, 3f, 4f, 0.5f, u, SolidColor(Color(0xFFD08A2A)), false)
    rr(6.5f, 17.5f, 11f, 4f, 1f, u, v(Color(0xFF8A5A3A), Color(0xFF5A3A24)))
}

private fun DrawScope.about(u: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF7FB2FF), Color(0xFF1E4FD0)), p(9f, 8f, u), 14f * u), 10.5f * u, p(12f, 12f, u))
    drawCircle(Outline, 10.5f * u, p(12f, 12f, u), style = Stroke(1.2f * u))
    drawCircle(Color.White, 1.6f * u, p(12f, 7f, u))
    drawLine(Color.White, p(12f, 10.5f, u), p(12f, 17.5f, u), 3f * u, StrokeCap.Round)
}

private fun DrawScope.quit(u: Float) {
    rr(9f, 2f, 12f, 20f, 1f, u, v(Color(0xFFE08A4A), Color(0xFFA0522D)))
    rr(10.8f, 3.8f, 8.4f, 16.4f, 0.5f, u, v(Color(0xFFC8703A), Color(0xFF8A4424)), false)
    drawCircle(Color(0xFFFFD46B), 1f * u, p(12.8f, 12f, u))
    drawLine(Color(0xFFE5383B), p(2f, 12f, u), p(9f, 12f, u), 2.6f * u, StrokeCap.Round)
    val head = Path().apply { moveTo(1.5f * u, 12f * u); lineTo(5f * u, 8.5f * u); lineTo(5f * u, 15.5f * u); close() }
    drawPath(head, SolidColor(Color(0xFFE5383B)))
}

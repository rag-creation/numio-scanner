package com.rr.numio.scanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.scanner.R

/** A sheet of paper with its top-right corner folded over. */
fun foldedShape(fold: Dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { fold.toPx() }
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - f, 0f)
            lineTo(size.width, f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Bars with a soft peak in the middle, like a folded edge. */
fun peakShape(peak: Dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val k = with(density) { peak.toPx() }
        val p = Path().apply {
            moveTo(0f, k)
            lineTo(size.width / 2f, 0f)
            lineTo(size.width, k)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

@Composable
fun FoldedPaper(
    modifier: Modifier = Modifier,
    fold: Dp = 22.dp,
    color: Color = LocalPaper.current.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val foldColor = LocalPaper.current.fold
    Box(
        modifier
            .clip(foldedShape(fold))
            .background(color)
            .drawWithContent {
                drawContent()
                val f = fold.toPx()
                val tri = Path().apply {
                    moveTo(size.width - f, 0f)
                    lineTo(size.width - f, f)
                    lineTo(size.width, f)
                    close()
                }
                drawPath(tri, foldColor)
            },
        content = content,
    )
}

/** Two flat paper tones meeting at a diagonal fold. */
fun Modifier.paperBackground(p: Paper) = drawBehind {
    drawRect(p.bgTop)
    val path = Path().apply {
        moveTo(0f, size.height * 0.42f)
        lineTo(size.width, size.height * 0.30f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, p.bgFold)
}

/** The official "Numio." wordmark: cream letters on dark screens, dark letters on light ones. */
@Composable
fun NumioWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(
            if (LocalPaper.current.dark) R.drawable.numio_wordmark_on_dark else R.drawable.numio_wordmark_on_light,
        ),
        contentDescription = "Numio",
        modifier = modifier,
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) p.accent else p.card)
            .border(1.dp, if (selected) p.accent else p.line, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) p.onAccent else p.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

enum class Glyph { Scan, Qr, Image, Flash, Rotate, Auto, Expand, Lock, Pdf, Text }

/** Small hand-made line icons, so we don't pull in the big icon library. */
@Composable
fun LineIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier.size(22.dp), stroke: Dp = 1.8.dp) {
    Canvas(modifier) {
        val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val u = size.minDimension / 24f
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        fun poly(vararg xy: Float, close: Boolean = false) {
            val path = Path()
            path.moveTo(xy[0] * u, xy[1] * u)
            var i = 2
            while (i < xy.size) { path.lineTo(xy[i] * u, xy[i + 1] * u); i += 2 }
            if (close) path.close()
            drawPath(path, tint, style = s)
        }
        fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float) =
            drawRect(tint, topLeft = pt(x, y), size = Size(w * u, h * u), style = s)

        when (glyph) {
            Glyph.Scan -> {
                poly(4f, 8f, 4f, 4f, 8f, 4f); poly(16f, 4f, 20f, 4f, 20f, 8f)
                poly(20f, 16f, 20f, 20f, 16f, 20f); poly(8f, 20f, 4f, 20f, 4f, 16f)
                poly(4f, 12f, 20f, 12f)
            }
            Glyph.Qr -> {
                rect(4f, 4f, 6f, 6f); rect(14f, 4f, 6f, 6f); rect(4f, 14f, 6f, 6f)
                poly(14f, 14f, 16f, 14f); poly(18f, 18f, 20f, 18f); poly(14f, 18f, 16f, 18f); poly(18f, 14f, 20f, 14f)
            }
            Glyph.Image -> {
                rect(3.5f, 5f, 17f, 14f)
                poly(3.5f, 16f, 9f, 11f, 14f, 16f); poly(13f, 14f, 16f, 11.5f, 20.5f, 15.5f)
                drawCircle(tint, radius = 1.6f * u, center = pt(15.5f, 8.8f), style = s)
            }
            Glyph.Flash -> poly(13f, 3f, 5f, 14f, 11f, 14f, 10f, 21f, 18f, 10f, 12f, 10f, close = true)
            Glyph.Rotate -> {
                drawArc(tint, -60f, 290f, false, topLeft = pt(5f, 5f), size = Size(14f * u, 14f * u), style = s)
                poly(15.5f, 3.5f, 18.5f, 6f, 15.5f, 8.5f)
            }
            Glyph.Auto -> {
                poly(4f, 8f, 4f, 4f, 8f, 4f); poly(16f, 4f, 20f, 4f, 20f, 8f)
                poly(20f, 16f, 20f, 20f, 16f, 20f); poly(8f, 20f, 4f, 20f, 4f, 16f)
                poly(12f, 7.5f, 13.2f, 10.8f, 16.5f, 12f, 13.2f, 13.2f, 12f, 16.5f, 10.8f, 13.2f, 7.5f, 12f, 10.8f, 10.8f, close = true)
            }
            Glyph.Expand -> { rect(4f, 4f, 16f, 16f); poly(8f, 12f, 16f, 12f); poly(12f, 8f, 12f, 16f) }
            Glyph.Lock -> {
                rect(5f, 11f, 14f, 10f)
                poly(8f, 11f, 8f, 8f, 9f, 5.5f, 12f, 4f, 15f, 5.5f, 16f, 8f, 16f, 11f)
            }
            Glyph.Pdf -> {
                poly(6f, 3f, 15f, 3f, 19f, 7f, 19f, 21f, 6f, 21f, close = true)
                poly(15f, 3f, 15f, 7f, 19f, 7f)
                poly(9f, 12f, 16f, 12f); poly(9f, 16f, 14f, 16f)
            }
            Glyph.Text -> { poly(5f, 6f, 5f, 4f, 19f, 4f, 19f, 6f); poly(12f, 4f, 12f, 20f); poly(9f, 20f, 15f, 20f) }
        }
    }
}

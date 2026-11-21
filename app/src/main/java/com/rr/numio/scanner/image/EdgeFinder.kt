package com.rr.numio.scanner.image

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the page in a photo, offline and without OpenCV.
 *
 * 1. Mark "paper-like" pixels: bright and not very colourful, at a few brightness levels.
 * 2. Shrink the mask a little so the page comes apart from bright bits of the table.
 * 3. Split the mask into separate blobs and score each one: big, rectangle-shaped,
 *    and not glued to the photo's edge = probably the page.
 * 4. The best blob's outermost points become the four corners.
 *
 * If nothing looks like a page, it falls back to a small margin and the user drags the corners.
 */
object EdgeFinder {

    fun inset(f: Float) = listOf(Offset(f, f), Offset(1 - f, f), Offset(1 - f, 1 - f), Offset(f, 1 - f))

    private const val SIZE = 320

    private class Candidate(val corners: List<Offset>, val score: Float)

    fun find(src: Bitmap): List<Offset> {
        val scale = SIZE.toFloat() / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val lum = IntArray(w * h)
        val chroma = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            lum[i] = (r * 299 + g * 587 + b * 114) / 1000
            chroma[i] = max(r, max(g, b)) - min(r, min(g, b))
        }
        val otsu = otsu(lum)

        var best: Candidate? = null
        for (t in listOf(otsu, otsu + 18, otsu + 36, otsu + 54)) {
            if (t >= 250) break
            val mask = BooleanArray(w * h) { lum[it] > t && chroma[it] < 70 }
            val eroded = erode(erode(mask, w, h), w, h)
            val c = bestBlob(eroded, w, h) ?: continue
            if (best == null || c.score > best.score) best = c
        }
        return best?.corners ?: inset(0.04f)
    }

    private fun otsu(lum: IntArray): Int {
        val hist = IntArray(256)
        lum.forEach { hist[it]++ }
        val total = lum.size
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var t = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return t
    }

    private fun erode(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(m.size)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            out[i] = m[i] && m[i - 1] && m[i + 1] && m[i - w] && m[i + w]
        }
        return out
    }

    /** Labels connected blobs and returns the one that looks most like a page. */
    private fun bestBlob(mask: BooleanArray, w: Int, h: Int): Candidate? {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val total = (w * h).toFloat()
        var best: Candidate? = null

        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var count = 0
            var edgeTouches = 0
            var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE
            var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
            var tl = 0; var br = 0; var tr = 0; var bl = 0

            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                count++
                if (x <= 2 || y <= 2 || x >= w - 3 || y >= h - 3) edgeTouches++
                val s = x + y
                val d = x - y
                if (s < minSum) { minSum = s; tl = i }
                if (s > maxSum) { maxSum = s; br = i }
                if (d > maxDiff) { maxDiff = d; tr = i }
                if (d < minDiff) { minDiff = d; bl = i }
                if (x > 0) push(i - 1, mask, seen, stack, sp)?.let { sp = it }
                if (x < w - 1) push(i + 1, mask, seen, stack, sp)?.let { sp = it }
                if (y > 0) push(i - w, mask, seen, stack, sp)?.let { sp = it }
                if (y < h - 1) push(i + w, mask, seen, stack, sp)?.let { sp = it }
            }

            val frac = count / total
            if (frac < 0.08f || frac > 0.95f) continue

            // Undo the 2-pixel erosion by pushing corners outwards a little.
            val cx = (tl % w + tr % w + br % w + bl % w) / 4f
            val cy = (tl / w + tr / w + br / w + bl / w) / 4f
            val corners = listOf(tl, tr, br, bl).map { i ->
                val x = i % w.toFloat()
                val y = (i / w).toFloat()
                val dx = x - cx
                val dy = y - cy
                val len = max(1f, kotlin.math.hypot(dx, dy))
                Offset(
                    ((x + dx / len * 2f) / (w - 1f)).coerceIn(0f, 1f),
                    ((y + dy / len * 2f) / (h - 1f)).coerceIn(0f, 1f),
                )
            }

            val quadArea = area(corners) * total
            if (quadArea <= 0f) continue
            // A real page fills its own four corners almost completely.
            val fill = (count / quadArea).coerceAtMost(1f)
            if (fill < 0.6f) continue
            val edgeShare = edgeTouches.toFloat() / max(1, count)
            val edgePenalty = if (edgeShare > 0.04f) 0.35f else 1f
            val score = frac * fill * fill * edgePenalty
            if (best == null || score > best.score) best = Candidate(corners, score)
        }
        return best
    }

    private fun push(i: Int, mask: BooleanArray, seen: BooleanArray, stack: IntArray, sp: Int): Int? {
        if (!mask[i] || seen[i]) return null
        seen[i] = true
        stack[sp] = i
        return sp + 1
    }

    private fun area(q: List<Offset>): Float {
        var a = 0f
        for (i in q.indices) {
            val p = q[i]; val n = q[(i + 1) % q.size]
            a += p.x * n.y - n.x * p.y
        }
        return abs(a) / 2f
    }
}

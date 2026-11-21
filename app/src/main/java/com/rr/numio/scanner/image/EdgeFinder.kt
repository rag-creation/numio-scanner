package com.rr.numio.scanner.image

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds the page in a photo, offline and without OpenCV.
 * Splits the picture into "paper" and "background" by brightness (Otsu),
 * then takes the outermost paper points as the four corners.
 * Works best with a light page on a darker surface; otherwise falls back
 * to a small margin and the user drags the corners.
 */
object EdgeFinder {

    fun inset(f: Float) = listOf(Offset(f, f), Offset(1 - f, f), Offset(1 - f, 1 - f), Offset(f, 1 - f))

    fun find(src: Bitmap): List<Offset> {
        val scale = 256f / max(src.width, src.height)
        val w = max(8, (src.width * scale).roundToInt())
        val h = max(8, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val lum = IntArray(w * h) { i ->
            val c = px[i]
            ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }

        // Otsu threshold
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

        var count = 0
        var tl = Offset.Zero; var tr = Offset.Zero; var br = Offset.Zero; var bl = Offset.Zero
        var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE
        var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            // Only solid paper pixels (all four neighbours bright too) to ignore specks.
            if (lum[i] > t && lum[i - 1] > t && lum[i + 1] > t && lum[i - w] > t && lum[i + w] > t) {
                count++
                val s = x + y
                val d = x - y
                val p = Offset(x / (w - 1f), y / (h - 1f))
                if (s < minSum) { minSum = s; tl = p }
                if (s > maxSum) { maxSum = s; br = p }
                if (d > maxDiff) { maxDiff = d; tr = p }
                if (d < minDiff) { minDiff = d; bl = p }
            }
        }

        val frac = count.toFloat() / (w * h)
        if (frac < 0.12f || frac > 0.92f) return inset(0.04f)
        val quad = listOf(tl, tr, br, bl)
        if (area(quad) < 0.12f) return inset(0.04f)
        return quad
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

package com.rr.numio.scanner.image

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds a page by its four straight edges (how real scanner apps do it), offline.
 *
 * 1. Find edges: places where colour changes sharply (all three colour channels,
 *    so white paper on orange wood is found even in dim light).
 * 2. Hough transform: every edge pixel votes for the straight lines it could be on.
 * 3. Try pairs of "across" lines with pairs of "up-down" lines; keep the four-sided
 *    shape whose sides really follow edges in the photo, preferring bigger pages.
 */
internal object LineFinder {

    private const val SIZE = 256
    private const val THETAS = 180
    private const val MAX_LINES = 24
    private const val MIN_SIDE_SUPPORT = 0.35f

    private class Line(val r: Float, val t: Float)

    fun find(src: Bitmap): List<Offset>? {
        val scale = SIZE.toFloat() / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        // Channels, lightly blurred.
        val ch = Array(3) { c -> FloatArray(w * h) { i -> (px[i] shr (16 - 8 * c) and 0xFF).toFloat() } }
        for (c in 0..2) ch[c] = blur(ch[c], w, h)

        // Sobel on each channel; keep the strongest.
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val mag = FloatArray(w * h)
        for (c in 0..2) {
            val a = ch[c]
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val sx = (a[i - w + 1] + 2 * a[i + 1] + a[i + w + 1]) - (a[i - w - 1] + 2 * a[i - 1] + a[i + w - 1])
                val sy = (a[i + w - 1] + 2 * a[i + w] + a[i + w + 1]) - (a[i - w - 1] + 2 * a[i - w] + a[i - w + 1])
                val m = hypot(sx, sy)
                if (m > mag[i]) { mag[i] = m; gx[i] = sx; gy[i] = sy }
            }
        }
        val sorted = mag.copyOf().also { it.sort() }
        val thr = max(sorted[(sorted.size * 0.88f).toInt().coerceAtMost(sorted.size - 1)], 40f)
        val edge = BooleanArray(w * h) { mag[it] > thr }

        // Hough voting, guided by the edge direction.
        val diag = hypot(w.toFloat(), h.toFloat()).toInt() + 1
        val rows = 2 * diag + 1
        val acc = IntArray(rows * THETAS)
        val cosT = FloatArray(THETAS) { cos(it * PI / THETAS).toFloat() }
        val sinT = FloatArray(THETAS) { sin(it * PI / THETAS).toFloat() }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!edge[i]) continue
            var g = atan2(gy[i], gx[i]).toDouble() % PI
            if (g < 0) g += PI
            val ti = (g / PI * THETAS).roundToInt() % THETAS
            for (d in -3..3) {
                val t = (ti + d + THETAS) % THETAS
                val r = (x * cosT[t] + y * sinT[t]).roundToInt()
                acc[(r + diag) * THETAS + t]++
            }
        }

        // Strongest lines, with neighbours suppressed.
        val lines = ArrayList<Line>()
        val minVotes = 0.15f * min(w, h)
        repeat(MAX_LINES) {
            var bi = 0
            for (k in acc.indices) if (acc[k] > acc[bi]) bi = k
            if (acc[bi] < minVotes) return@repeat
            val r0 = bi / THETAS
            val t0 = bi % THETAS
            lines += Line((r0 - diag).toFloat(), (t0 * PI / THETAS).toFloat())
            for (dt in -8..8) {
                val tt = (t0 + dt + THETAS) % THETAS
                for (rr in max(0, r0 - 10)..min(rows - 1, r0 + 10)) acc[rr * THETAS + tt] = 0
            }
        }

        val upDown = lines.filter { abs(sin(it.t)) < 0.5f }
        val across = lines.filter { abs(sin(it.t)) >= 0.5f }
        var best: List<Offset>? = null
        var bestScore = 0f

        for (i in across.indices) for (j in i + 1 until across.size)
            for (k in upDown.indices) for (l in k + 1 until upDown.size) {
                val raw = listOf(
                    cross(across[i], upDown[k]), cross(across[i], upDown[l]),
                    cross(across[j], upDown[l]), cross(across[j], upDown[k]),
                )
                if (raw.any { it == null }) continue
                val pts = raw.filterNotNull()
                val m = 3f
                if (pts.any { it.x < -m || it.x > w - 1 + m || it.y < -m || it.y > h - 1 + m }) continue
                val cx = pts.sumOf { it.x.toDouble() }.toFloat() / 4f
                val cy = pts.sumOf { it.y.toDouble() }.toFloat() / 4f
                val quad = pts.sortedBy { atan2(it.y - cy, it.x - cx) } // TL, TR, BR, BL
                val area = area(quad) / (w * h)
                if (area < 0.08f || area > 0.97f) continue
                var sum = 0f
                var minSup = 1f
                for (s in 0..3) {
                    val sup = support(quad[s], quad[(s + 1) % 4], edge, w, h)
                    sum += sup; minSup = min(minSup, sup)
                }
                if (minSup < MIN_SIDE_SUPPORT) continue
                val avg = sum / 4f
                val score = avg * avg * sqrt(area)
                if (score > bestScore) {
                    bestScore = score
                    best = quad.map { Offset((it.x / (w - 1)).coerceIn(0f, 1f), (it.y / (h - 1)).coerceIn(0f, 1f)) }
                }
            }
        return best
    }

    private fun cross(a: Line, b: Line): Offset? {
        val d = cos(a.t) * sin(b.t) - sin(a.t) * cos(b.t)
        if (abs(d) < 1e-6f) return null
        val x = (a.r * sin(b.t) - b.r * sin(a.t)) / d
        val y = (b.r * cos(a.t) - a.r * cos(b.t)) / d
        return Offset(x, y)
    }

    /** Share of the side that runs along real edges in the photo. */
    private fun support(p: Offset, q: Offset, edge: BooleanArray, w: Int, h: Int): Float {
        val n = max(2, hypot(q.x - p.x, q.y - p.y).toInt())
        var hit = 0
        for (i in 0 until n) {
            val x = (p.x + (q.x - p.x) * i / n).roundToInt()
            val y = (p.y + (q.y - p.y) * i / n).roundToInt()
            var ok = false
            loop@ for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && edge[yy * w + xx]) { ok = true; break@loop }
            }
            if (ok) hit++
        }
        return hit.toFloat() / n
    }

    private fun area(q: List<Offset>): Float {
        var a = 0f
        for (i in q.indices) {
            val p = q[i]; val n = q[(i + 1) % q.size]
            a += p.x * n.y - n.x * p.y
        }
        return abs(a) / 2f
    }

    private fun blur(a: FloatArray, w: Int, h: Int): FloatArray {
        val t = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            val l = a[y * w + max(0, x - 1)]; val r = a[y * w + min(w - 1, x + 1)]
            t[y * w + x] = (l + 2 * a[y * w + x] + r) / 4f
        }
        val o = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            val u = t[max(0, y - 1) * w + x]; val d = t[min(h - 1, y + 1) * w + x]
            o[y * w + x] = (u + 2 * t[y * w + x] + d) / 4f
        }
        return o
    }
}

package com.rr.numio.scanner.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Filters. "Paper" ones flatten shadows first (good for documents); the rest are plain photo filters. */
enum class Look(val label: String, val paper: Boolean) {
    Original("Original", false),
    CleanPaper("Clean paper", true),
    SharpText("Sharp text", true),
    BlackWhite("B&W", true),
    ColourPop("Colour pop", true),
    Greyscale("Greyscale", false),
    Warm("Warm", false),
    Cool("Cool", false),
    Sepia("Sepia", false),
    Night("Night", true),
}

/** Fine tuning on top of a filter. 0 = untouched. Both run from -1 to 1. */
data class Adjust(val brightness: Float = 0f, val contrast: Float = 0f) {
    val isNone get() = abs(brightness) < 0.01f && abs(contrast) < 0.01f
}

object ImageTools {

    val FULL = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

    /** Decodes a photo, turned the right way up, no bigger than [maxDim] on its long side. */
    fun decodeFile(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= maxDim) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("Could not read this image")

        val currentLong = max(bmp.width, bmp.height)
        if (currentLong > maxDim) {
            val s = maxDim.toFloat() / currentLong
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
        }

        val degrees = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
        return rotate(bmp, degrees)
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** The whole edit: straighten the page, turn it, then filter and fine-tune. */
    fun process(src: Bitmap, corners: List<Offset>, rotation: Int, look: Look, adjust: Adjust): Bitmap {
        val warped = warp(src, corners)
        val turned = rotate(warped, rotation)
        val filtered = applyLook(turned, look)
        return applyAdjust(filtered, adjust)
    }

    private fun isFull(c: List<Offset>) = c.zip(FULL).all { (a, b) -> abs(a.x - b.x) < 0.002f && abs(a.y - b.y) < 0.002f }

    /** Perspective-correct the page. Corners are TL, TR, BR, BL in 0..1 of the image. */
    fun warp(src: Bitmap, corners: List<Offset>): Bitmap {
        if (corners.size != 4 || isFull(corners)) return src
        val p = corners.map { Offset(it.x * src.width, it.y * src.height) }
        val (tl, tr, br, bl) = p
        fun d(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
        var w = max(d(tl, tr), d(bl, br))
        var h = max(d(tl, bl), d(tr, br))
        val cap = 3000f
        if (max(w, h) > cap) {
            val s = cap / max(w, h); w *= s; h *= s
        }
        val ow = max(16, w.roundToInt())
        val oh = max(16, h.roundToInt())
        val m = Matrix()
        val ok = m.setPolyToPoly(
            floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y), 0,
            floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat()), 0,
            4,
        )
        if (!ok) return src
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun applyLook(src: Bitmap, look: Look): Bitmap {
        if (look == Look.Original) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val bg = if (look.paper) backgroundMap(src) else null

        for (i in px.indices) {
            val c = px[i]
            var r = (c shr 16 and 0xFF).toFloat()
            var g = (c shr 8 and 0xFF).toFloat()
            var b = (c and 0xFF).toFloat()

            if (bg != null) {
                // Divide by the paper's own brightness: shadows and uneven light disappear.
                val f = 255f / max(bg[i], 24)
                r *= f; g *= f; b *= f
            }
            val l = 0.299f * r + 0.587f * g + 0.114f * b

            when (look) {
                Look.CleanPaper -> {
                    r = levels(sat(r, l, 0.9f), 25f, 238f)
                    g = levels(sat(g, l, 0.9f), 25f, 238f)
                    b = levels(sat(b, l, 0.9f), 25f, 238f)
                }
                Look.ColourPop -> {
                    r = levels(sat(r, l, 1.4f), 25f, 238f)
                    g = levels(sat(g, l, 1.4f), 25f, 238f)
                    b = levels(sat(b, l, 1.4f), 25f, 238f)
                }
                Look.SharpText -> {
                    val v = levels(l, 70f, 225f); r = v; g = v; b = v
                }
                Look.BlackWhite -> {
                    val v = if (l < 172f) 0f else 255f; r = v; g = v; b = v
                }
                Look.Night -> {
                    // Dark page, light text: easy reading at night.
                    val v = 255f - levels(l, 40f, 230f)
                    r = v * 0.95f; g = v * 0.92f; b = v * 0.85f
                }
                Look.Greyscale -> { r = l; g = l; b = l }
                Look.Warm -> { r *= 1.08f; g *= 1.02f; b *= 0.88f }
                Look.Cool -> { r *= 0.9f; g *= 1.0f; b *= 1.1f }
                Look.Sepia -> {
                    val nr = 0.393f * r + 0.769f * g + 0.189f * b
                    val ng = 0.349f * r + 0.686f * g + 0.168f * b
                    val nb = 0.272f * r + 0.534f * g + 0.131f * b
                    r = nr; g = ng; b = nb
                }
                Look.Original -> Unit
            }
            px[i] = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun applyAdjust(src: Bitmap, adjust: Adjust): Bitmap {
        if (adjust.isNone) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val add = adjust.brightness * 70f
        val k = 1f + adjust.contrast * 0.6f
        val lut = IntArray(256) { v -> clamp((v - 128f) * k + 128f + add) }
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0xFF shl 24) or (lut[c shr 16 and 0xFF] shl 16) or (lut[c shr 8 and 0xFF] shl 8) or lut[c and 0xFF]
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** A blurred "paper only" brightness map: small max-filtered copy scaled back up. */
    private fun backgroundMap(src: Bitmap): IntArray {
        val sw = max(4, src.width / 28)
        val sh = max(4, src.height / 28)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        var lum = IntArray(sw * sh) { i ->
            val c = sp[i]
            ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        repeat(2) { lum = maxFilter(lum, sw, sh) }
        val grey = IntArray(sw * sh) { i -> val v = lum[i]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val greyBmp = Bitmap.createBitmap(grey, sw, sh, Bitmap.Config.ARGB_8888)
        val up = Bitmap.createScaledBitmap(greyBmp, src.width, src.height, true)
        val out = IntArray(src.width * src.height)
        up.getPixels(out, 0, src.width, 0, 0, src.width, src.height)
        for (i in out.indices) out[i] = out[i] shr 16 and 0xFF
        if (up !== greyBmp) up.recycle()
        greyBmp.recycle()
        if (small !== src) small.recycle()
        return out
    }

    private fun maxFilter(a: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = min(w - 1, max(0, x + dx))
                val yy = min(h - 1, max(0, y + dy))
                m = max(m, a[yy * w + xx])
            }
            out[y * w + x] = m
        }
        return out
    }

    private fun sat(c: Float, l: Float, s: Float) = l + (c - l) * s
    private fun levels(v: Float, black: Float, white: Float) = (v - black) * 255f / (white - black)
    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}

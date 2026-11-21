package com.rr.numio.scanner.image

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.rr.numio.scanner.data.Scan
import java.io.OutputStream
import kotlin.math.roundToInt

/** Builds a PDF with Android's own PDF maker. One scan page = one PDF page, 595 pt wide (A4 width). */
object PdfMaker {

    fun write(scan: Scan, out: OutputStream) {
        val doc = PdfDocument()
        try {
            for (i in 0 until scan.pageCount) {
                // ~200 dpi at A4 width: sharp text, sensible file size.
                val bmp = ImageTools.decodeFile(scan.page(i), 1700)
                val pw = 595
                val ph = (pw.toFloat() * bmp.height / bmp.width).roundToInt().coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(pw, ph, i + 1).create()
                val page = doc.startPage(info)
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, pw, ph), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}

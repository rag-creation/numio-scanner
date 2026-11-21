package com.rr.numio.scanner.image

import android.graphics.BitmapFactory
import com.rr.numio.scanner.data.Scan
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a PDF by placing each saved page JPEG straight inside it.
 * No re-compression, so pages stay exactly as sharp as the scan and the file
 * stays small (Android's own PDF maker stores pages uncompressed, ~5x bigger).
 * One scan page = one PDF page, 595 pt wide (A4 width).
 */
object PdfMaker {

    fun write(scan: Scan, out: OutputStream) {
        val w = Counter(BufferedOutputStream(out, 64 * 1024))
        val n = scan.pageCount
        // Objects: 1 catalog, 2 page list, then per page: page, contents, image.
        val total = 2 + 3 * n
        val offsets = LongArray(total + 1)

        fun begin(num: Int) {
            offsets[num] = w.count
            w.ascii("$num 0 obj\n")
        }

        w.ascii("%PDF-1.4\n")
        w.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        begin(1)
        w.ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        begin(2)
        val kids = (0 until n).joinToString(" ") { "${3 + 3 * it} 0 R" }
        w.ascii("<< /Type /Pages /Count $n /Kids [$kids] >>\nendobj\n")

        for (i in 0 until n) {
            val file = scan.page(i)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val iw = bounds.outWidth
            val ih = bounds.outHeight
            require(iw > 0 && ih > 0) { "Page ${i + 1} can't be read" }
            val pw = 595f
            val ph = pw * ih / iw
            val pageNum = 3 + 3 * i
            val contentNum = pageNum + 1
            val imageNum = pageNum + 2

            begin(pageNum)
            w.ascii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(pw)} ${f(ph)}] " +
                    "/Resources << /XObject << /Im0 $imageNum 0 R >> >> /Contents $contentNum 0 R >>\nendobj\n",
            )

            val content = "q ${f(pw)} 0 0 ${f(ph)} 0 0 cm /Im0 Do Q\n"
            begin(contentNum)
            w.ascii("<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")

            val length = file.length()
            begin(imageNum)
            w.ascii(
                "<< /Type /XObject /Subtype /Image /Width $iw /Height $ih /ColorSpace /DeviceRGB " +
                    "/BitsPerComponent 8 /Filter /DCTDecode /Length $length >>\nstream\n",
            )
            file.inputStream().use { it.copyTo(w) }
            w.ascii("\nendstream\nendobj\n")
        }

        val xref = w.count
        w.ascii("xref\n0 ${total + 1}\n0000000000 65535 f \n")
        for (num in 1..total) w.ascii(String.format(Locale.US, "%010d 00000 n \n", offsets[num]))
        w.ascii("trailer\n<< /Size ${total + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        w.flush()
    }

    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    /** Counts bytes written, for the PDF's table of object positions. */
    private class Counter(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b); count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len); count += len
        }

        override fun flush() = target.flush()

        fun ascii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }
}

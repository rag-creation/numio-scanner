package com.rr.numio.scanner.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class Scan(
    val id: String,
    val name: String,
    val created: Long,
    val pageCount: Int,
    val dir: File,
) {
    fun page(index: Int) = File(dir, "page_$index.jpg")
}

/**
 * Scans live only in the app's private storage:
 *   files/scans/<id>/scan.json + page_0.jpg, page_1.jpg, ...
 * A scan is written to a hidden temp folder first and renamed when complete,
 * so a crash mid-save never leaves a half-written scan in the list.
 */
class ScanStore(context: Context) {

    private val root = File(context.filesDir, "scans").apply { mkdirs() }

    init {
        // Leftovers from an interrupted save.
        root.listFiles()?.filter { it.name.startsWith(".") }?.forEach { it.deleteRecursively() }
    }

    fun list(): List<Scan> =
        root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.created }
            ?: emptyList()

    fun get(id: String): Scan? = File(root, id).takeIf { it.isDirectory }?.let { read(it) }

    fun save(name: String, pageCount: Int, page: (Int) -> Bitmap): Scan {
        val created = System.currentTimeMillis()
        val id = "scan_$created"
        val tmp = File(root, ".$id").apply { deleteRecursively(); mkdirs() }
        try {
            for (i in 0 until pageCount) {
                val bmp = page(i)
                FileOutputStream(File(tmp, "page_$i.jpg")).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bmp.recycle()
            }
            writeMeta(tmp, name, created, pageCount)
            val dir = File(root, id)
            if (!tmp.renameTo(dir)) throw IllegalStateException("Could not save the scan")
            return Scan(id, name, created, pageCount, dir)
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }

    fun rename(scan: Scan, newName: String): Scan {
        val clean = newName.trim().ifEmpty { scan.name }
        writeMeta(scan.dir, clean, scan.created, scan.pageCount)
        return scan.copy(name = clean)
    }

    fun delete(scan: Scan) {
        scan.dir.deleteRecursively()
    }

    private fun read(dir: File): Scan? = try {
        val json = JSONObject(File(dir, "scan.json").readText())
        Scan(
            id = dir.name,
            name = json.getString("name"),
            created = json.getLong("created"),
            pageCount = json.getInt("pages"),
            dir = dir,
        )
    } catch (e: Exception) {
        null
    }

    private fun writeMeta(dir: File, name: String, created: Long, pages: Int) {
        val json = JSONObject()
            .put("version", 1)
            .put("name", name)
            .put("created", created)
            .put("pages", pages)
        val tmp = File(dir, "scan.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(File(dir, "scan.json"))) {
            tmp.delete()
            throw IllegalStateException("Could not write scan details")
        }
    }
}

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
    /** Changes whenever the scan is changed, so pictures are re-read. */
    val rev: Long = 0L,
) {
    fun page(index: Int) = File(dir, "page_$index.jpg")
}

/**
 * Scans live only in the app's private storage:
 *   files/scans/<id>/scan.json + page_0.jpg, page_1.jpg, ...
 *
 * Nothing is ever changed in place. A new or changed scan is written to a hidden
 * folder first and swapped in only when complete:
 *   save:   .<id>       → <id>
 *   change: .new_<id>   → <id>  (old copy kept as .old_<id> until the swap is done)
 * If the phone dies halfway, [recover] finishes or undoes the swap on next start,
 * so a scan is always either fully old or fully new, never half-written.
 */
class ScanStore(context: Context) {

    private val root = File(context.filesDir, "scans").apply { mkdirs() }

    init {
        recover()
    }

    private fun recover() {
        val hidden = root.listFiles()?.filter { it.name.startsWith(".") } ?: return
        // 1. Interrupted page changes.
        for (old in hidden.filter { it.name.startsWith(".old_") }) {
            val id = old.name.removePrefix(".old_")
            val live = File(root, id)
            val fresh = File(root, ".new_$id")
            when {
                live.exists() -> old.deleteRecursively() // swap finished, only clean-up was missing
                File(fresh, "scan.json").exists() && fresh.renameTo(live) -> old.deleteRecursively()
                else -> old.renameTo(live) // put the old scan back
            }
        }
        // 2. Anything else hidden is an unfinished write that never went live.
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
            for (i in 0 until pageCount) writePage(File(tmp, "page_$i.jpg"), page(i))
            writeMeta(tmp, name, created, pageCount)
            val dir = File(root, id)
            if (!tmp.renameTo(dir)) throw IllegalStateException("Could not save the scan")
            return read(dir) ?: Scan(id, name, created, pageCount, dir)
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }

    /**
     * Rebuilds a scan's pages: [keep] are existing page numbers in their new order,
     * then [extraCount] new pages from [extra] are added at the end.
     */
    fun changePages(
        scan: Scan,
        keep: List<Int>,
        extraCount: Int = 0,
        extra: ((Int) -> Bitmap)? = null,
    ): Scan {
        val total = keep.size + extraCount
        require(total > 0) { "A scan needs at least one page" }
        val fresh = File(root, ".new_${scan.id}").apply { deleteRecursively(); mkdirs() }
        try {
            keep.forEachIndexed { i, from -> scan.page(from).copyTo(File(fresh, "page_$i.jpg")) }
            for (j in 0 until extraCount) {
                writePage(File(fresh, "page_${keep.size + j}.jpg"), extra!!(j))
            }
            writeMeta(fresh, scan.name, scan.created, total)
        } catch (e: Throwable) {
            fresh.deleteRecursively()
            throw e
        }
        // Swap: live → .old, .new → live, then drop .old.
        val old = File(root, ".old_${scan.id}")
        old.deleteRecursively()
        if (!scan.dir.renameTo(old)) {
            fresh.deleteRecursively()
            throw IllegalStateException("Could not update the scan")
        }
        if (!fresh.renameTo(scan.dir)) {
            old.renameTo(scan.dir)
            fresh.deleteRecursively()
            throw IllegalStateException("Could not update the scan")
        }
        old.deleteRecursively()
        return read(scan.dir) ?: scan.copy(pageCount = total)
    }

    fun rename(scan: Scan, newName: String): Scan {
        val clean = newName.trim().ifEmpty { scan.name }
        writeMeta(scan.dir, clean, scan.created, scan.pageCount)
        return read(scan.dir) ?: scan.copy(name = clean)
    }

    fun delete(scan: Scan) {
        scan.dir.deleteRecursively()
    }

    private fun writePage(file: File, bmp: Bitmap) {
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bmp.recycle()
    }

    private fun read(dir: File): Scan? = try {
        val meta = File(dir, "scan.json")
        val json = JSONObject(meta.readText())
        Scan(
            id = dir.name,
            name = json.getString("name"),
            created = json.getLong("created"),
            pageCount = json.getInt("pages"),
            dir = dir,
            rev = json.optLong("changed", json.getLong("created")),
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
            .put("changed", System.currentTimeMillis())
        val tmp = File(dir, "scan.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(File(dir, "scan.json"))) {
            tmp.delete()
            throw IllegalStateException("Could not write scan details")
        }
    }
}

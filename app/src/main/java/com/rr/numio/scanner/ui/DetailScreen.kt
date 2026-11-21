package com.rr.numio.scanner.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rr.numio.scanner.data.Scan
import com.rr.numio.scanner.data.ScanStore
import com.rr.numio.scanner.image.ImageTools
import com.rr.numio.scanner.image.PdfMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun DetailScreen(
    store: ScanStore,
    start: Scan,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val p = LocalPaper.current
    SystemBarIcons(lightIcons = p.dark)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scan by remember { mutableStateOf(start) }
    var busy by remember { mutableStateOf(false) }
    var askRename by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    val fileName = safeName(scan.name) + ".pdf"

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { PdfMaker.write(scan, it) }
                        ?: error("no stream")
                }.isSuccess
            }
            busy = false
            Toast.makeText(ctx, if (ok) "PDF saved" else "Couldn't save the PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePdf() {
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
                    File(dir, fileName).also { f -> FileOutputStream(f).use { PdfMaker.write(scan, it) } }
                }.getOrNull()
            }
            busy = false
            if (file == null) {
                Toast.makeText(ctx, "Couldn't make the PDF", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share PDF"))
        }
    }

    Box(Modifier.fillMaxSize().paperBackground(p)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = p.ink) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { askRename = true }) { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = p.ink) }
                IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete scan", tint = p.ink) }
            }
            Text(
                scan.name,
                fontFamily = Display, fontSize = 28.sp, color = p.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Text(
                if (scan.pageCount == 1) "1 page" else "${scan.pageCount} pages",
                fontSize = 13.sp, color = p.sub,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(scan.pageCount) { i -> PageView(scan, i) }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(peakShape(16.dp))
                .background(p.bar)
                .navigationBarsPadding()
                .height(96.dp)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF4A463F), RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy, role = Role.Button) { saveLauncher.launch(fileName) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Glyph.Pdf, p.barInk, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save PDF", color = p.barInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(NumioAmber)
                    .clickable(enabled = !busy, role = Role.Button) { sharePdf() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Share PDF", color = Color(0xFF1A1400), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NumioAmber)
            }
        }
    }

    if (askRename) {
        var name by remember { mutableStateOf(scan.name) }
        AlertDialog(
            onDismissRequest = { askRename = false },
            title = { Text("Rename", fontFamily = Display) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    askRename = false
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) { runCatching { store.rename(scan, name) }.getOrNull() }
                        if (updated != null) { scan = updated; onChanged() }
                    }
                }) { Text("Save", color = p.accentInk) }
            },
            dismissButton = { TextButton(onClick = { askRename = false }) { Text("Cancel", color = p.sub) } },
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete this scan?", fontFamily = Display) },
            text = { Text("\"${scan.name}\" will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(scan) }
                        onChanged()
                        onBack()
                    }
                }) { Text("Delete", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel", color = p.accentInk) } },
        )
    }
}

@Composable
private fun PageView(scan: Scan, index: Int) {
    val p = LocalPaper.current
    val bmp by produceState<Bitmap?>(null, scan.id, index) {
        value = withContext(Dispatchers.IO) { runCatching { ImageTools.decodeFile(scan.page(index), 1400) }.getOrNull() }
    }
    val b = bmp
    if (b == null) {
        FoldedPaper(Modifier.fillMaxWidth().aspectRatio(0.72f), fold = 28.dp) {
            CircularProgressIndicator(color = p.accent, modifier = Modifier.align(Alignment.Center))
        }
    } else {
        FoldedPaper(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height), fold = 28.dp) {
            Image(b.asImageBitmap(), contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize())
        }
    }
}

private fun safeName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().ifEmpty { "Scan" }.take(80)

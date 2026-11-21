package com.rr.numio.scanner.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.scanner.data.Scan
import com.rr.numio.scanner.data.ScanStore
import com.rr.numio.scanner.image.Adjust
import com.rr.numio.scanner.image.EdgeFinder
import com.rr.numio.scanner.image.ImageTools
import com.rr.numio.scanner.image.Look
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** One page waiting to be saved. The original photo stays untouched; edits are just settings. */
class PageDraft(val file: File) {
    var corners by mutableStateOf<List<Offset>?>(null)
    var look by mutableStateOf(Look.CleanPaper)
    var adjust by mutableStateOf(Adjust())
    var rotation by mutableIntStateOf(0)
}

private enum class EditTab(val label: String) { Edges("Edges"), Filters("Filters"), Adjust("Adjust") }

@Composable
fun EditorScreen(
    store: ScanStore,
    draft: SnapshotStateList<PageDraft>,
    onAddPage: () -> Unit,
    onSaved: (Scan) -> Unit,
    onDiscard: () -> Unit,
) {
    val p = LocalPaper.current
    SystemBarIcons(lightIcons = p.dark)
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    if (draft.isEmpty()) return
    val page = draft[index.coerceIn(0, draft.lastIndex)]
    if (index > draft.lastIndex) index = draft.lastIndex

    var tab by remember { mutableStateOf(EditTab.Filters) }
    var askDiscard by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = !saving) { askDiscard = true }

    val base by produceState<Bitmap?>(null, page) {
        value = null
        val bmp = withContext(Dispatchers.Default) { runCatching { ImageTools.decodeFile(page.file, 1200) }.getOrNull() }
        if (bmp != null && page.corners == null) {
            page.corners = withContext(Dispatchers.Default) { EdgeFinder.find(bmp) }
        }
        value = bmp
    }
    val preview by produceState<Bitmap?>(null, base, page.corners, page.rotation, page.look, page.adjust) {
        val b = base ?: return@produceState
        delay(90) // let sliders settle
        value = withContext(Dispatchers.Default) {
            ImageTools.process(b, page.corners ?: ImageTools.FULL, page.rotation, page.look, page.adjust)
        }
    }

    Box(Modifier.fillMaxSize().paperBackground(p)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { askDiscard = true }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = p.ink)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous page", tint = if (index > 0) p.ink else p.line)
                }
                Text("Page ${index + 1} of ${draft.size}", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = { if (index < draft.lastIndex) index++ }, enabled = index < draft.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next page", tint = if (index < draft.lastIndex) p.ink else p.line)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (draft.size == 1) askDiscard = true else draft.removeAt(index)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove this page", tint = p.ink)
                }
            }

            // Tabs
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                EditTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (on) p.ink else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = t },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.label, color = if (on) p.bgTop else p.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Page
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val b = base
                if (b == null) {
                    CircularProgressIndicator(color = p.accent)
                } else if (tab == EditTab.Edges) {
                    CornerEditor(
                        image = b,
                        corners = page.corners ?: ImageTools.FULL,
                        onChange = { page.corners = it },
                    )
                } else {
                    val pv = preview
                    if (pv != null) {
                        FoldedPaper(
                            Modifier.aspectRatio(pv.width.toFloat() / pv.height, matchHeightConstraintsFirst = pv.height > pv.width),
                            fold = 28.dp,
                        ) {
                            Image(pv.asImageBitmap(), contentDescription = "Page preview", modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        CircularProgressIndicator(color = p.accent)
                    }
                }
            }

            // Tools for the tab
            when (tab) {
                EditTab.Edges -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolButton(Glyph.Auto, "Find page") {
                        base?.let { b -> scope.launch { page.corners = withContext(Dispatchers.Default) { EdgeFinder.find(b) } } }
                    }
                    ToolButton(Glyph.Expand, "Whole photo") { page.corners = ImageTools.FULL }
                    ToolButton(Glyph.Rotate, "Rotate") { page.rotation = (page.rotation + 90) % 360 }
                }
                EditTab.Filters -> Column {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Look.entries.forEach { l -> Chip(l.label, l == page.look, { page.look = l }) }
                    }
                    if (draft.size > 1) {
                        Text(
                            "Use this filter on all pages",
                            color = p.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = 20.dp, top = 10.dp)
                                .clickable(role = Role.Button) { draft.forEach { it.look = page.look; it.adjust = page.adjust } }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                EditTab.Adjust -> Column(Modifier.padding(horizontal = 24.dp)) {
                    AdjustSlider("Brightness", page.adjust.brightness) { page.adjust = page.adjust.copy(brightness = it) }
                    AdjustSlider("Contrast", page.adjust.contrast) { page.adjust = page.adjust.copy(contrast = it) }
                }
            }
            Spacer(Modifier.height(116.dp))
        }

        // Bottom bar
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.height(46.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF4A463F), RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onAddPage)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+ Add page", color = p.barInk, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Box(
                Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(NumioAmber)
                    .clickable(enabled = !saving, role = Role.Button) { askName = true }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save", color = Color(0xFF1A1400), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        if (saving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NumioAmber)
                    Spacer(Modifier.height(12.dp))
                    Text("Saving your scan…", color = Cream, fontSize = 15.sp)
                }
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard this scan?", fontFamily = Display) },
            text = { Text("The pages you took haven't been saved yet.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onDiscard() }) { Text("Discard", color = Color(0xFFB3261E)) } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing", color = p.accentInk) } },
        )
    }

    if (askName) {
        var name by remember { mutableStateOf(defaultName()) }
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("Name this scan", fontFamily = Display) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    askName = false
                    saving = true
                    val pages = draft.map { Snapshot(it.file, it.corners, it.rotation, it.look, it.adjust) }
                    val finalName = name.trim().ifEmpty { defaultName() }
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                store.save(finalName, pages.size) { i ->
                                    val s = pages[i]
                                    val src = ImageTools.decodeFile(s.file, 2200)
                                    ImageTools.process(src, s.corners ?: EdgeFinder.find(src), s.rotation, s.look, s.adjust)
                                }
                            }
                        }
                        saving = false
                        result.onSuccess(onSaved).onFailure { error = "Couldn't save the scan. Your pages are still here — try again." }
                    }
                }) { Text("Save", color = p.accentInk) }
            },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("Cancel", color = p.sub) } },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK", color = p.accentInk) } },
        )
    }
}

private data class Snapshot(val file: File, val corners: List<Offset>?, val rotation: Int, val look: Look, val adjust: Adjust)

private fun defaultName(): String =
    "Scan " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun ToolButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    val p = LocalPaper.current
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(glyph, p.accentInk, Modifier.size(20.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = p.ink, fontSize = 12.sp)
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val p = LocalPaper.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.ink, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line),
            modifier = Modifier.weight(1f),
        )
        Text(
            "Reset",
            color = p.accentInk, fontSize = 12.sp,
            modifier = Modifier.clickable(role = Role.Button) { onChange(0f) }.padding(8.dp),
        )
    }
}

/** The photo with four amber corners you can drag onto the page's edges. */
@Composable
private fun CornerEditor(image: Bitmap, corners: List<Offset>, onChange: (List<Offset>) -> Unit) {
    val current by rememberUpdatedState(corners)
    val img = remember(image) { image.asImageBitmap() }
    val grab = with(LocalDensity.current) { 56.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = constraints.maxWidth.toFloat()
        val bh = constraints.maxHeight.toFloat()
        val scale = min(bw / image.width, bh / image.height)
        val dw = image.width * scale
        val dh = image.height * scale
        val ox = (bw - dw) / 2f
        val oy = (bh - dh) / 2f
        var active by remember { mutableIntStateOf(-1) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(image, dw, dh) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val pts = current.map { Offset(ox + it.x * dw, oy + it.y * dh) }
                            val nearest = pts.indices.minByOrNull { hypot(pts[it].x - pos.x, pts[it].y - pos.y) } ?: -1
                            active = if (nearest >= 0 && hypot(pts[nearest].x - pos.x, pts[nearest].y - pos.y) < grab) nearest else -1
                        },
                        onDragEnd = { active = -1 },
                        onDragCancel = { active = -1 },
                        onDrag = { change, drag ->
                            if (active >= 0) {
                                change.consume()
                                val list = current.toMutableList()
                                val c = list[active]
                                list[active] = Offset(
                                    (c.x + drag.x / dw).coerceIn(0f, 1f),
                                    (c.y + drag.y / dh).coerceIn(0f, 1f),
                                )
                                onChange(list)
                            }
                        },
                    )
                },
        ) {
            drawImage(
                img,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            )
            val pts = corners.map { Offset(ox + it.x * dw, oy + it.y * dh) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1..3) lineTo(pts[i].x, pts[i].y)
                close()
            }
            drawPath(path, NumioAmber.copy(alpha = 0.14f))
            drawPath(path, NumioAmber, style = Stroke(width = 2.5.dp.toPx()))
            pts.forEachIndexed { i, pt ->
                val r = if (i == active) 13.dp.toPx() else 10.dp.toPx()
                drawCircle(Coal, radius = r, center = pt)
                drawCircle(NumioAmber, radius = r, center = pt, style = Stroke(width = 3.dp.toPx()))
            }

            // Zoom bubble: shows what's under your finger while you drag a corner.
            if (active in 0..3) {
                val pt = pts[active]
                val lr = 58.dp.toPx()
                val zoom = 2.5f
                val margin = 12.dp.toPx()
                val onLeft = pt.x > size.width / 2f
                val center = Offset(
                    if (onLeft) margin + lr else size.width - margin - lr,
                    margin + lr,
                )
                val circle = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center, lr))
                }
                drawCircle(Coal, radius = lr + 3.dp.toPx(), center = center)
                clipPath(circle) {
                    drawRect(Coal)
                    withTransform({
                        translate(center.x - pt.x * zoom, center.y - pt.y * zoom)
                        scale(zoom, zoom, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            img,
                            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                        )
                        drawPath(path, NumioAmber, style = Stroke(width = 2.5.dp.toPx() / zoom))
                    }
                }
                drawCircle(NumioAmber, radius = lr, center = center, style = Stroke(width = 2.dp.toPx()))
                // crosshair
                val c = 8.dp.toPx()
                drawLine(NumioAmber, Offset(center.x - c, center.y), Offset(center.x + c, center.y), strokeWidth = 1.5.dp.toPx())
                drawLine(NumioAmber, Offset(center.x, center.y - c), Offset(center.x, center.y + c), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

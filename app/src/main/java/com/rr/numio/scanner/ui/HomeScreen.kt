package com.rr.numio.scanner.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rr.numio.scanner.data.Scan
import com.rr.numio.scanner.data.ScanStore
import com.rr.numio.scanner.data.ThemeMode
import com.rr.numio.scanner.image.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    store: ScanStore,
    refreshKey: Int,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onScan: () -> Unit,
    onQr: () -> Unit,
    onImport: () -> Unit,
    onOpen: (Scan) -> Unit,
) {
    val p = LocalPaper.current
    SystemBarIcons(lightIcons = p.dark)
    var scans by remember { mutableStateOf<List<Scan>?>(null) }
    LaunchedEffect(refreshKey) { scans = withContext(Dispatchers.IO) { store.list() } }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val all = scans.orEmpty()
    val shown = if (query.isBlank()) all else all.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxSize().paperBackground(p)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumioWordmark(Modifier.height(26.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = p.ink)
                }
            }
            Text(
                "Scanner",
                fontFamily = Display,
                fontSize = 38.sp,
                color = p.ink,
                modifier = Modifier.padding(start = 22.dp, top = 6.dp),
            )
            Text(
                when (all.size) {
                    0 -> "Kept only on this phone"
                    1 -> "1 scan · kept only on this phone"
                    else -> "${all.size} scans · kept only on this phone"
                },
                fontSize = 13.sp,
                color = p.sub,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
            )

            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = p.sub, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search your scans", color = p.sub, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = p.ink, fontSize = 14.sp),
                        cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (scans != null && shown.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FoldedPaper(Modifier.size(width = 120.dp, height = 150.dp), fold = 26.dp) {}
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (all.isEmpty()) "No scans yet" else "Nothing matches",
                        fontFamily = Display, fontSize = 22.sp, color = p.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (all.isEmpty()) "Tap the amber button to scan your first page." else "Try another word.",
                        fontSize = 14.sp, color = p.sub,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shown, key = { it.id }) { scan -> ScanCard(scan) { onOpen(scan) } }
                }
            }
        }

        // Bottom bar
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(peakShape(18.dp))
                .background(p.bar)
                .navigationBarsPadding()
                .height(100.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BarButton(Glyph.Image, "Import", p.barInk, onImport)
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .rotate(-4f)
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(NumioAmber)
                        .clickable(role = Role.Button, onClickLabel = "Scan", onClick = onScan),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(Glyph.Scan, p.onAccent, Modifier.size(30.dp), stroke = 2.2.dp)
                }
                BarButton(Glyph.Qr, "QR", p.barInk, onQr)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done", color = p.accentInk) } },
            title = { Text("Settings", fontFamily = Display) },
            text = {
                Column {
                    Text("Theme", fontWeight = FontWeight.SemiBold, color = p.ink)
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { onThemeChange(mode) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = { onThemeChange(mode) })
                            Text(mode.label, color = p.ink)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    NumioWordmark(Modifier.height(22.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Scanner by Numio · no internet permission. Your scans never leave this phone.",
                        fontSize = 13.sp,
                        color = p.sub,
                    )
                }
            },
        )
    }
}

@Composable
private fun BarButton(glyph: Glyph, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(top = 14.dp)
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LineIcon(glyph, tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

private val dateFormat = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())

@Composable
private fun ScanCard(scan: Scan, onClick: () -> Unit) {
    val p = LocalPaper.current
    val thumb by produceState<Bitmap?>(null, scan.id, scan.rev) {
        value = withContext(Dispatchers.IO) { runCatching { ImageTools.decodeFile(scan.page(0), 500) }.getOrNull() }
    }
    Column(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        FoldedPaper(Modifier.fillMaxWidth().height(170.dp), fold = 24.dp) {
            thumb?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.accent)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    if (scan.pageCount == 1) "1 page" else "${scan.pageCount} pages",
                    color = p.onAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            scan.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(dateFormat.format(Date(scan.created)), color = p.sub, fontSize = 12.sp)
    }
}

package com.rr.numio.scanner.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rr.numio.scanner.image.QrAnalyzer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

enum class CamMode(val label: String) { Document("Document"), IdCard("ID card"), Qr("QR") }

@Composable
fun CameraScreen(
    startMode: CamMode,
    pageCount: Int,
    modes: List<CamMode> = CamMode.entries,
    onCaptured: (File, CamMode) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    SystemBarIcons(lightIcons = true)
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var mode by remember { mutableStateOf(startMode) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val main = remember { Handler(Looper.getMainLooper()) }
    val analyzer = remember { QrAnalyzer { text -> main.post { qrText = text } } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    LaunchedEffect(granted, mode) {
        if (!granted) return@LaunchedEffect
        val p = provider ?: cameraProvider(ctx).also { provider = it }
        p.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val useCase = if (mode != CamMode.Qr) {
            imageCapture
        } else {
            analyzer.paused = false
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
        }
        camera = try {
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, useCase)
        } catch (e: Exception) {
            null
        }
        camera?.cameraControl?.enableTorch(torch)
    }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    var lastShot by remember { mutableStateOf(0L) }

    fun capture() {
        if (busy) return
        // Ignore a second tap right after a photo, so one tap = one page.
        val now = System.currentTimeMillis()
        if (now - lastShot < 1200) return
        lastShot = now
        busy = true
        // Feedback: a short tap vibration and a white flash.
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scope.launch {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(260))
        }
        val dir = File(ctx.cacheDir, "draft").apply { mkdirs() }
        val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    onCaptured(file, mode)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    Toast.makeText(ctx, "Couldn't take the photo. Try again.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Coal)) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Scanner needs the camera", color = Cream, fontFamily = Display, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Photos are only used to make your scans. Nothing is sent anywhere.",
                    color = Color(0xFFB9B3A6), fontSize = 14.sp,
                )
                if (asked) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NumioAmber)
                            .clickable(role = Role.Button) { permission.launch(Manifest.permission.CAMERA) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) { Text("Allow camera", color = Color(0xFF1A1400), fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (mode == CamMode.IdCard && granted) {
            // Card-shaped guide (ID-1 cards are 85.6 × 54 mm).
            Column(
                Modifier.align(Alignment.Center).offset(y = (-60).dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NumioAmber)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        if (pageCount == 0) "Front of the card" else "Now the back",
                        color = Color(0xFF1A1400), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Nearly full width, so the card fills the photo: sharper small text.
                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .aspectRatio(85.6f / 54f)
                        .border(3.dp, NumioAmber, RoundedCornerShape(16.dp)),
                )
            }
        }

        if (mode == CamMode.Qr && granted) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)
                    .size(240.dp)
                    .border(3.dp, NumioAmber, RoundedCornerShape(24.dp)),
            )
        }

        // Top row
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Cream) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(Coal.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(Glyph.Lock, NumioAmber, Modifier.size(13.dp), stroke = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("No internet · stays on phone", color = Cream, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }) {
                LineIcon(Glyph.Flash, if (torch) NumioAmber else Cream)
            }
        }

        // Bottom panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(peakShape(22.dp))
                .background(Coal)
                .navigationBarsPadding()
                .padding(top = 36.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { m ->
                    val on = m == mode
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) Cream else Color.Transparent)
                            .clickable(role = Role.Tab) { mode = m; qrText = null }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(m.label, color = if (on) Coal else Color(0xFFB9B3A6), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Page stack
                Box(
                    Modifier
                        .size(width = 58.dp, height = 66.dp)
                        .clickable(enabled = pageCount > 0, role = Role.Button, onClickLabel = "Review pages", onClick = onDone),
                ) {
                    if (pageCount > 0) {
                        Box(
                            Modifier.offset(x = 6.dp).size(44.dp, 58.dp).rotate(8f)
                                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFCFC8B8)),
                        )
                        FoldedPaper(
                            Modifier.offset(x = 4.dp, y = 4.dp).size(44.dp, 58.dp),
                            fold = 12.dp,
                            color = Color(0xFFF6F4EE),
                        ) {}
                        Box(
                            Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(NumioAmber),
                            contentAlignment = Alignment.Center,
                        ) { Text("$pageCount", color = Color(0xFF1A1400), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }

                // Shutter
                if (mode != CamMode.Qr) {
                    Box(
                        Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .border(3.dp, NumioAmber, CircleShape)
                            .clickable(enabled = granted && !busy, role = Role.Button, onClickLabel = "Take scan") { capture() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = NumioAmber, modifier = Modifier.size(40.dp))
                        } else {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(NumioAmber))
                        }
                    }
                } else {
                    Text("Point at a code", color = Cream, fontSize = 14.sp)
                }

                // Done
                Box(
                    Modifier
                        .size(width = 58.dp, height = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = pageCount > 0, role = Role.Button, onClick = onDone),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Done",
                        color = if (pageCount > 0) NumioAmber else Color(0xFF5E5A50),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Shutter flash
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))
        }

        qrText?.let { text ->
            QrSheet(
                text = text,
                onDismiss = {
                    qrText = null
                    analyzer.paused = false
                },
            )
        }
    }
}

private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(ctx))
    }

/** What a QR code holds. Links are shown in full and only open when tapped. */
@Composable
private fun QrSheet(text: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isLink = text.startsWith("http://", true) || text.startsWith("https://", true)
    val wifi = if (text.startsWith("WIFI:", true)) parseWifi(text) else null

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
    ) {
        FoldedPaper(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(onClick = {}) // keeps taps on the sheet from closing it
                .navigationBarsPadding(),
            fold = 34.dp,
            color = Color(0xFFF6F4EE),
        ) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp)) {
                Text(
                    when {
                        isLink -> "Link found"
                        wifi != null -> "Wi-Fi network"
                        else -> "Code found"
                    },
                    fontFamily = Display, fontSize = 24.sp, color = Color(0xFF191815),
                )
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2DDD0), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        if (wifi != null) {
                            Text("Network: ${wifi.first}", color = Color(0xFF191815), fontSize = 15.sp)
                            if (wifi.second.isNotEmpty()) {
                                Text("Password: ${wifi.second}", color = Color(0xFF191815), fontSize = 15.sp)
                            }
                        } else {
                            Text(text, color = Color(0xFF191815), fontSize = 15.sp)
                        }
                    }
                }
                if (isLink) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Check the address before opening. Scanner never opens links on its own.",
                        color = Color(0xFF5E5A50), fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetButton("Copy", primary = !isLink, modifier = Modifier.weight(1f)) {
                        copy(ctx, wifi?.second?.takeIf { it.isNotEmpty() } ?: text)
                    }
                    if (isLink) {
                        SheetButton("Open link", primary = true, modifier = Modifier.weight(1f)) {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text)))
                            }.onFailure {
                                Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    SheetButton("Scan again", primary = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) NumioAmber else Color.Transparent)
            .border(1.dp, if (primary) NumioAmber else Color(0xFFD8D2C3), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF191815), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("Scanner", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

/** WIFI:T:WPA;S:name;P:password;; → (name, password) */
private fun parseWifi(s: String): Pair<String, String> {
    fun field(key: String): String {
        val m = Regex("""(?:^|;|:)$key:((?:\\.|[^;])*)""").find(s.removePrefix("WIFI:"))
        return m?.groupValues?.get(1)?.replace(Regex("""\\(.)"""), "$1") ?: ""
    }
    return field("S") to field("P")
}

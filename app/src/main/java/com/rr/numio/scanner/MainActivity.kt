package com.rr.numio.scanner

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rr.numio.scanner.data.Prefs
import com.rr.numio.scanner.data.Scan
import com.rr.numio.scanner.data.ScanStore
import com.rr.numio.scanner.data.ThemeMode
import com.rr.numio.scanner.ui.CamMode
import com.rr.numio.scanner.ui.CameraScreen
import com.rr.numio.scanner.ui.DetailScreen
import com.rr.numio.scanner.ui.EditorScreen
import com.rr.numio.scanner.ui.HomeScreen
import com.rr.numio.scanner.ui.PageDraft
import com.rr.numio.scanner.ui.PaperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Unsaved pages from an earlier session are only temporary photos.
        File(cacheDir, "draft").deleteRecursively()
        val store = ScanStore(applicationContext)
        val prefs = Prefs(applicationContext)
        setContent { ScannerApp(store, prefs) }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Camera(val mode: CamMode) : Screen
    data object Editor : Screen
    data class Detail(val scan: Scan) : Screen
}

@Composable
private fun ScannerApp(store: ScanStore, prefs: Prefs) {
    var themeMode by remember { mutableStateOf(prefs.theme) }
    val dark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val draft = remember { mutableStateListOf<PageDraft>() }
    var refresh by remember { mutableIntStateOf(0) }
    /** Set while adding pages to a saved scan. */
    var appendTo by remember { mutableStateOf<Scan?>(null) }
    /** True while scanning the front and back of an ID card. */
    var idCard by remember { mutableStateOf(false) }

    /** Starting something new from the home screen. */
    fun freshStart() {
        appendTo = null
        idCard = false
    }

    /** Where to go when leaving the camera or editor without saving. */
    fun leave() {
        val back = appendTo
        freshStart()
        screen = if (back != null) Screen.Detail(back) else Screen.Home
    }

    fun clearDraft() {
        draft.forEach { it.file.delete() }
        draft.clear()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "draft").apply { mkdirs() }
                uris.mapIndexedNotNull { i, uri ->
                    runCatching {
                        val f = File(dir, "import_${System.currentTimeMillis()}_$i.jpg")
                        ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                        f
                    }.getOrNull()
                }
            }
            if (files.isEmpty()) {
                Toast.makeText(ctx, "Couldn't open those images", Toast.LENGTH_SHORT).show()
            } else {
                draft.addAll(files.map { PageDraft(it) })
                screen = Screen.Editor
            }
        }
    }

    PaperTheme(dark) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                store = store,
                refreshKey = refresh,
                themeMode = themeMode,
                onThemeChange = { themeMode = it; prefs.theme = it },
                onScan = { freshStart(); screen = Screen.Camera(CamMode.Document) },
                onQr = { freshStart(); screen = Screen.Camera(CamMode.Qr) },
                onImport = {
                    freshStart()
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onOpen = { screen = Screen.Detail(it) },
            )

            is Screen.Camera -> {
                BackHandler { if (draft.isEmpty()) leave() else screen = Screen.Editor }
                CameraScreen(
                    startMode = s.mode,
                    pageCount = draft.size,
                    // ID cards and QR only when starting fresh; adding pages is documents only.
                    modes = if (appendTo == null && draft.isEmpty()) CamMode.entries else listOf(CamMode.Document),
                    onCaptured = { file, mode ->
                        val page = PageDraft(file)
                        if (mode == CamMode.IdCard) {
                            idCard = true
                            // Cards are colourful: start untouched.
                            page.look = com.rr.numio.scanner.image.Look.Original
                            page.autoLook = false
                        }
                        draft.add(page)
                        if (mode == CamMode.IdCard && draft.size == 2) screen = Screen.Editor
                    },
                    onDone = { if (draft.isNotEmpty()) screen = Screen.Editor },
                    onClose = { if (draft.isEmpty()) leave() else screen = Screen.Editor },
                )
            }

            Screen.Editor -> {
                if (draft.isEmpty()) {
                    leave()
                } else {
                    EditorScreen(
                        store = store,
                        draft = draft,
                        onAddPage = { screen = Screen.Camera(CamMode.Document) },
                        onSaved = { saved ->
                            clearDraft()
                            freshStart()
                            refresh++
                            screen = Screen.Detail(saved)
                        },
                        onDiscard = {
                            clearDraft()
                            leave()
                        },
                        idCard = idCard,
                        appendTo = appendTo,
                    )
                }
            }

            is Screen.Detail -> {
                BackHandler { screen = Screen.Home }
                DetailScreen(
                    store = store,
                    start = s.scan,
                    onBack = { screen = Screen.Home },
                    onChanged = { refresh++ },
                    onAddPages = { scan, fromGallery ->
                        freshStart()
                        appendTo = scan
                        if (fromGallery) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            screen = Screen.Camera(CamMode.Document)
                        }
                    },
                )
            }
        }
    }
}

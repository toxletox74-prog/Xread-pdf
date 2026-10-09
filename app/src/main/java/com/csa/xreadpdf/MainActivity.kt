package com.csa.xreadpdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csa.xreadpdf.ui.LibraryScreen
import com.csa.xreadpdf.ui.ViewerScreen
import com.csa.xreadpdf.ui.XreadPdfTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { XreadPdfTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** PDF reçu via "Ouvrir avec" ou "Partager vers". */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        vm.openExternal(uri)
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val files by vm.files.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Scanner Google : caméra, détection des bords, recadrage, filtres, multi-pages -> PDF
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pdf?.uri?.let(vm::onScanResult)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::openExternal)
    }

    fun startScan() {
        val activity = context.findActivity() ?: return
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.message("Scanner indisponible (services Google Play requis)") }
    }

    fun share(file: java.io.File) {
        runCatching { context.startActivity(vm.shareIntent(file)) }
            .onFailure { vm.message("Aucune application pour partager") }
    }

    when (val screen = vm.screen) {
        Screen.Library -> LibraryScreen(
            files = files,
            snackbar = snackbar,
            onScan = ::startScan,
            onOpenExternal = { openLauncher.launch(arrayOf("application/pdf")) },
            onOpen = vm::open,
            onShare = ::share,
            onRename = vm::rename,
            onDelete = vm::delete,
        )
        is Screen.Viewer -> {
            BackHandler(onBack = vm::back)
            ViewerScreen(
                file = screen.file,
                inLibrary = vm.isInLibrary(screen.file),
                snackbar = snackbar,
                onBack = vm::back,
                onShare = { share(screen.file) },
                onSave = { vm.saveToLibrary(screen.file) },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

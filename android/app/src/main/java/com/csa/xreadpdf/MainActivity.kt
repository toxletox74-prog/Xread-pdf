package com.csa.xreadpdf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csa.xreadpdf.editor.Tool
import com.csa.xreadpdf.tools.ImageFormat
import com.csa.xreadpdf.tools.SystemTools
import com.csa.xreadpdf.ui.EditorScreen
import com.csa.xreadpdf.ui.FormSheet
import com.csa.xreadpdf.ui.LibraryActions
import com.csa.xreadpdf.ui.LibraryScreen
import com.csa.xreadpdf.ui.LockScreen
import com.csa.xreadpdf.ui.ScanSaveDialog
import com.csa.xreadpdf.ui.SettingsScreen
import com.csa.xreadpdf.ui.SignaturesScreen
import com.csa.xreadpdf.ui.ToolLaunchers
import com.csa.xreadpdf.ui.ToolsSheet
import com.csa.xreadpdf.ui.ViewerActions
import com.csa.xreadpdf.ui.ViewerScreen
import com.csa.xreadpdf.ui.XreadPdfTheme
import com.csa.xreadpdf.ui.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { vm.backgroundAt = System.currentTimeMillis() }
            override fun onStart(owner: LifecycleOwner) {
                val away = vm.backgroundAt
                if (vm.appLock && away > 0 && System.currentTimeMillis() - away > 60_000) vm.locked = true
            }
        })
        setContent {
            XreadPdfTheme {
                App(vm, onUnlock = ::authenticate, canLock = ::canLock)
                if (vm.locked) {
                    BackHandler { finish() }
                    LockScreen(onUnlock = ::authenticate)
                    LaunchedEffect(Unit) { authenticate() }
                }
            }
        }
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

    private fun canLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate() {
        if (!canLock()) { vm.locked = false; return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.locked = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Xread PDF")
                .setSubtitle("Empreinte, visage ou code de l'appareil")
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .build()
        )
    }
}

@Composable
private fun App(vm: MainViewModel, onUnlock: () -> Unit, canLock: () -> Boolean) {
    val context = LocalContext.current
    val files by vm.files.collectAsStateWithLifecycle()
    val signatures by vm.signatures.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toolsFile by remember { mutableStateOf<File?>(null) }
    var formFile by remember { mutableStateOf<File?>(null) }
    var insertInto by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var exportFrom by remember { mutableStateOf<Pair<File, ImageFormat>?>(null) }

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
        } else vm.scanTarget = null
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::openExternal)
    }
    // Sélecteur d'emplacement Android (Téléchargements, Documents, carte SD, Drive…)
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> vm.onExportDestination(uri) }
    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { uris ->
        if (uris.isNotEmpty()) vm.imagesToPdf(uris)
    }
    val insertLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = insertInto
        insertInto = null
        if (uri != null && target != null) vm.insertPdf(target.first, uri, target.second)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val src = exportFrom
        exportFrom = null
        if (uri != null && src != null) vm.exportImages(src.first, uri, src.second)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::backup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restore)
    }

    fun launchSaveAs(suggestedName: String) {
        runCatching { saveAsLauncher.launch(suggestedName) }
            .onFailure { vm.cancelExport(); vm.message("Aucun gestionnaire de fichiers disponible") }
    }

    fun safely(what: String, block: () -> Unit) {
        runCatching(block).onFailure { vm.message("$what : aucune application disponible") }
    }

    fun startScan(target: File? = null) {
        val activity = context.findActivity() ?: return
        vm.scanTarget = target
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.scanTarget = null; vm.message("Scanner indisponible (services Google Play requis)") }
    }

    fun share(file: File) = safely("Partage") { context.startActivity(vm.shareIntent(file)) }
    fun shareMany(list: List<File>) = safely("Partage") { context.startActivity(vm.shareManyIntent(list)) }
    fun print(file: File) {
        val activity = context.findActivity() ?: return
        safely("Impression") { SystemTools.print(activity, file) }
    }

    val toolLaunchers = { file: File ->
        ToolLaunchers(
            pickPdfToInsert = { after -> insertInto = file to after; safely("Sélection") { insertLauncher.launch(arrayOf("application/pdf")) } },
            exportImages = { format -> exportFrom = file to format; safely("Sélection du dossier") { exportLauncher.launch(null) } },
            scanMore = { startScan(file) },
            print = { print(file) },
            fillForm = { formFile = file },
        )
    }

    val libraryActions = LibraryActions(
        onScan = { startScan() },
        onOpenExternal = { safely("Ouverture") { openLauncher.launch(arrayOf("application/pdf")) } },
        onImages = { safely("Galerie") { imagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        onSignatures = vm::showSignatures,
        onSettings = vm::showSettings,
        onOpen = vm::open,
        onEdit = { vm.edit(it, Tool.SELECT, Screen.Library) },
        onSign = { vm.edit(it, Tool.SIGNATURE, Screen.Library) },
        onShare = ::share,
        onShareMany = ::shareMany,
        onSaveAs = { launchSaveAs(vm.prepareExport(it)) },
        onPrint = ::print,
        onTools = { toolsFile = it },
        onRename = vm::rename,
        onDelete = vm::delete,
        onDeleteMany = vm::deleteMany,
        onToggleFavorite = vm::toggleFavorite,
        onMoveToFolder = vm::moveToFolder,
        onCreateFolder = vm::createFolder,
        onDeleteFolder = vm::deleteFolder,
        onMerge = vm::merge,
    )

    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        contentKey = { it::class },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Library -> LibraryScreen(
                files = files,
                library = library,
                signatureCount = signatures.size,
                snackbar = snackbar,
                a = libraryActions,
            )
            is Screen.Viewer -> {
                BackHandler(onBack = vm::back)
                val file = screen.file
                key(file, screen.version) { ViewerScreen(
                    file = file,
                    inLibrary = vm.isInLibrary(file),
                    meta = library.of(file),
                    snackbar = snackbar,
                    a = ViewerActions(
                        onBack = vm::back,
                        onShare = { share(file) },
                        onSave = { vm.saveToLibrary(file) },
                        onSaveAs = { launchSaveAs(vm.prepareExport(file)) },
                        onEdit = { vm.edit(file, Tool.SELECT, screen) },
                        onSign = { vm.edit(file, Tool.SIGNATURE, screen) },
                        onPrint = { print(file) },
                        onTools = { toolsFile = file },
                        onFillForm = { formFile = file },
                        onUnlock = { pw, wrong -> vm.unlock(file, pw, wrong) },
                        onPageChanged = { vm.setLastPage(file, it) },
                        onToggleBookmark = { vm.toggleBookmark(file, it) },
                        loadOutline = { vm.outline(file) },
                        loadTexts = { vm.pageTexts(file) },
                    ),
                ) }
            }
            is Screen.Editor -> EditorScreen(
                s = screen.session,
                signatures = signatures,
                inLibrary = vm.isInLibrary(screen.session.file),
                snackbar = snackbar,
                onClose = vm::closeEditor,
                onSave = vm::saveEdits,
                onNewSignature = { vm.addSignature(it) },
                onMessage = vm::message,
            )
            Screen.Signatures -> {
                BackHandler(onBack = vm::back)
                SignaturesScreen(
                    signatures = signatures,
                    onBack = vm::back,
                    onAdd = { vm.addSignature(it) },
                    onDelete = { vm.deleteSignature(it) },
                )
            }
            Screen.Settings -> {
                BackHandler(onBack = vm::back)
                SettingsScreen(
                    appLock = vm.appLock,
                    snackbar = snackbar,
                    onBack = vm::back,
                    onToggleLock = { on ->
                        if (on && !canLock()) vm.message("Configurez d'abord un verrouillage d'écran (empreinte ou code) sur le téléphone")
                        else vm.enableAppLock(on)
                    },
                    onBackup = {
                        val name = "Xread PDF - sauvegarde " + SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) + ".zip"
                        safely("Sauvegarde") { backupLauncher.launch(name) }
                    },
                    onRestore = { safely("Restauration") { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) } },
                )
            }
        }
    }

    toolsFile?.let { f ->
        ToolsSheet(vm, f, toolLaunchers(f), onDismiss = { toolsFile = null })
    }
    formFile?.let { f ->
        FormSheet(vm, f, onDismiss = { formFile = null })
    }

    vm.pendingScan?.let { p ->
        key(p.file) {
            ScanSaveDialog(
                defaultName = p.defaultName,
                initialToFolder = vm.scanToFolderDefault,
                initialKeepCopy = vm.scanKeepCopyDefault,
                onSaveInApp = vm::saveScanInApp,
                onChooseLocation = { name, keep -> launchSaveAs(vm.prepareScanExport(name, keep)) },
                onDiscard = vm::discardScan,
            )
        }
    }

    vm.busy?.let { label ->
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(32.dp)) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

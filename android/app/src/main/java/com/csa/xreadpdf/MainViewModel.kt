package com.csa.xreadpdf

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.csa.xreadpdf.editor.EditorSession
import com.csa.xreadpdf.editor.PdfExporter
import com.csa.xreadpdf.editor.Signature
import com.csa.xreadpdf.editor.SignatureStore
import com.csa.xreadpdf.editor.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Library : Screen
    data class Viewer(val file: File) : Screen
    data class Editor(val session: EditorSession, val returnTo: Screen) : Screen
    data object Signatures : Screen
}

data class PendingScan(val file: File, val defaultName: String)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo = PdfRepository(app)
    private val signatureStore = SignatureStore(app)

    private val _files = MutableStateFlow(repo.list())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _signatures = MutableStateFlow(signatureStore.list())
    val signatures: StateFlow<List<Signature>> = _signatures.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    var screen by mutableStateOf<Screen>(Screen.Library)
        private set

    fun isInLibrary(file: File) = repo.isInLibrary(file)
    fun shareIntent(file: File): Intent = repo.shareIntent(file)
    fun message(text: String) { _messages.tryEmit(text) }

    fun open(file: File) { screen = Screen.Viewer(file) }
    fun showSignatures() { screen = Screen.Signatures }
    fun back() { screen = Screen.Library }

    // ---- Scan : choix du nom et de l'emplacement ----

    /** Scan en attente d'enregistrement (affiche la fenêtre « Enregistrer le scan »). */
    var pendingScan by mutableStateOf<PendingScan?>(null)
        private set

    private val prefs = app.getSharedPreferences("xreadpdf", android.content.Context.MODE_PRIVATE)
    /** Derniers choix de la fenêtre, proposés par défaut au scan suivant. */
    val scanToFolderDefault: Boolean get() = prefs.getBoolean("scan_to_folder", false)
    val scanKeepCopyDefault: Boolean get() = prefs.getBoolean("scan_keep_copy", true)

    private sealed interface ExportJob {
        data class Scan(val name: String, val keepCopy: Boolean) : ExportJob
        data class Doc(val file: File) : ExportJob
    }
    private var exportJob: ExportJob? = null

    fun onScanResult(uri: Uri) = viewModelScope.launch {
        runCatching { repo.stageScan(uri) }
            .onSuccess { pendingScan = PendingScan(it, repo.defaultScanName()) }
            .onFailure { message("Échec de la récupération du scan") }
    }

    fun saveScanInApp(name: String) {
        val p = pendingScan ?: return
        pendingScan = null
        prefs.edit().putBoolean("scan_to_folder", false).apply()
        viewModelScope.launch {
            runCatching { repo.keepInLibrary(p.file, name) }
                .onSuccess { refresh(); screen = Screen.Viewer(it); message("Enregistré dans Mes PDF") }
                .onFailure { pendingScan = p; message("Échec de l'enregistrement du scan") }
        }
    }

    /** Prépare l'enregistrement du scan ailleurs ; renvoie le nom à proposer au sélecteur. */
    fun prepareScanExport(name: String, keepCopy: Boolean): String {
        exportJob = ExportJob.Scan(name, keepCopy)
        prefs.edit().putBoolean("scan_to_folder", true).putBoolean("scan_keep_copy", keepCopy).apply()
        return repo.fileName(name)
    }

    /** Prépare « Enregistrer sous… » pour un PDF existant. */
    fun prepareExport(file: File): String {
        exportJob = ExportJob.Doc(file)
        return file.name
    }

    fun cancelExport() { exportJob = null }

    /** Retour du sélecteur d'emplacement (null = annulé : la fenêtre du scan reste ouverte). */
    fun onExportDestination(uri: Uri?) {
        val job = exportJob ?: return
        exportJob = null
        if (uri == null) return
        when (job) {
            is ExportJob.Scan -> {
                val p = pendingScan ?: return
                pendingScan = null
                viewModelScope.launch {
                    runCatching {
                        val shown = repo.exportTo(p.file, uri)
                        val file = if (job.keepCopy) repo.keepInLibrary(p.file, job.name) else repo.nameStaged(p.file, job.name)
                        shown to file
                    }.onSuccess { (shown, file) ->
                        refresh()
                        screen = Screen.Viewer(file)
                        message("Enregistré : $shown" + if (job.keepCopy) " (+ copie dans Mes PDF)" else "")
                    }.onFailure {
                        pendingScan = p
                        message("Impossible d'enregistrer à cet emplacement")
                    }
                }
            }
            is ExportJob.Doc -> viewModelScope.launch {
                runCatching { repo.exportTo(job.file, uri) }
                    .onSuccess { message("Enregistré : $it") }
                    .onFailure { message("Impossible d'enregistrer à cet emplacement") }
            }
        }
    }

    fun discardScan() {
        pendingScan?.file?.delete()
        pendingScan = null
        message("Scan supprimé")
    }

    fun openExternal(uri: Uri) = viewModelScope.launch {
        closeEditorDoc()
        runCatching { repo.openExternal(uri) }
            .onSuccess { screen = Screen.Viewer(it) }
            .onFailure { message("Impossible d'ouvrir ce fichier") }
    }

    fun saveToLibrary(file: File) = viewModelScope.launch {
        runCatching { repo.saveToLibrary(file) }
            .onSuccess { refresh(); screen = Screen.Viewer(it); message("Ajouté à Mes PDF") }
            .onFailure { message("Échec de l'enregistrement") }
    }

    fun rename(file: File, newName: String) {
        val renamed = repo.rename(file, newName)
        if (renamed == null) message("Un fichier porte déjà ce nom") else refresh()
    }

    fun delete(file: File) {
        if (repo.delete(file)) {
            refresh()
            message("PDF supprimé")
        }
    }

    // ---- Éditeur ----

    fun edit(file: File, tool: Tool, returnTo: Screen) = viewModelScope.launch {
        launch(Dispatchers.IO) { PdfExporter.warmUp(app) }
        runCatching { PdfDoc.open(file) }
            .onSuccess { doc ->
                if (doc.pageCount == 0) {
                    doc.close()
                    message("Ce PDF ne contient aucune page")
                    return@onSuccess
                }
                val session = EditorSession(file, doc, tool)
                session.signature = _signatures.value.firstOrNull()
                if (tool == Tool.SIGNATURE && session.signature == null) session.padOpen = true
                screen = Screen.Editor(session, returnTo)
            }
            .onFailure { message("Impossible de modifier ce PDF") }
    }

    fun closeEditor() {
        val s = screen as? Screen.Editor ?: return
        s.session.doc.close()
        screen = s.returnTo
    }

    fun saveEdits(asCopy: Boolean) {
        val s = screen as? Screen.Editor ?: return
        val session = s.session
        if (session.saving) return
        session.saving = true
        val pages = session.pages
        viewModelScope.launch {
            runCatching {
                repo.saveEdited(session.file, asCopy) { dest ->
                    PdfExporter.export(app, session.file, pages, dest)
                }
            }.onSuccess { saved ->
                session.doc.close()
                refresh()
                screen = Screen.Viewer(saved)
                message(if (saved == session.file) "Modifications enregistrées" else "Enregistré : ${saved.nameWithoutExtension}")
            }.onFailure {
                session.saving = false
                message("Échec de l'enregistrement du PDF")
            }
        }
    }

    // ---- Signatures ----

    fun addSignature(sig: Signature) = viewModelScope.launch {
        withContext(Dispatchers.IO) { signatureStore.save(sig) }
        _signatures.value = listOf(sig) + _signatures.value
    }

    fun deleteSignature(sig: Signature) = viewModelScope.launch {
        withContext(Dispatchers.IO) { signatureStore.delete(sig) }
        _signatures.value = _signatures.value.filterNot { it.id == sig.id }
    }

    private fun closeEditorDoc() {
        (screen as? Screen.Editor)?.session?.doc?.close()
    }

    override fun onCleared() {
        closeEditorDoc()
    }

    private fun refresh() { _files.value = repo.list() }
}

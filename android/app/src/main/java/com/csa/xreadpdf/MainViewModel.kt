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

    fun onScanResult(uri: Uri) = viewModelScope.launch {
        runCatching { repo.saveScan(uri) }
            .onSuccess { refresh(); screen = Screen.Viewer(it); message("Scan enregistré") }
            .onFailure { message("Échec de l'enregistrement du scan") }
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

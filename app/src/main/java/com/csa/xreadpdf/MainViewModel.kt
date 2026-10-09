package com.csa.xreadpdf

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface Screen {
    data object Library : Screen
    data class Viewer(val file: File) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PdfRepository(app)

    private val _files = MutableStateFlow(repo.list())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    var screen by mutableStateOf<Screen>(Screen.Library)
        private set

    fun isInLibrary(file: File) = repo.isInLibrary(file)
    fun shareIntent(file: File): Intent = repo.shareIntent(file)
    fun message(text: String) { _messages.tryEmit(text) }

    fun open(file: File) { screen = Screen.Viewer(file) }
    fun back() { screen = Screen.Library }

    fun onScanResult(uri: Uri) = viewModelScope.launch {
        runCatching { repo.saveScan(uri) }
            .onSuccess { refresh(); screen = Screen.Viewer(it); message("Scan enregistré") }
            .onFailure { message("Échec de l'enregistrement du scan") }
    }

    fun openExternal(uri: Uri) = viewModelScope.launch {
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

    private fun refresh() { _files.value = repo.list() }
}

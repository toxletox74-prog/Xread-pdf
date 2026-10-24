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
import com.csa.xreadpdf.tools.CompressLevel
import com.csa.xreadpdf.tools.FormField
import com.csa.xreadpdf.tools.ImageFormat
import com.csa.xreadpdf.tools.NumberPosition
import com.csa.xreadpdf.tools.OutlineEntry
import com.csa.xreadpdf.tools.PageText
import com.csa.xreadpdf.tools.PasswordRequiredException
import com.csa.xreadpdf.tools.PdfTools
import com.csa.xreadpdf.tools.SystemTools
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
    data object Settings : Screen
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
    fun shareManyIntent(files: List<File>): Intent = repo.shareManyIntent(files)
    fun message(text: String) { _messages.tryEmit(text) }

    fun open(file: File) {
        updateDoc(file) { it.copy(lastOpened = System.currentTimeMillis()) }
        screen = Screen.Viewer(file)
    }
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
        val target = scanTarget
        scanTarget = null
        if (target != null) { appendScan(target, uri); return@launch }
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
        if (renamed == null) message("Un fichier porte déjà ce nom")
        else {
            if (renamed != file) moveMeta(file.name, renamed.name)
            refresh()
        }
    }

    fun delete(file: File) {
        if (repo.delete(file)) {
            updateLib { it.copy(docs = it.docs - file.name) }
            refresh()
            message("PDF supprimé")
        }
    }

    fun deleteMany(files: Collection<File>) {
        var n = 0
        files.forEach { if (repo.delete(it)) n++ }
        updateLib { it.copy(docs = it.docs - files.map { f -> f.name }.toSet()) }
        refresh()
        message(if (n > 1) "$n PDF supprimés" else "PDF supprimé")
    }

    // ---- Métadonnées : favoris, dossiers, récents, dernière page, signets ----

    private val metaStore = LibraryMetaStore(app)
    private val _library = MutableStateFlow(metaStore.load())
    val library: StateFlow<Library> = _library.asStateFlow()

    private fun updateLib(f: (Library) -> Library) {
        val next = f(_library.value)
        if (next == _library.value) return
        _library.value = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { metaStore.save(next) } }
    }

    private fun updateDoc(file: File, f: (DocMeta) -> DocMeta) {
        if (!repo.isInLibrary(file)) return
        updateLib { lib -> lib.copy(docs = lib.docs + (file.name to f(lib.of(file)))) }
    }

    private fun moveMeta(from: String, to: String) = updateLib { lib ->
        val m = lib.docs[from] ?: return@updateLib lib
        lib.copy(docs = lib.docs - from + (to to m))
    }

    fun toggleFavorite(file: File) = updateDoc(file) { it.copy(favorite = !it.favorite) }
    fun setLastPage(file: File, page: Int) = updateDoc(file) { it.copy(lastPage = page) }
    fun toggleBookmark(file: File, page: Int) = updateDoc(file) {
        it.copy(bookmarks = if (page in it.bookmarks) it.bookmarks - page else it.bookmarks + page)
    }
    fun moveToFolder(files: Collection<File>, folder: String?) {
        files.forEach { f -> updateDoc(f) { it.copy(folder = folder) } }
        message(if (folder == null) "Retiré du dossier" else "Déplacé dans « $folder »")
    }
    fun createFolder(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean in _library.value.folders) return false
        updateLib { it.copy(folders = it.folders + clean) }
        return true
    }
    fun deleteFolder(name: String) = updateLib { lib ->
        lib.copy(
            folders = lib.folders - name,
            docs = lib.docs.mapValues { (_, d) -> if (d.folder == name) d.copy(folder = null) else d },
        )
    }

    // ---- Tâches longues (overlay « Traitement… ») ----

    var busy by mutableStateOf<String?>(null)
        private set

    private fun job(label: String, work: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            try { work() }
            catch (e: PasswordRequiredException) { message("Ce PDF est protégé par mot de passe : ouvrez-le d'abord") }
            catch (e: Throwable) { message("Échec : ${e.message ?: "erreur inattendue"}") }
            finally { busy = null }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Produit un nouveau document « <nom> <suffixe> » dans Mes PDF à partir de [file], puis l'ouvre. */
    private fun derive(file: File, label: String, suffix: String, done: String, write: (File) -> Boolean = { true }, op: (File) -> Unit) =
        job(label) {
            val tmp = repo.tempFile("tool")
            val ok = io { op(tmp); write(tmp) }
            if (!ok) { tmp.delete(); return@job }
            val out = io { repo.addToLibrary(tmp, file.nameWithoutExtension + suffix) }
            refresh()
            screen = Screen.Viewer(out)
            message(done)
        }

    fun extractPages(file: File, pages: List<Int>) =
        derive(file, "Extraction des pages…", " - extrait", "Pages extraites dans un nouveau document") { PdfTools.extract(app, file, pages, it) }

    fun splitPdf(file: File, groups: List<List<Int>>) = job("Découpage…") {
        val n = io {
            groups.forEachIndexed { i, g ->
                val tmp = repo.tempFile("split")
                PdfTools.extract(app, file, g, tmp)
                repo.addToLibrary(tmp, "${file.nameWithoutExtension} - partie ${i + 1}")
            }
            groups.size
        }
        refresh()
        screen = Screen.Library
        message("$n documents créés dans Mes PDF")
    }

    fun insertBlank(file: File, after: Int) =
        derive(file, "Insertion…", " - modifié", "Page blanche insérée") { PdfTools.insertBlank(app, file, after, it) }

    fun insertPdf(file: File, other: Uri, after: Int) = job("Insertion du PDF…") {
        val src = repo.copyToCache(other)
        val tmp = repo.tempFile("ins")
        io { PdfTools.insertPdf(app, file, src, after, tmp) }
        src.delete()
        val out = io { repo.addToLibrary(tmp, file.nameWithoutExtension + " - modifié") }
        refresh()
        screen = Screen.Viewer(out)
        message("PDF inséré")
    }

    fun compress(file: File, level: CompressLevel) = derive(
        file, "Compression…", " - compressé", "PDF compressé",
        write = { ok -> ok.exists().also { if (!it) message("Ce PDF ne peut pas être allégé davantage") } },
    ) { PdfTools.compress(app, file, level, it) }

    fun ocr(file: File) = job("Reconnaissance du texte…") {
        val tmp = repo.tempFile("ocr")
        val n = io { PdfTools.ocr(app, file, tmp) { i, total -> busy = "Reconnaissance du texte… page $i / $total" } }
        if (n == 0) {
            tmp.delete()
            message("Ce PDF contient déjà du texte cherchable")
            return@job
        }
        val out = io { repo.addToLibrary(tmp, file.nameWithoutExtension + " - texte") }
        pageTextCache.remove(out.absolutePath)
        refresh()
        screen = Screen.Viewer(out)
        message("Texte reconnu sur $n page${if (n > 1) "s" else ""} : recherche et copie possibles")
    }

    fun pageNumbers(file: File, pos: NumberPosition) =
        derive(file, "Numérotation…", " - numéroté", "Numéros de page ajoutés") { PdfTools.addPageNumbers(app, file, pos, it) }

    fun watermark(file: File, text: String) =
        derive(file, "Ajout du filigrane…", " - filigrane", "Filigrane ajouté") { PdfTools.watermark(app, file, text, it) }

    fun protect(file: File, password: String) =
        derive(file, "Protection…", " - protégé", "Copie protégée par mot de passe créée") { PdfTools.protect(app, file, password, it) }

    /** Ouvre une copie déchiffrée d'un PDF protégé ; null si le mot de passe est faux. */
    fun unlock(file: File, password: String, onWrong: () -> Unit) = job("Déverrouillage…") {
        val dest = repo.unlockedFile(file)
        val ok = io { runCatching { PdfTools.decrypt(app, file, password, dest) }.exceptionOrNull() !is PasswordRequiredException }
        if (!ok) { onWrong(); return@job }
        screen = Screen.Viewer(dest)
        message("PDF déverrouillé. « Enregistrer » le garde sans mot de passe.")
    }

    fun exportImages(file: File, tree: Uri, format: ImageFormat) = job("Export des images…") {
        val n = io { SystemTools.exportImages(app, file, tree, format) { i, t -> busy = "Export des images… $i / $t" } }
        message("$n image${if (n > 1) "s" else ""} enregistrée${if (n > 1) "s" else ""}")
    }

    suspend fun formFields(file: File): List<FormField> = io { PdfTools.formFields(app, file) }

    fun fillForm(file: File, values: Map<String, String>) = job("Remplissage du formulaire…") {
        val tmp = repo.tempFile("form")
        val failed = io { PdfTools.fillForm(app, file, values, tmp) }
        val out = io { repo.addToLibrary(tmp, file.nameWithoutExtension + " - rempli") }
        refresh()
        screen = Screen.Viewer(out)
        message(if (failed == 0) "Formulaire rempli" else "Formulaire rempli ($failed champ(s) au rendu approximatif)")
    }

    fun merge(files: List<File>) = job("Fusion…") {
        val tmp = repo.tempFile("merge")
        io { PdfTools.merge(app, files, tmp) }
        val out = io { repo.addToLibrary(tmp, "${files.first().nameWithoutExtension} + ${files.size - 1}") }
        refresh()
        screen = Screen.Viewer(out)
        message("${files.size} PDF fusionnés")
    }

    fun imagesToPdf(uris: List<Uri>) = job("Création du PDF…") {
        val staged = repo.stagedFile()
        io { PdfTools.imagesToPdf(app, uris, staged) }
        pendingScan = PendingScan(staged, "Images " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.FRANCE).format(java.util.Date()))
    }

    // ---- Ajouter des pages scannées à un document ----

    /** Document auquel le prochain scan sera ajouté (null = nouveau document). */
    var scanTarget: File? = null

    private fun appendScan(target: File, scanUri: Uri) = job("Ajout des pages…") {
        val staged = repo.stageScan(scanUri)
        val tmp = repo.tempFile("append")
        io { PdfTools.merge(app, listOf(target, staged), tmp) }
        staged.delete()
        val out = io { repo.replaceOrAdd(target, tmp, " - complété") }
        pageTextCache.remove(out.absolutePath)
        refresh()
        screen = Screen.Viewer(out)
        message("Pages ajoutées")
    }

    // ---- Lecture : sommaire, texte ----

    private val pageTextCache = mutableMapOf<String, List<PageText>>()

    suspend fun outline(file: File): List<OutlineEntry> = io { PdfTools.outline(app, file) }

    suspend fun pageTexts(file: File): List<PageText> {
        val key = file.absolutePath
        pageTextCache[key]?.let { return it }
        val t = io { runCatching { PdfTools.pageTexts(app, file) }.getOrDefault(emptyList()) }
        if (pageTextCache.size > 4) pageTextCache.clear()
        pageTextCache[key] = t
        return t
    }

    // ---- Sauvegarde / restauration ----

    fun backup(uri: Uri) = job("Sauvegarde…") {
        val n = io { SystemTools.backup(app, uri) }
        message("Sauvegarde terminée : $n PDF")
    }

    fun restore(uri: Uri) = job("Restauration…") {
        var imported: Library? = null
        val n = io {
            SystemTools.restore(app, uri) { json ->
                val tmp = File(app.cacheDir, "restore-library.json").apply { writeText(json) }
                imported = metaStore.loadFrom(tmp)
                tmp.delete()
            }
        }
        imported?.let { imp ->
            updateLib { cur ->
                Library(
                    docs = imp.docs.filterKeys { File(repo.libraryDir, it).exists() } + cur.docs,
                    folders = (cur.folders + imp.folders).distinct(),
                )
            }
        }
        _signatures.value = signatureStore.list()
        refresh()
        message("Restauration terminée : $n PDF ajoutés")
    }

    // ---- Verrouillage ----

    var appLock by mutableStateOf(prefs.getBoolean("app_lock", false))
        private set

    /** Verrou actif (écran de déverrouillage affiché). */
    var locked by mutableStateOf(appLock)
    /** Heure de passage en arrière-plan (verrouillage après 1 min). */
    var backgroundAt = 0L

    fun enableAppLock(enabled: Boolean) {
        appLock = enabled
        prefs.edit().putBoolean("app_lock", enabled).apply()
    }

    // ---- Réglages ----

    fun showSettings() { screen = Screen.Settings }

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

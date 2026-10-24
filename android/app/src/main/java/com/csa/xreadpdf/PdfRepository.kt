package com.csa.xreadpdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stockage :
 *  - filesDir/pdfs : la bibliothèque "Mes PDF" (scans et PDF enregistrés)
 *  - cacheDir/open : copie temporaire d'un PDF ouvert depuis une autre app
 *  - cacheDir/scan : scan en attente du choix de l'emplacement
 */
class PdfRepository(private val context: Context) {

    val libraryDir: File = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val openDir: File = File(context.cacheDir, "open").apply { mkdirs() }
    private val scanDir: File = File(context.cacheDir, "scan").apply { mkdirs() }

    fun list(): List<File> =
        libraryDir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun isInLibrary(file: File): Boolean =
        file.parentFile?.canonicalPath == libraryDir.canonicalPath

    fun defaultScanName(): String =
        "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.FRANCE).format(Date())

    /** Nom de fichier proposé au sélecteur d'emplacement. */
    fun fileName(name: String): String = sanitize(name) + ".pdf"

    /** Met le PDF produit par le scanner en attente (cacheDir/scan) le temps de choisir où l'enregistrer. */
    suspend fun stageScan(uri: Uri): File = withContext(Dispatchers.IO) {
        scanDir.listFiles()?.forEach { it.delete() }
        copy(uri, File(scanDir, "scan-${System.currentTimeMillis()}.pdf"))
    }

    /** Range un scan en attente dans « Mes PDF » sous le nom choisi. */
    suspend fun keepInLibrary(staged: File, name: String): File = withContext(Dispatchers.IO) {
        val dest = uniqueFile(libraryDir, name)
        if (!staged.renameTo(dest)) {
            staged.copyTo(dest)
            staged.delete()
        }
        dest
    }

    /** Donne au scan en attente son nom définitif (sans l'ajouter à « Mes PDF »). */
    suspend fun nameStaged(staged: File, name: String): File = withContext(Dispatchers.IO) {
        val dest = File(scanDir, fileName(name))
        if (dest.canonicalPath == staged.canonicalPath || staged.renameTo(dest)) dest else staged
    }

    /** Écrit [file] à l'emplacement choisi par l'utilisateur ; renvoie le nom affiché de la destination. */
    suspend fun exportTo(file: File, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: error("Destination inaccessible")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
        displayName(uri)
    }

    suspend fun openExternal(uri: Uri): File = withContext(Dispatchers.IO) {
        openDir.listFiles()?.forEach { it.delete() }
        copy(uri, File(openDir, sanitize(displayName(uri)) + ".pdf"))
    }

    suspend fun saveToLibrary(file: File): File = withContext(Dispatchers.IO) {
        if (isInLibrary(file)) return@withContext file
        val dest = uniqueFile(libraryDir, file.nameWithoutExtension)
        file.copyTo(dest)
        dest
    }

    /**
     * Enregistre une version modifiée : [write] produit le PDF dans un fichier temporaire, qui
     * remplace ensuite l'original (s'il est dans « Mes PDF » et que [asCopy] est faux) ou devient
     * une copie dans « Mes PDF ».
     */
    suspend fun saveEdited(source: File, asCopy: Boolean, write: (File) -> Unit): File = withContext(Dispatchers.IO) {
        val inLib = isInLibrary(source)
        val tmp = File(libraryDir, ".edit-${System.nanoTime()}.part")
        try {
            write(tmp)
            val dest = when {
                inLib && !asCopy -> source
                inLib -> uniqueFile(libraryDir, source.nameWithoutExtension + " - modifié")
                else -> uniqueFile(libraryDir, source.nameWithoutExtension)
            }
            if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)
            dest
        } finally {
            tmp.delete()
        }
    }

    /** Nouveau fichier libre dans « Mes PDF » (nom de base, suffixe « (2) »… si déjà pris). */
    fun newLibraryFile(base: String): File = uniqueFile(libraryDir, base)

    /** Fichier temporaire de travail (cache). */
    fun tempFile(prefix: String): File = File(scanDir, "$prefix-${System.nanoTime()}.pdf")

    /** Fichier d'attente pour la fenêtre « Enregistrer » (images → PDF). */
    fun stagedFile(): File {
        scanDir.listFiles()?.forEach { it.delete() }
        return File(scanDir, "staged-${System.currentTimeMillis()}.pdf")
    }

    /** Copie d'un document choisi dans une autre app (cache). */
    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        copy(uri, File(scanDir, "in-${System.nanoTime()}.pdf"))
    }

    /** Copie déchiffrée d'un PDF protégé, affichée hors de « Mes PDF ». */
    fun unlockedFile(source: File): File {
        val dir = File(context.cacheDir, "unlocked").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, source.name)
    }

    /** Remplace [target] (dans « Mes PDF ») par [tmp] ; sinon range [tmp] comme nouveau document. */
    fun replaceOrAdd(target: File, tmp: File, suffix: String): File {
        val dest = if (isInLibrary(target)) target else uniqueFile(libraryDir, target.nameWithoutExtension + suffix)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        return dest
    }

    /** Range [tmp] dans « Mes PDF » sous un nom libre. */
    fun addToLibrary(tmp: File, base: String): File {
        val dest = uniqueFile(libraryDir, base)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest); tmp.delete() }
        return dest
    }

    /** Renvoie le nouveau fichier, ou null si le nom est déjà pris / renommage impossible. */
    fun rename(file: File, newName: String): File? {
        val dest = File(libraryDir, sanitize(newName) + ".pdf")
        if (dest.canonicalPath == file.canonicalPath) return file
        if (dest.exists()) return null
        return if (file.renameTo(dest)) dest else null
    }

    fun delete(file: File): Boolean = file.delete()

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Partager le PDF")
    }

    fun shareManyIntent(files: List<File>): Intent {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            if (uris.isNotEmpty()) {
                clipData = ClipData.newRawUri(files.first().name, uris.first()).also { c ->
                    uris.drop(1).forEach { c.addItem(ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Partager ${files.size} PDF")
    }

    private fun copy(uri: Uri, dest: File): File {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Impossible de lire le fichier")
        input.use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
        return dest
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: "Document" }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    }

    private fun uniqueFile(dir: File, base: String): File {
        val clean = sanitize(base)
        var candidate = File(dir, "$clean.pdf")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$clean ($n).pdf").also { n++ }
        return candidate
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("(?i)\\.pdf$"), "")
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_")
            .trim()
            .take(100)
            .ifBlank { "Document" }
}

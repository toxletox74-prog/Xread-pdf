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
 */
class PdfRepository(private val context: Context) {

    val libraryDir: File = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val openDir: File = File(context.cacheDir, "open").apply { mkdirs() }

    fun list(): List<File> =
        libraryDir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun isInLibrary(file: File): Boolean =
        file.parentFile?.canonicalPath == libraryDir.canonicalPath

    suspend fun saveScan(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.FRANCE).format(Date())
        copy(uri, uniqueFile(libraryDir, name))
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

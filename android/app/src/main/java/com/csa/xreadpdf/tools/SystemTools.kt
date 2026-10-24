package com.csa.xreadpdf.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

enum class ImageFormat(val label: String, val ext: String, val mime: String) {
    JPG("JPG (léger)", "jpg", "image/jpeg"),
    PNG("PNG (sans perte)", "png", "image/png"),
}

object SystemTools {

    /** Impression via le service d'impression Android (imprimante Wi-Fi, PDF…). [context] = Activity. */
    fun print(context: Context, file: File) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val name = file.nameWithoutExtension
        pm.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder("$name.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback,
            ) {
                try {
                    file.inputStream().use { input ->
                        FileOutputStream(destination.fileDescriptor).use { out -> input.copyTo(out) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }

    /**
     * Exporte chaque page en image dans le dossier choisi ([treeUri], sélecteur « dossier » Android).
     * Renvoie le nombre d'images écrites.
     */
    fun exportImages(context: Context, file: File, treeUri: Uri, format: ImageFormat, progress: (Int, Int) -> Unit): Int {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        var written = 0
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            val digits = r.pageCount.toString().length
            for (i in 0 until r.pageCount) {
                progress(i + 1, r.pageCount)
                val page = r.openPage(i)
                // ~150 dpi
                val k = 150f / 72f
                val w = (page.width * k).roundToInt().coerceIn(1, 4000)
                val h = (page.height * k * w / (page.width * k)).roundToInt().coerceIn(1, 6000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                val name = "${file.nameWithoutExtension} - page ${(i + 1).toString().padStart(digits, '0')}.${format.ext}"
                val uri = DocumentsContract.createDocument(resolver, parent, format.mime, name) ?: continue
                resolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
                    written++
                }
                bmp.recycle()
            }
        }
        return written
    }

    /** Sauvegarde : un ZIP contenant les PDF, les signatures et les métadonnées. */
    fun backup(context: Context, dest: Uri): Int {
        val root = context.filesDir
        val sources = listOf("pdfs", "signatures").flatMap { dir ->
            File(root, dir).listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.map { "$dir/${it.name}" to it } ?: emptyList()
        } + listOfNotNull(File(root, "library.json").takeIf { it.exists() }?.let { "library.json" to it })
        val out = context.contentResolver.openOutputStream(dest, "wt") ?: context.contentResolver.openOutputStream(dest)
            ?: error("Destination inaccessible")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("xread-pdf-backup.txt"))
            zip.write("Sauvegarde Xread PDF\n".toByteArray())
            zip.closeEntry()
            for ((path, f) in sources) {
                zip.putNextEntry(ZipEntry(path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return sources.count { it.first.startsWith("pdfs/") }
    }

    /**
     * Restauration : ajoute les PDF et signatures de la sauvegarde (sans écraser l'existant ;
     * un PDF de même nom est renommé). Renvoie le nombre de PDF restaurés.
     */
    fun restore(context: Context, src: Uri, onLibraryJson: (String) -> Unit): Int {
        val root = context.filesDir
        var pdfs = 0
        var valid = false
        val input = context.contentResolver.openInputStream(src) ?: error("Fichier illisible")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                when {
                    e.isDirectory || name.contains("..") -> Unit
                    name == "xread-pdf-backup.txt" -> valid = true
                    name == "library.json" -> onLibraryJson(zip.readBytes().toString(Charsets.UTF_8))
                    name.startsWith("pdfs/") && name.endsWith(".pdf", ignoreCase = true) -> {
                        val dir = File(root, "pdfs").apply { mkdirs() }
                        val base = name.removePrefix("pdfs/").substringAfterLast('/')
                        var target = File(dir, base)
                        var n = 2
                        while (target.exists()) target = File(dir, base.removeSuffix(".pdf").removeSuffix(".PDF") + " ($n).pdf").also { n++ }
                        target.outputStream().use { zip.copyTo(it) }
                        pdfs++
                    }
                    name.startsWith("signatures/") && name.endsWith(".json") -> {
                        val dir = File(root, "signatures").apply { mkdirs() }
                        val target = File(dir, name.substringAfterLast('/'))
                        if (!target.exists()) target.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
        if (!valid && pdfs == 0) error("Ce fichier n'est pas une sauvegarde Xread PDF")
        return pdfs
    }
}

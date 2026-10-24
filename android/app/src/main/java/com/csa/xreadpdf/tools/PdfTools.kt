package com.csa.xreadpdf.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Entrée du sommaire d'un PDF. */
data class OutlineEntry(val title: String, val page: Int, val level: Int)

/** Texte d'une page, avec la boîte (en points d'affichage, y vers le bas) de chaque caractère. */
class PageText(val text: String, val boxes: List<RectF?>)

/** Résultat de recherche : page, rectangles à surligner (points d'affichage) et extrait. */
data class SearchHit(val page: Int, val rects: List<RectF>, val snippet: String)

enum class FieldKind { TEXT, CHECK, RADIO, CHOICE }

/** Champ de formulaire PDF. [name] = nom complet (identifiant), [label] = libellé lisible. */
data class FormField(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val options: List<String> = emptyList(),
    val multiline: Boolean = false,
)

enum class CompressLevel(val label: String, val quality: Float, val maxSide: Int) {
    STRONG("Forte (plus léger)", 0.45f, 1200),
    MEDIUM("Moyenne", 0.62f, 1700),
    LIGHT("Légère (meilleure qualité)", 0.8f, 2400),
}

enum class NumberPosition(val label: String) { BOTTOM_CENTER("En bas au centre"), BOTTOM_RIGHT("En bas à droite"), TOP_RIGHT("En haut à droite") }

/** Le PDF demande un mot de passe (ou le mot de passe donné est faux). */
class PasswordRequiredException : Exception("Mot de passe requis")

/**
 * Opérations PDF (PdfBox-Android). Toutes sont bloquantes : à appeler hors du thread principal.
 * Les résultats sont écrits dans [dest] ; la source n'est jamais modifiée.
 */
object PdfTools {

    private fun mem() = MemoryUsageSetting.setupMixed(48L * 1024 * 1024)

    private fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    private fun load(context: Context, file: File, password: String = ""): PDDocument {
        init(context)
        return try {
            PDDocument.load(file, password, mem()).also { if (it.isEncrypted) it.isAllSecurityToBeRemoved = true }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordRequiredException()
        }
    }

    // ------------------------------------------------------------------ Mot de passe

    fun needsPassword(context: Context, file: File): Boolean =
        try { load(context, file).close(); false } catch (e: PasswordRequiredException) { true }

    /** Copie déchiffrée d'un PDF protégé ([PasswordRequiredException] si le mot de passe est faux). */
    fun decrypt(context: Context, src: File, password: String, dest: File) {
        load(context, src, password).use { it.save(dest) }
    }

    fun protect(context: Context, src: File, password: String, dest: File) {
        load(context, src).use { doc ->
            val policy = StandardProtectionPolicy(password + "#owner", password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(dest)
        }
    }

    // ------------------------------------------------------------------ Pages

    fun pageCount(context: Context, file: File): Int = load(context, file).use { it.numberOfPages }

    fun merge(context: Context, files: List<File>, dest: File) {
        init(context)
        val util = PDFMergerUtility()
        files.forEach { util.addSource(it) }
        util.destinationFileName = dest.absolutePath
        util.mergeDocuments(mem())
    }

    /** Ne garde que les pages [keep] (index 0-based, dans l'ordre du document). */
    fun extract(context: Context, src: File, keep: List<Int>, dest: File) {
        load(context, src).use { doc ->
            val set = keep.toSet()
            for (i in doc.numberOfPages - 1 downTo 0) if (i !in set) doc.removePage(i)
            doc.save(dest)
        }
    }

    /** Insère une page blanche après la page [after] (-1 = au début). */
    fun insertBlank(context: Context, src: File, after: Int, dest: File) {
        load(context, src).use { doc ->
            val ref = doc.getPage(after.coerceIn(0, doc.numberOfPages - 1))
            val blank = PDPage(PDRectangle(ref.mediaBox.width, ref.mediaBox.height))
            if (after < 0) doc.pages.insertBefore(blank, doc.getPage(0)) else doc.pages.insertAfter(blank, ref)
            doc.save(dest)
        }
    }

    /** Insère toutes les pages de [other] après la page [after] (-1 = au début, ≥ dernière = à la fin). */
    fun insertPdf(context: Context, src: File, other: File, after: Int, dest: File) {
        load(context, src).use { doc ->
            load(context, other).use { o ->
                var anchor: PDPage? = if (after < 0) null else doc.getPage(after.coerceAtMost(doc.numberOfPages - 1))
                val first = doc.getPage(0)
                for (p in o.pages) {
                    val imported = doc.importPage(p)
                    doc.pages.remove(imported)
                    if (anchor == null) doc.pages.insertBefore(imported, first) else doc.pages.insertAfter(imported, anchor)
                    anchor = imported
                }
                doc.save(dest)
            }
        }
    }

    // ------------------------------------------------------------------ Compression

    /** Recompresse les images ; renvoie false si le fichier n'a pas pu être allégé. */
    fun compress(context: Context, src: File, level: CompressLevel, dest: File): Boolean {
        load(context, src).use { doc ->
            val done = IdentityHashMap<COSBase, PDImageXObject>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                    val key = x.cosObject
                    val already = done[key]
                    if (already != null) { res.put(name, already); continue }
                    if (x.isStencil || x.bitsPerComponent == 1) continue
                    if (x.width * x.height < 250 * 250) continue
                    val bmp = runCatching { x.image }.getOrNull() ?: continue
                    val scale = min(1f, level.maxSide.toFloat() / max(bmp.width, bmp.height))
                    val scaled = if (scale < 1f)
                        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1), (bmp.height * scale).roundToInt().coerceAtLeast(1), true)
                    else bmp.copy(Bitmap.Config.ARGB_8888, true)
                    // Sans masque de transparence d'origine : image opaque (pas de masque ajouté inutilement)
                    if (x.softMask == null && x.mask == null) scaled.setHasAlpha(false)
                    val jpeg = runCatching { JPEGFactory.createFromImage(doc, scaled, level.quality) }.getOrNull() ?: continue
                    done[key] = jpeg
                    res.put(name, jpeg)
                }
            }
            doc.save(dest)
        }
        if (dest.length() >= src.length()) {
            dest.delete()
            return false
        }
        return true
    }

    // ------------------------------------------------------------------ Tampons de page

    /** Repère d'affichage (origine en bas à gauche de la page visible, y vers le haut) → espace PDF. */
    private class Display(val toUser: Matrix, val w: Float, val h: Float)

    private fun display(page: PDPage): Display {
        val crop = page.cropBox
        val x0 = crop.lowerLeftX
        val y0 = crop.lowerLeftY
        val w = crop.width
        val h = crop.height
        val r = ((page.rotation % 360) + 360) % 360
        val m = when (r) {
            90 -> Matrix(0f, 1f, -1f, 0f, x0 + w, y0)
            180 -> Matrix(-1f, 0f, 0f, -1f, x0 + w, y0 + h)
            270 -> Matrix(0f, -1f, 1f, 0f, x0, y0 + h)
            else -> Matrix(1f, 0f, 0f, 1f, x0, y0)
        }
        return if (r % 180 == 0) Display(m, w, h) else Display(m, h, w)
    }

    private val helv get() = PDType1Font.HELVETICA
    private val helvBold get() = PDType1Font.HELVETICA_BOLD

    /** Helvetica (WinAnsi) couvre le français ; les autres caractères deviennent « ? » (ou sont omis). */
    fun encodable(s: String, font: PDType1Font = helv, replacement: String = "?"): String = buildString {
        for (ch in s) {
            val c = if (ch == '\t' || ch == '\n' || ch == '\r') " " else ch.toString()
            append(if (runCatching { font.encode(c) }.isSuccess) c else replacement)
        }
    }

    fun addPageNumbers(context: Context, src: File, position: NumberPosition, dest: File) {
        load(context, src).use { doc ->
            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, page ->
                val d = display(page)
                val label = "${i + 1} / $total"
                val size = (min(d.w, d.h) / 50f).coerceIn(8f, 12f)
                val tw = helv.getStringWidth(label) / 1000f * size
                val margin = size * 2.2f
                val (x, y) = when (position) {
                    NumberPosition.BOTTOM_CENTER -> (d.w - tw) / 2f to margin
                    NumberPosition.BOTTOM_RIGHT -> d.w - tw - margin to margin
                    NumberPosition.TOP_RIGHT -> d.w - tw - margin to d.h - margin - size
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(d.toUser)
                    cs.setNonStrokingColor(0.25f, 0.25f, 0.28f)
                    cs.beginText()
                    cs.setFont(helv, size)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(dest)
        }
    }

    fun watermark(context: Context, src: File, text: String, dest: File) {
        val label = encodable(text.trim().ifEmpty { "CONFIDENTIEL" }, helvBold)
        load(context, src).use { doc ->
            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.16f }
            for (page in doc.pages) {
                val d = display(page)
                val diag = kotlin.math.sqrt(d.w * d.w + d.h * d.h)
                val unit = helvBold.getStringWidth(label) / 1000f
                val size = (diag * 0.7f / unit.coerceAtLeast(0.1f)).coerceIn(18f, 140f)
                val tw = unit * size
                val angle = kotlin.math.atan2(d.h, d.w)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(d.toUser)
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(0.85f, 0.1f, 0.1f)
                    cs.beginText()
                    cs.setFont(helvBold, size)
                    val m = Matrix.getRotateInstance(angle.toDouble(), d.w / 2f, d.h / 2f)
                    m.translate(-tw / 2f, -size * 0.35f)
                    cs.setTextMatrix(m)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.save(dest)
        }
    }

    // ------------------------------------------------------------------ Sommaire

    fun outline(context: Context, file: File): List<OutlineEntry> = runCatching {
        load(context, file).use { doc ->
            val out = mutableListOf<OutlineEntry>()
            fun walk(node: PDOutlineNode, level: Int) {
                for (item: PDOutlineItem in node.children()) {
                    if (out.size > 2000) return
                    val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                    val index = if (page != null) doc.pages.indexOf(page) else -1
                    out += OutlineEntry(item.title?.trim().orEmpty().ifEmpty { "Sans titre" }, index, level)
                    walk(item, level + 1)
                }
            }
            doc.documentCatalog.documentOutline?.let { walk(it, 0) }
            out
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ Texte et recherche

    private class PositionStripper : PDFTextStripper() {
        val sb = StringBuilder()
        val boxes = ArrayList<RectF?>()

        init { sortByPosition = true }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val positions = textPositions ?: return
            for (tp in positions) {
                val u = tp.unicode ?: continue
                val h = max(tp.heightDir, tp.fontSizeInPt * 0.75f)
                val r = RectF(tp.xDirAdj, tp.yDirAdj - h, tp.xDirAdj + tp.widthDirAdj, tp.yDirAdj + h * 0.25f)
                for (c in u) { sb.append(c); boxes.add(r) }
            }
        }

        override fun writeWordSeparator() { sb.append(' '); boxes.add(null) }
        override fun writeLineSeparator() { sb.append('\n'); boxes.add(null) }
    }

    /** Texte de chaque page avec positions (pour la recherche, la copie et l'OCR). */
    fun pageTexts(context: Context, file: File, password: String = ""): List<PageText> =
        load(context, file, password).use { doc ->
            List(doc.numberOfPages) { i ->
                runCatching {
                    val s = PositionStripper()
                    s.startPage = i + 1
                    s.endPage = i + 1
                    s.getText(doc)
                    PageText(s.sb.toString(), s.boxes)
                }.getOrElse { PageText("", emptyList()) }
            }
        }

    fun search(pages: List<PageText>, query: String): List<SearchHit> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        pages.forEachIndexed { pi, pt ->
            val hay = normalize(pt.text)
            var from = 0
            while (hits.size < 500) {
                val i = hay.indexOf(q, from)
                if (i < 0) break
                val rects = mergeRects((i until i + q.length).mapNotNull { pt.boxes.getOrNull(it) })
                val start = max(0, i - 30)
                val end = min(pt.text.length, i + q.length + 40)
                val snippet = (if (start > 0) "…" else "") + pt.text.substring(start, end).replace('\n', ' ').trim() + (if (end < pt.text.length) "…" else "")
                hits += SearchHit(pi, rects, snippet)
                from = i + q.length
            }
        }
        return hits
    }

    /** Minuscules sans accents, même longueur que l'original (indices conservés). */
    private fun normalize(s: String): String {
        val out = StringBuilder(s.length)
        for (c in s) {
            val base = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).firstOrNull() ?: c
            out.append(base.lowercaseChar())
        }
        return out.toString()
    }

    private fun mergeRects(rs: List<RectF>): List<RectF> {
        val out = mutableListOf<RectF>()
        for (r in rs) {
            val last = out.lastOrNull()
            if (last != null && kotlin.math.abs(last.centerY() - r.centerY()) < last.height() * 0.6f && r.left <= last.right + last.height()) {
                last.union(r)
            } else out += RectF(r)
        }
        return out
    }

    // ------------------------------------------------------------------ OCR

    /**
     * Rend le texte des pages scannées cherchable et copiable : reconnaissance (ML Kit, sur l'appareil)
     * puis ajout d'un calque de texte invisible. Les pages contenant déjà du texte sont laissées telles quelles.
     * Renvoie le nombre de pages traitées.
     */
    fun ocr(context: Context, src: File, dest: File, progress: (Int, Int) -> Unit): Int {
        val existing = runCatching { pageTexts(context, src) }.getOrDefault(emptyList())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var treated = 0
        try {
            val renderer = android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(src, android.os.ParcelFileDescriptor.MODE_READ_ONLY))
            renderer.use { r ->
                load(context, src).use { doc ->
                    val n = min(r.pageCount, doc.numberOfPages)
                    for (i in 0 until n) {
                        progress(i + 1, n)
                        if ((existing.getOrNull(i)?.text?.count { it.isLetterOrDigit() } ?: 0) > 25) continue
                        val page = r.openPage(i)
                        val ptsW = page.width.toFloat()
                        val ptsH = page.height.toFloat()
                        val scale = (2000f / max(ptsW, ptsH)).coerceIn(1.5f, 4f)
                        val bmp = Bitmap.createBitmap((ptsW * scale).roundToInt(), (ptsH * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                        bmp.recycle()
                        val words = result.textBlocks.flatMap { b -> b.lines.flatMap { l -> l.elements } }
                            .mapNotNull { el -> el.boundingBox?.let { el.text to it } }
                        addTextLayer(doc, i, words, ptsW * scale, ptsH * scale)
                        treated++
                    }
                    doc.save(dest)
                }
            }
        } finally {
            recognizer.close()
        }
        return treated
    }

    /**
     * Ajoute un calque de texte invisible (cherchable, copiable) sur la page [pageIndex].
     * [words] : mots et leur boîte, en pixels d'une image de [imgW]×[imgH] couvrant la page affichée.
     */
    fun addTextLayer(doc: PDDocument, pageIndex: Int, words: List<Pair<String, android.graphics.Rect>>, imgW: Float, imgH: Float) {
        if (words.isEmpty()) return
        val pdPage = doc.getPage(pageIndex)
        val d = display(pdPage)
        val kx = d.w / imgW
        val ky = d.h / imgH
        PDPageContentStream(doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(d.toUser)
            cs.beginText()
            cs.setRenderingMode(RenderingMode.NEITHER)
            for ((text, box) in words) {
                val word = encodable(text, helv, "")
                if (word.isBlank()) continue
                val h = box.height() * ky
                val w = box.width() * kx
                val size = (h * 0.85f).coerceAtLeast(1f)
                val natural = helv.getStringWidth(word) / 1000f * size
                cs.setFont(helv, size)
                cs.setHorizontalScaling((100f * w / natural.coerceAtLeast(0.1f)).coerceIn(10f, 400f))
                cs.setTextMatrix(Matrix.getTranslateInstance(box.left * kx, d.h - box.bottom * ky + h * 0.18f))
                cs.showText(word)
            }
            cs.endText()
        }
    }

    // ------------------------------------------------------------------ Images → PDF

    /** Une image par page (A4, orientée selon l'image). */
    fun imagesToPdf(context: Context, uris: List<Uri>, dest: File) {
        init(context)
        PDDocument(mem()).use { doc ->
            for (uri in uris) {
                val bmp = decode(context, uri, 2480) ?: continue
                val a4 = PDRectangle.A4
                val landscape = bmp.width > bmp.height
                val pw = if (landscape) a4.height else a4.width
                val ph = if (landscape) a4.width else a4.height
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                val margin = 18f
                val k = min((pw - 2 * margin) / bmp.width, (ph - 2 * margin) / bmp.height)
                val w = bmp.width * k
                val h = bmp.height * k
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, (pw - w) / 2f, (ph - h) / 2f, w, h) }
                bmp.recycle()
            }
            if (doc.numberOfPages == 0) error("Aucune image lisible")
            doc.save(dest)
        }
    }

    private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val s = info.size
                val k = min(1f, maxSide.toFloat() / max(s.width, s.height))
                if (k < 1f) decoder.setTargetSize((s.width * k).roundToInt().coerceAtLeast(1), (s.height * k).roundToInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    // ------------------------------------------------------------------ Formulaires

    fun formFields(context: Context, file: File): List<FormField> = runCatching {
        load(context, file).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return@use emptyList()
            form.fieldTree.mapNotNull { f -> toFormField(f) }
        }
    }.getOrDefault(emptyList())

    private fun toFormField(f: PDField): FormField? {
        if (f !is PDTerminalField || f is PDPushButton || f is PDSignatureField || f.isReadOnly) return null
        val label = (f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: f.fullyQualifiedName).trim()
        return when (f) {
            is PDTextField -> FormField(f.fullyQualifiedName, label, FieldKind.TEXT, f.valueAsString.orEmpty(), multiline = f.isMultiline)
            is PDCheckBox -> FormField(f.fullyQualifiedName, label, FieldKind.CHECK, if (f.isChecked) "true" else "false")
            is PDRadioButton -> FormField(f.fullyQualifiedName, label, FieldKind.RADIO, f.value.orEmpty(), f.onValues.toList().sorted())
            is PDChoice -> FormField(f.fullyQualifiedName, label, FieldKind.CHOICE, f.value.firstOrNull().orEmpty(), f.optionsExportValues.ifEmpty { f.optionsDisplayValues })
            else -> null
        }
    }

    /** Remplit les champs ; renvoie le nombre de champs qui n'ont pas pu être écrits. */
    fun fillForm(context: Context, src: File, values: Map<String, String>, dest: File): Int {
        var failed = 0
        load(context, src).use { doc ->
            val form = doc.documentCatalog.acroForm ?: error("Pas de formulaire")
            for ((name, value) in values) {
                val f = form.getField(name) ?: continue
                val ok = runCatching {
                    when (f) {
                        is PDCheckBox -> if (value == "true") f.check() else f.unCheck()
                        is PDRadioButton -> if (value.isNotEmpty()) f.setValue(value)
                        is PDChoice -> f.setValue(value)
                        is PDTextField -> f.setValue(value)
                        else -> Unit
                    }
                }.isSuccess
                if (!ok) {
                    failed++
                    form.needAppearances = true
                    runCatching { f.cosObject.setString(com.tom_roush.pdfbox.cos.COSName.V, value) }
                }
            }
            doc.save(dest)
        }
        return failed
    }
}

/**
 * Interprète « 1-3, 5, 8- » en index de pages 0-based (dans [1, count]).
 * Renvoie null si la saisie est invalide.
 */
fun parsePageRanges(input: String, count: Int): List<List<Int>>? {
    val parts = input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val groups = mutableListOf<List<Int>>()
    for (p in parts) {
        val m = Regex("^(\\d*)\\s*-\\s*(\\d*)$").find(p)
        val range = if (m != null) {
            val a = m.groupValues[1].toIntOrNull() ?: 1
            val b = m.groupValues[2].toIntOrNull() ?: count
            if (a < 1 || b > count || a > b) return null
            (a..b).map { it - 1 }
        } else {
            val n = p.toIntOrNull() ?: return null
            if (n !in 1..count) return null
            listOf(n - 1)
        }
        groups += range
    }
    return groups
}

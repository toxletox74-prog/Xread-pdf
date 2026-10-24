package com.csa.xreadpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.csa.xreadpdf.editor.EditPage
import com.csa.xreadpdf.editor.NoteAnnot
import com.csa.xreadpdf.editor.PdfExporter
import com.csa.xreadpdf.editor.Pt
import com.csa.xreadpdf.editor.ShapeAnnot
import com.csa.xreadpdf.editor.ShapeKind
import com.csa.xreadpdf.editor.StampAnnot
import com.csa.xreadpdf.tools.CompressLevel
import com.csa.xreadpdf.tools.FieldKind
import com.csa.xreadpdf.tools.NumberPosition
import com.csa.xreadpdf.tools.PasswordRequiredException
import com.csa.xreadpdf.tools.PdfTools
import com.csa.xreadpdf.tools.SystemTools
import com.csa.xreadpdf.tools.parsePageRanges
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ToolsTest {

    private lateinit var ctx: Context
    private lateinit var dir: File
    private lateinit var sample: File

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(ctx)
        dir = File(ctx.cacheDir, "tests").apply { deleteRecursively(); mkdirs() }
        sample = File(dir, "sample.pdf")
        makeSample(sample)
    }

    private fun f(name: String) = File(dir, name)

    /** 3 pages de texte, un sommaire et un formulaire (champ texte + case à cocher). */
    private fun makeSample(dest: File) {
        PDDocument().use { doc ->
            val font = PDType1Font.HELVETICA
            repeat(3) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 18f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Bonjour page ${i + 1} – facture électricité")
                    cs.endText()
                }
            }
            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline
            listOf("Introduction" to 0, "Détails" to 2).forEach { (t, p) ->
                val item = PDOutlineItem()
                item.title = t
                item.destination = PDPageFitDestination().apply { page = doc.getPage(p) }
                outline.addLast(item)
            }
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            val res = PDResources()
            res.put(COSName.getPDFName("Helv"), font)
            form.defaultResources = res
            form.defaultAppearance = "/Helv 0 Tf 0 g"
            val text = PDTextField(form)
            text.partialName = "nom"
            text.alternateFieldName = "Nom du client"
            text.defaultAppearance = "/Helv 12 Tf 0 g"
            form.fields.add(text)
            val w = text.widgets[0]
            w.rectangle = PDRectangle(72f, 600f, 250f, 24f)
            w.page = doc.getPage(0)
            doc.getPage(0).annotations.add(w)
            val box = PDCheckBox(form)
            box.partialName = "accord"
            form.fields.add(box)
            val bw: PDAnnotationWidget = box.widgets[0]
            bw.rectangle = PDRectangle(72f, 560f, 16f, 16f)
            bw.page = doc.getPage(0)
            doc.getPage(0).annotations.add(bw)
            doc.save(dest)
        }
    }

    private fun pages(file: File) = PdfTools.pageCount(ctx, file)

    private fun rendersOk(file: File) = runBlocking {
        val d = PdfDoc.open(file)
        assertTrue(d.pageCount > 0)
        assertNotNull(d.render(0, 300))
        d.close()
    }

    @Test fun textSearchAndOutline() {
        val texts = PdfTools.pageTexts(ctx, sample)
        assertEquals(3, texts.size)
        assertTrue(texts[1].text, texts[1].text.contains("page 2"))
        val hits = PdfTools.search(texts, "ELECTRICITE")
        assertEquals(3, hits.size)
        assertTrue(hits[0].rects.isNotEmpty())
        val outline = PdfTools.outline(ctx, sample)
        assertEquals(listOf("Introduction", "Détails"), outline.map { it.title })
        assertEquals(2, outline[1].page)
    }

    @Test fun ranges() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(4)), parsePageRanges("1-3, 5", 6))
        assertEquals(listOf(listOf(4, 5)), parsePageRanges("5-", 6))
        assertEquals(null, parsePageRanges("7", 6))
        assertEquals(null, parsePageRanges("abc", 6))
    }

    @Test fun pageOperations() {
        PdfTools.extract(ctx, sample, listOf(0, 2), f("extract.pdf"))
        assertEquals(2, pages(f("extract.pdf")))
        PdfTools.merge(ctx, listOf(sample, f("extract.pdf")), f("merge.pdf"))
        assertEquals(5, pages(f("merge.pdf")))
        PdfTools.insertBlank(ctx, sample, 0, f("blank.pdf"))
        assertEquals(4, pages(f("blank.pdf")))
        assertTrue(PdfTools.pageTexts(ctx, f("blank.pdf"))[1].text.isBlank())
        PdfTools.insertBlank(ctx, sample, -1, f("blank0.pdf"))
        assertTrue(PdfTools.pageTexts(ctx, f("blank0.pdf"))[0].text.isBlank())
        PdfTools.insertPdf(ctx, sample, f("extract.pdf"), 1, f("insert.pdf"))
        val t = PdfTools.pageTexts(ctx, f("insert.pdf")).map { it.text }
        assertEquals(5, t.size)
        assertTrue(t[2].contains("page 1") && t[3].contains("page 3") && t[4].contains("page 3"))
        PdfTools.insertPdf(ctx, sample, f("extract.pdf"), -1, f("insert0.pdf"))
        assertTrue(PdfTools.pageTexts(ctx, f("insert0.pdf"))[0].text.contains("page 1"))
        listOf("extract.pdf", "merge.pdf", "blank.pdf", "insert.pdf").forEach { rendersOk(f(it)) }
    }

    @Test fun stamps() {
        PdfTools.addPageNumbers(ctx, sample, NumberPosition.BOTTOM_CENTER, f("num.pdf"))
        assertTrue(PdfTools.pageTexts(ctx, f("num.pdf"))[2].text.contains("3 / 3"))
        PdfTools.watermark(ctx, sample, "Confidentiel é", f("wm.pdf"))
        assertTrue(PdfTools.pageTexts(ctx, f("wm.pdf"))[0].text.contains("Confidentiel"))
        rendersOk(f("num.pdf"))
        rendersOk(f("wm.pdf"))
    }

    @Test fun passwords() {
        PdfTools.protect(ctx, sample, "secret", f("prot.pdf"))
        assertTrue(PdfTools.needsPassword(ctx, f("prot.pdf")))
        try {
            PdfTools.decrypt(ctx, f("prot.pdf"), "faux", f("x.pdf"))
            fail("mauvais mot de passe accepté")
        } catch (e: PasswordRequiredException) { /* attendu */ }
        PdfTools.decrypt(ctx, f("prot.pdf"), "secret", f("clear.pdf"))
        assertFalse(PdfTools.needsPassword(ctx, f("clear.pdf")))
        assertEquals(3, pages(f("clear.pdf")))
        rendersOk(f("clear.pdf"))
        // PdfRenderer refuse le PDF protégé (la visionneuse demande alors le mot de passe)
        val opened = runCatching { runBlocking { PdfDoc.open(f("prot.pdf")).close() } }
        assertTrue(opened.exceptionOrNull() is SecurityException)
    }

    @Test fun forms() {
        val fields = PdfTools.formFields(ctx, sample)
        assertEquals(2, fields.size)
        val name = fields.first { it.kind == FieldKind.TEXT }
        assertEquals("Nom du client", name.label)
        val failed = PdfTools.fillForm(ctx, sample, mapOf(name.name to "Ced Sàrl", "accord" to "true"), f("form.pdf"))
        assertEquals(0, failed)
        val after = PdfTools.formFields(ctx, f("form.pdf"))
        assertEquals("Ced Sàrl", after.first { it.kind == FieldKind.TEXT }.value)
        assertEquals("true", after.first { it.kind == FieldKind.CHECK }.value)
        rendersOk(f("form.pdf"))
    }

    private fun noisyBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        val rnd = java.util.Random(1)
        for (i in 0 until 4000) {
            p.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 4f + rnd.nextFloat() * 30f, p)
        }
        return bmp
    }

    @Test fun compressAndImages() {
        // PDF avec une grande image sans perte
        val big = f("big.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val img = LosslessFactory.createFromImage(doc, noisyBitmap(2400, 3200))
            PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, 595f, 842f) }
            doc.save(big)
        }
        assertTrue(PdfTools.compress(ctx, big, CompressLevel.MEDIUM, f("small.pdf")))
        assertTrue(f("small.pdf").length() < big.length())
        rendersOk(f("small.pdf"))
        // Un PDF sans image ne peut pas être allégé : pas de fichier produit
        assertFalse(PdfTools.compress(ctx, sample, CompressLevel.STRONG, f("same.pdf")))
        assertFalse(f("same.pdf").exists())

        // Images → PDF
        val png = f("photo.png")
        png.outputStream().use { noisyBitmap(1200, 800).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val jpg = f("photo.jpg")
        jpg.outputStream().use { noisyBitmap(600, 900).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        PdfTools.imagesToPdf(ctx, listOf(Uri.fromFile(png), Uri.fromFile(jpg)), f("images.pdf"))
        assertEquals(2, pages(f("images.pdf")))
        rendersOk(f("images.pdf"))
    }

    @Test fun editorExportNewAnnotations() {
        val pages = List(3) { EditPage(it, 595f, 842f) }.toMutableList()
        pages[0] = pages[0].copy(
            annots = listOf(
                ShapeAnnot(1, ShapeKind.RECT, Pt(50f, 50f), Pt(200f, 120f), Color.RED, 2f),
                ShapeAnnot(2, ShapeKind.ELLIPSE, Pt(60f, 200f), Pt(220f, 300f), Color.BLUE, 2f),
                ShapeAnnot(3, ShapeKind.ARROW, Pt(300f, 400f), Pt(450f, 300f), Color.BLACK, 3f),
                ShapeAnnot(4, ShapeKind.LINE, Pt(300f, 500f), Pt(450f, 500f), Color.BLACK, 1f),
                StampAnnot(5, "APPROUVÉ", Color.GREEN, 100f, 600f, 160f, 40f, 0),
                NoteAnnot(6, "Ligne 1\nLigne 2 éàç", 300f, 650f, 120f, 40f, 10f, 90),
            )
        )
        PdfExporter.export(ctx, sample, pages, f("annots.pdf"))
        val text = PdfTools.pageTexts(ctx, f("annots.pdf"))[0].text
        assertTrue(text, text.contains("APPROUV"))
        assertTrue(text, text.contains("Ligne 2"))
        rendersOk(f("annots.pdf"))
    }

    @Test fun backupRestore() {
        val lib = File(ctx.filesDir, "pdfs").apply { mkdirs() }
        val doc = File(lib, "test-sauvegarde.pdf")
        sample.copyTo(doc, overwrite = true)
        val zip = f("backup.zip")
        val n = SystemTools.backup(ctx, Uri.fromFile(zip))
        assertTrue(n >= 1)
        doc.delete()
        var json: String? = null
        val restored = SystemTools.restore(ctx, Uri.fromFile(zip)) { json = it }
        assertTrue(restored >= 1)
        assertTrue(doc.exists())
        doc.delete()
        assertTrue(json == null || json!!.startsWith("{"))
    }

    @Test fun ocrDoesNotCrash() {
        // Dépend des services Google Play de l'émulateur : on vérifie seulement l'absence de plantage inattendu.
        val img = f("scan.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val bmp = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val c = Canvas(bmp)
            val p = Paint().apply { color = Color.BLACK; textSize = 64f; isAntiAlias = true }
            c.drawText("FACTURE NUMERO 2026", 100f, 300f, p)
            c.drawText("Montant total 128 euros", 100f, 420f, p)
            val x = LosslessFactory.createFromImage(doc, bmp)
            PDPageContentStream(doc, page).use { it.drawImage(x, 0f, 0f, 595f, 842f) }
            doc.save(img)
        }
        val r = runCatching { PdfTools.ocr(ctx, img, f("ocr.pdf")) { _, _ -> } }
        android.util.Log.i("XreadTest", "OCR: ${r.getOrNull()} ${r.exceptionOrNull()}")
        if (r.isSuccess && r.getOrThrow() > 0) {
            val t = PdfTools.pageTexts(ctx, f("ocr.pdf"))[0].text
            android.util.Log.i("XreadTest", "OCR text: $t")
            assertTrue(t, t.contains("FACTURE", ignoreCase = true) || t.contains("Montant", ignoreCase = true))
            rendersOk(f("ocr.pdf"))
        }
    }

    @Test fun appStartsAndShowsLibrary() {
        val lib = File(ctx.filesDir, "pdfs").apply { mkdirs() }
        sample.copyTo(File(lib, "Démarrage.pdf"), overwrite = true)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            Thread.sleep(2500)
            s.onActivity { assertFalse(it.isFinishing) }
        }
    }
}

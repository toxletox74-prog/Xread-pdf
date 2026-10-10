package com.csa.xreadpdf.editor

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * Applique les modifications au PDF source avec PdfBox-Android et écrit le résultat dans [dest].
 * Le contenu d'origine reste vectoriel : les annotations sont AJOUTÉES au flux de chaque page
 * (mode append, contexte graphique isolé), puis rotations, suppressions et ordre sont appliqués.
 */
object PdfExporter {

    private val font get() = PDType1Font.HELVETICA

    /**
     * À la première utilisation, PdfBox indexe les polices du système (plusieurs secondes).
     * Appelé en tâche de fond à l'ouverture de l'éditeur pour que l'enregistrement soit rapide.
     */
    fun warmUp(context: Context) {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            font.encode("é")
        }
    }

    fun export(context: Context, source: File, pages: List<EditPage>, dest: File) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(source, MemoryUsageSetting.setupMixed(48L * 1024 * 1024)).use { doc ->
            if (doc.isEncrypted) doc.setAllSecurityToBeRemoved(true)

            val originals = (0 until doc.numberOfPages).map { doc.getPage(it) }
            for (ep in pages) {
                val page = originals[ep.src]
                val r0 = normDeg(page.rotation)
                if (ep.annots.isNotEmpty()) drawAnnots(doc, page, r0, ep)
                if (ep.rotation != 0) page.rotation = normDeg(r0 + ep.rotation)
            }

            val order = pages.map { it.src }
            if (order != originals.indices.toList()) {
                val tree = doc.pages
                originals.forEach { tree.remove(it) }
                order.forEach { tree.add(originals[it]) }
            }
            doc.save(dest)
        }
    }

    private fun drawAnnots(doc: PDDocument, page: PDPage, r0: Int, ep: EditPage) {
        val crop = page.cropBox
        val x0 = crop.lowerLeftX
        val y0 = crop.lowerLeftY
        val w = crop.width
        val h = crop.height
        // Taille affichée par PdfRenderer (CropBox tournée de /Rotate)
        val dw = if (r0 % 180 == 0) w else h
        val dh = if (r0 % 180 == 0) h else w

        // Repère d'affichage (X vers la droite, Y vers le HAUT, en points) → espace utilisateur PDF
        val toUser = when (r0) {
            90 -> Matrix(0f, 1f, -1f, 0f, x0 + w, y0)
            180 -> Matrix(-1f, 0f, 0f, -1f, x0 + w, y0 + h)
            270 -> Matrix(0f, -1f, 1f, 0f, x0, y0 + h)
            else -> Matrix(1f, 0f, 0f, 1f, x0, y0)
        }

        val highlightGs = PDExtendedGraphicsState().apply {
            setStrokingAlphaConstant(0.4f)
            setBlendMode(BlendMode.MULTIPLY)
        }

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(toUser)
            // Les tailles mémorisées viennent de PdfRenderer : on les recale sur la CropBox
            cs.transform(Matrix.getScaleInstance(dw / ep.width, dh / ep.height))
            val top = ep.height

            for (a in ep.annots) {
                cs.saveGraphicsState()
                when (a) {
                    is InkAnnot -> {
                        if (a.highlighter) cs.setGraphicsStateParameters(highlightGs)
                        stroke(cs, a.color, a.width, listOf(a.points.map { Pt(it.x, top - it.y) }))
                    }
                    is TextAnnot -> {
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(a.rotation.toDouble()), a.x, top - a.y))
                        drawText(cs, a)
                    }
                    is SignAnnot -> {
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(a.rotation.toDouble()), a.x, top - a.y))
                        val k = a.width
                        stroke(cs, a.sig.color, a.sig.width * k, a.sig.strokes.map { s -> s.map { Pt(it.x * k, -it.y * k) } })
                    }
                }
                cs.restoreGraphicsState()
            }
        }
    }

    /** Tracés lissés ; les points sont déjà en repère Y vers le haut. */
    private fun stroke(cs: PDPageContentStream, color: Int, width: Float, strokes: List<List<Pt>>) {
        cs.setStrokingColor(r(color), g(color), b(color))
        cs.setLineWidth(width)
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        for (pts in strokes) {
            if (pts.isEmpty()) continue
            var cur = pts[0]
            smoothPath(
                pts,
                moveTo = { cs.moveTo(it.x, it.y); cur = it },
                lineTo = { cs.lineTo(it.x, it.y); cur = it },
                quadTo = { q, e ->
                    // Quadratique → cubique
                    val c1 = cur + (q - cur) * (2f / 3f)
                    val c2 = e + (q - e) * (2f / 3f)
                    cs.curveTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                    cur = e
                },
            )
        }
        cs.stroke()
    }

    private fun drawText(cs: PDPageContentStream, a: TextAnnot) {
        cs.setNonStrokingColor(r(a.color), g(a.color), b(a.color))
        cs.beginText()
        cs.setFont(font, a.size)
        cs.newLineAtOffset(0f, -TextMetrics.ASCENT * a.size)
        a.text.split('\n').forEachIndexed { i, line ->
            if (i > 0) cs.newLineAtOffset(0f, -TextMetrics.LINE * a.size)
            cs.showText(encodable(line))
        }
        cs.endText()
    }

    /** Helvetica (WinAnsi) couvre le français ; tout caractère hors jeu devient « ? ». */
    private fun encodable(s: String): String = buildString {
        for (ch in s) {
            val c = if (ch == '\t') " " else ch.toString()
            append(if (runCatching { font.encode(c) }.isSuccess) c else "?")
        }
    }

    private fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun b(c: Int) = (c and 0xFF) / 255f
}

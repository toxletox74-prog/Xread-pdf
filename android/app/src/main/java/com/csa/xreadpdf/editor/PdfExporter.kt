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
    private val bold get() = PDType1Font.HELVETICA_BOLD

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
                    is ShapeAnnot -> drawShape(cs, a, top)
                    is StampAnnot -> {
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(a.rotation.toDouble()), a.x, top - a.y))
                        drawStamp(cs, a)
                    }
                    is NoteAnnot -> {
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(a.rotation.toDouble()), a.x, top - a.y))
                        drawNote(cs, a)
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

    private fun drawShape(cs: PDPageContentStream, a: ShapeAnnot, top: Float) {
        fun f(p: Pt) = Pt(p.x, top - p.y)
        val p1 = f(a.a)
        val p2 = f(a.b)
        cs.setStrokingColor(r(a.color), g(a.color), b(a.color))
        cs.setLineWidth(a.width)
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        when (a.kind) {
            ShapeKind.RECT -> {
                cs.addRect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y))
                cs.stroke()
            }
            ShapeKind.ELLIPSE -> {
                ellipse(cs, (p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f, kotlin.math.abs(p2.x - p1.x) / 2f, kotlin.math.abs(p2.y - p1.y) / 2f)
                cs.stroke()
            }
            ShapeKind.LINE, ShapeKind.ARROW -> {
                cs.moveTo(p1.x, p1.y)
                cs.lineTo(p2.x, p2.y)
                if (a.kind == ShapeKind.ARROW) {
                    val (l, rr) = arrowHead(a.a, a.b, a.width)
                    val fl = f(l)
                    val fr = f(rr)
                    cs.moveTo(fl.x, fl.y)
                    cs.lineTo(p2.x, p2.y)
                    cs.lineTo(fr.x, fr.y)
                }
                cs.stroke()
            }
        }
    }

    private fun ellipse(cs: PDPageContentStream, cx: Float, cy: Float, rx: Float, ry: Float) {
        val k = 0.5523f
        cs.moveTo(cx + rx, cy)
        cs.curveTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
        cs.curveTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
        cs.curveTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
        cs.curveTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
        cs.closePath()
    }

    /** Rectangle arrondi ; repère local y vers le haut, coin haut-gauche en (0, 0). */
    private fun roundRect(cs: PDPageContentStream, w: Float, h: Float, rad: Float) {
        val k = 0.5523f * rad
        cs.moveTo(rad, 0f)
        cs.lineTo(w - rad, 0f)
        cs.curveTo(w - rad + k, 0f, w, -rad + k, w, -rad)
        cs.lineTo(w, -h + rad)
        cs.curveTo(w, -h + rad - k, w - rad + k, -h, w - rad, -h)
        cs.lineTo(rad, -h)
        cs.curveTo(rad - k, -h, 0f, -h + rad - k, 0f, -h + rad)
        cs.lineTo(0f, -rad)
        cs.curveTo(0f, -rad + k, rad - k, 0f, rad, 0f)
        cs.closePath()
    }

    private fun drawStamp(cs: PDPageContentStream, a: StampAnnot) {
        val size = stampFont(a.height)
        val lw = size * 0.14f
        cs.setStrokingColor(r(a.color), g(a.color), b(a.color))
        cs.setNonStrokingColor(r(a.color), g(a.color), b(a.color))
        cs.setLineWidth(lw)
        roundRect(cs, a.width, a.height, size * 0.35f)
        cs.stroke()
        val label = encodable(a.text, bold)
        val unit = bold.getStringWidth(label) / 1000f
        val fs = minOf(size, (a.width - size * 1.1f) / unit.coerceAtLeast(0.01f))
        val tw = unit * fs
        cs.beginText()
        cs.setFont(bold, fs)
        cs.newLineAtOffset((a.width - tw) / 2f, -a.height / 2f - fs * 0.35f)
        cs.showText(label)
        cs.endText()
    }

    private fun drawNote(cs: PDPageContentStream, a: NoteAnnot) {
        cs.setNonStrokingColor(r(NOTE_FILL), g(NOTE_FILL), b(NOTE_FILL))
        cs.setStrokingColor(r(NOTE_BORDER), g(NOTE_BORDER), b(NOTE_BORDER))
        cs.setLineWidth(0.8f)
        roundRect(cs, a.width, a.height, a.size * 0.3f)
        cs.fillAndStroke()
        val pad = NOTE_PAD * a.size
        cs.setNonStrokingColor(r(NOTE_TEXT), g(NOTE_TEXT), b(NOTE_TEXT))
        cs.beginText()
        cs.setFont(font, a.size)
        cs.newLineAtOffset(pad, -pad - TextMetrics.ASCENT * a.size)
        a.text.split('\n').forEachIndexed { i, line ->
            if (i > 0) cs.newLineAtOffset(0f, -TextMetrics.LINE * a.size)
            cs.showText(encodable(line))
        }
        cs.endText()
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
    private fun encodable(s: String, f: PDType1Font = font): String = buildString {
        for (ch in s) {
            val c = if (ch == '\t') " " else ch.toString()
            append(if (runCatching { f.encode(c) }.isSuccess) c else "?")
        }
    }

    private fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun b(c: Int) = (c and 0xFF) / 255f
}

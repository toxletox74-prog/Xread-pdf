package com.csa.xreadpdf.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.csa.xreadpdf.PdfDoc
import java.io.File
import java.util.Collections

enum class Tool(val label: String) {
    SELECT("Sélection"),
    PEN("Stylo"),
    HIGHLIGHTER("Surligner"),
    TEXT("Texte"),
    SIGNATURE("Signature"),
    SHAPE("Formes"),
    STAMP("Tampon"),
    NOTE("Note"),
    ERASER("Gomme"),
}

/** Boîte de dialogue de saisie de texte : création (editId null) ou modification. */
data class TextDialog(val editId: Long?, val at: Pt, val initial: String)

/** Mesure d'un texte en points (fournie par l'UI, qui seule sait mettre en page). */
interface TextSizer {
    fun measure(text: String, fontSize: Float, bold: Boolean = false): Pt
    fun size(a: TextAnnot): Pt = measure(a.text, a.size)
}

/** Saisie d'une note : création (editId null) ou modification. */
data class NoteDialog(val editId: Long?, val at: Pt, val initial: String)

/**
 * État d'une séance d'édition, conservé par le ViewModel (survit à la rotation de l'écran).
 * L'historique stocke des instantanés immuables de la liste de pages.
 */
class EditorSession(
    val file: File,
    val doc: PdfDoc,
    initialTool: Tool,
) {
    var pages by mutableStateOf(List(doc.pageCount) { EditPage(it, doc.pageSizes[it].width, doc.pageSizes[it].height) })
        private set

    private val undoStack = ArrayDeque<List<EditPage>>()
    private val redoStack = ArrayDeque<List<EditPage>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set

    var current by mutableIntStateOf(0)
        private set

    private var _tool by mutableStateOf(initialTool)
    var tool: Tool
        get() = _tool
        set(value) { _tool = value; if (value != Tool.SELECT) selected = null }

    var penColor by mutableIntStateOf(PEN_COLORS[0])
    var penWidth by mutableFloatStateOf(PEN_WIDTHS[1])
    var highlightColor by mutableIntStateOf(HIGHLIGHT_COLORS[0])
    var textColor by mutableIntStateOf(PEN_COLORS[0])
    var textSize by mutableFloatStateOf(TEXT_SIZES[1])
    var signature by mutableStateOf<Signature?>(null)
    var shapeKind by mutableStateOf(ShapeKind.RECT)
    var shapeColor by mutableIntStateOf(PEN_COLORS[2])
    var shapeWidth by mutableFloatStateOf(PEN_WIDTHS[1])
    var stamp by mutableStateOf(STAMPS[0])
    var noteDialog by mutableStateOf<NoteDialog?>(null)

    var selected by mutableStateOf<Long?>(null)
    var textDialog by mutableStateOf<TextDialog?>(null)
    var padOpen by mutableStateOf(false)
    var saving by mutableStateOf(false)

    private var gestureStart: List<EditPage>? = null
    private var nextId = 1L
    fun newId(): Long = nextId++

    val page: EditPage get() = pages[current.coerceIn(0, pages.lastIndex)]

    // ---- Historique ----

    fun commit(new: List<EditPage>) {
        if (new == pages) return
        push(pages)
        pages = new
    }

    private fun push(old: List<EditPage>) {
        undoStack.addLast(old)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        dirty = true
        sync()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(pages)
        pages = prev
        sync()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(pages)
        pages = next
        sync()
    }

    private fun sync() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        current = current.coerceIn(0, pages.lastIndex)
        if (selected != null && page.annots.none { it.id == selected }) selected = null
    }

    /** Geste continu : modifications "live" sans historique, validées à la fin en une seule étape. */
    fun beginGesture() { gestureStart = pages }
    fun live(new: List<EditPage>) { pages = new }
    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != pages) push(start)
    }
    fun cancelGesture() {
        gestureStart?.let { pages = it }
        gestureStart = null
    }

    // ---- Annotations de la page courante ----

    fun mapPage(index: Int = current, f: (EditPage) -> EditPage): List<EditPage> =
        pages.toMutableList().also { it[index] = f(it[index]) }

    fun mapAnnots(f: (List<Annot>) -> List<Annot>): List<EditPage> =
        mapPage { it.copy(annots = f(it.annots)) }

    fun replace(a: Annot): List<EditPage> = mapAnnots { l -> l.map { if (it.id == a.id) a else it } }

    fun annot(id: Long?): Annot? = id?.let { i -> page.annots.find { it.id == i } }

    fun deleteSelected() {
        val id = selected ?: return
        commit(mapAnnots { l -> l.filterNot { it.id == id } })
        selected = null
    }

    fun boxSize(a: Placed, sizer: TextSizer): Pt = when (a) {
        is TextAnnot -> sizer.size(a)
        is SignAnnot -> Pt(a.width, a.height)
        is StampAnnot -> Pt(a.width, a.height)
        is NoteAnnot -> Pt(a.width, a.height)
    }

    /** Élément posé sous le point [p] (le plus haut d'abord). */
    fun hitPlaced(p: Pt, tol: Float, sizer: TextSizer): Placed? =
        page.annots.asReversed().filterIsInstance<Placed>().firstOrNull { a ->
            val l = a.toLocal(p)
            val s = boxSize(a, sizer)
            l.x in -tol..(s.x + tol) && l.y in -tol..(s.y + tol)
        }

    fun hitAny(p: Pt, tol: Float, sizer: TextSizer): List<Long> =
        page.annots.filter { a ->
            when (a) {
                is InkAnnot -> a.hit(p, tol)
                is ShapeAnnot -> a.hit(p, tol)
                is Placed -> {
                    val l = a.toLocal(p)
                    val s = boxSize(a, sizer)
                    l.x in -tol..(s.x + tol) && l.y in -tol..(s.y + tol)
                }
            }
        }.map { it.id }

    // ---- Texte ----

    fun openNewText(at: Pt) { textDialog = TextDialog(null, at, "") }
    fun openEditText(a: TextAnnot) { textDialog = TextDialog(a.id, Pt(a.x, a.y), a.text) }

    fun confirmText(text: String) {
        val d = textDialog ?: return
        textDialog = null
        val clean = text.trimEnd()
        val existing = annot(d.editId) as? TextAnnot
        if (existing != null) {
            if (clean.isBlank()) {
                commit(mapAnnots { l -> l.filterNot { it.id == existing.id } })
                selected = null
            } else {
                commit(replace(existing.copy(text = clean)))
            }
        } else if (clean.isNotBlank()) {
            val a = TextAnnot(newId(), clean, d.at.x, d.at.y, textSize, textColor, page.rotation)
            commit(mapAnnots { it + a })
            tool = Tool.SELECT
            selected = a.id
        }
    }

    // ---- Tampon ----

    /** Pose le tampon choisi centré sur [at]. */
    fun placeStamp(at: Pt, sizer: TextSizer) {
        val p = page
        val size = (minOf(p.width, p.height) * 0.045f).coerceIn(12f, 40f)
        val t = sizer.measure(stamp.text, size, bold = true)
        val w = t.x + size * 1.4f
        val h = size * 1.9f
        val anchor = at - Pt(w / 2f, h / 2f).rotate(-p.rotation)
        val a = StampAnnot(newId(), stamp.text, stamp.color, anchor.x, anchor.y, w, h, p.rotation)
        commit(mapAnnots { it + a })
        tool = Tool.SELECT
        selected = a.id
    }

    // ---- Note ----

    fun openNewNote(at: Pt) { noteDialog = NoteDialog(null, at, "") }
    fun openEditNote(a: NoteAnnot) { noteDialog = NoteDialog(a.id, Pt(a.x, a.y), a.text) }

    fun confirmNote(text: String, sizer: TextSizer) {
        val d = noteDialog ?: return
        noteDialog = null
        val clean = text.trim()
        val existing = annot(d.editId) as? NoteAnnot
        fun box(size: Float): Pt {
            val m = sizer.measure(clean, size)
            return Pt(m.x + 2 * NOTE_PAD * size, m.y + 2 * NOTE_PAD * size)
        }
        if (existing != null) {
            if (clean.isEmpty()) {
                commit(mapAnnots { l -> l.filterNot { it.id == existing.id } })
                selected = null
            } else {
                val b = box(existing.size)
                commit(replace(existing.copy(text = clean, width = b.x, height = b.y)))
            }
        } else if (clean.isNotEmpty()) {
            val b = box(NOTE_SIZE)
            val a = NoteAnnot(newId(), clean, d.at.x, d.at.y, b.x, b.y, NOTE_SIZE, page.rotation)
            commit(mapAnnots { it + a })
            tool = Tool.SELECT
            selected = a.id
        }
    }

    // ---- Signature ----

    /** Pose la signature centrée sur [at], largeur par défaut ~35 % du petit côté. */
    fun placeSignature(at: Pt, sig: Signature) {
        val p = page
        val w = minOf(p.width, p.height) * 0.35f
        val h = w / sig.aspect
        val anchor = at - Pt(w / 2f, h / 2f).rotate(-p.rotation)
        val a = SignAnnot(newId(), sig, anchor.x, anchor.y, w, p.rotation)
        commit(mapAnnots { it + a })
        tool = Tool.SELECT
        selected = a.id
    }

    // ---- Pages ----

    fun goTo(index: Int) {
        current = index.coerceIn(0, pages.lastIndex)
        selected = null
    }

    fun rotatePage(index: Int) =
        commit(mapPage(index) { it.copy(rotation = (it.rotation + 90) % 360) })

    fun deletePage(index: Int): Boolean {
        if (pages.size <= 1) return false
        commit(pages.filterIndexed { i, _ -> i != index })
        current = current.coerceIn(0, pages.lastIndex)
        selected = null
        return true
    }

    fun movePage(index: Int, delta: Int) {
        val to = index + delta
        if (to !in pages.indices) return
        commit(pages.toMutableList().also { Collections.swap(it, index, to) })
        current = when (current) { index -> to; to -> index; else -> current }
    }
}

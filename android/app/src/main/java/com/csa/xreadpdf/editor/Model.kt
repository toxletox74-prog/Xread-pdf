package com.csa.xreadpdf.editor

import kotlin.math.hypot

/**
 * Toutes les coordonnées d'édition sont en POINTS PDF, dans l'espace "intrinsèque"
 * de la page : la page telle que PdfRenderer l'affiche (CropBox + /Rotate d'origine),
 * origine en haut à gauche, y vers le bas. La rotation ajoutée par l'utilisateur
 * (EditPage.rotation) est appliquée par-dessus, à l'affichage comme à l'export.
 */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
}

fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)

/** Rotation horaire (repère y vers le bas) d'un multiple de 90°. */
fun Pt.rotate(deg: Int): Pt = when (normDeg(deg)) {
    90 -> Pt(-y, x)
    180 -> Pt(-x, -y)
    270 -> Pt(y, -x)
    else -> this
}

fun normDeg(deg: Int): Int = ((deg % 360) + 360) % 360

/**
 * Signature vectorielle. Points en unités de LARGEUR de signature :
 * x dans [0, 1], y dans [0, 1 / aspect]. [width] = épaisseur du trait, même unité.
 */
data class Signature(
    val id: String,
    val strokes: List<List<Pt>>,
    val aspect: Float,
    val color: Int,
    val width: Float,
)

sealed interface Annot { val id: Long }

data class InkAnnot(
    override val id: Long,
    val points: List<Pt>,
    val color: Int,
    val width: Float,
    val highlighter: Boolean,
) : Annot

/**
 * Élément posé (texte, signature) : ancré par son coin haut-gauche (x, y).
 * [rotation] = rotation de la page au moment de la pose : l'élément est dessiné
 * tourné de -rotation, pour apparaître droit dans la vue tournée.
 */
sealed interface Placed : Annot {
    val x: Float
    val y: Float
    val rotation: Int
    fun at(x: Float, y: Float): Placed
}

data class TextAnnot(
    override val id: Long,
    val text: String,
    override val x: Float,
    override val y: Float,
    val size: Float,
    val color: Int,
    override val rotation: Int,
) : Placed {
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
}

data class SignAnnot(
    override val id: Long,
    val sig: Signature,
    override val x: Float,
    override val y: Float,
    val width: Float,
    override val rotation: Int,
) : Placed {
    val height: Float get() = width / sig.aspect
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
}

enum class ShapeKind(val label: String) { RECT("Rectangle"), ELLIPSE("Cercle"), LINE("Ligne"), ARROW("Flèche") }

/** Forme tracée de [a] à [b] (coordonnées de page). */
data class ShapeAnnot(
    override val id: Long,
    val kind: ShapeKind,
    val a: Pt,
    val b: Pt,
    val color: Int,
    val width: Float,
) : Annot

/** Tampon (« PAYÉ », « APPROUVÉ »…) : cadre de [width]×[height] points, texte en gras ajusté au cadre. */
data class StampAnnot(
    override val id: Long,
    val text: String,
    val color: Int,
    override val x: Float,
    override val y: Float,
    val width: Float,
    val height: Float,
    override val rotation: Int,
) : Placed {
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
}

/** Note (post-it) : fond jaune, texte de [size] points, cadre de [width]×[height] points. */
data class NoteAnnot(
    override val id: Long,
    val text: String,
    override val x: Float,
    override val y: Float,
    val width: Float,
    val height: Float,
    val size: Float,
    override val rotation: Int,
) : Placed {
    override fun at(x: Float, y: Float) = copy(x = x, y = y)
}

const val NOTE_FILL = 0xFFFFF1A6.toInt()
const val NOTE_BORDER = 0xFFE3BF2B.toInt()
const val NOTE_TEXT = 0xFF3A3000.toInt()
/** Marge intérieure d'une note, en fraction de la taille du texte. */
const val NOTE_PAD = 0.7f
const val NOTE_SIZE = 10f

data class StampDef(val text: String, val color: Int)

val STAMPS = listOf(
    StampDef("PAYÉ", 0xFF188038.toInt()),
    StampDef("APPROUVÉ", 0xFF188038.toInt()),
    StampDef("REÇU", 0xFF1F4FD8.toInt()),
    StampDef("COPIE", 0xFF1F4FD8.toInt()),
    StampDef("REFUSÉ", 0xFFD93025.toInt()),
    StampDef("URGENT", 0xFFD93025.toInt()),
    StampDef("CONFIDENTIEL", 0xFFD93025.toInt()),
    StampDef("BROUILLON", 0xFF5F6368.toInt()),
)

/** Taille de police d'un tampon de hauteur [h]. */
fun stampFont(h: Float) = h / 1.9f

data class EditPage(
    /** Index de la page dans le PDF source. */
    val src: Int,
    /** Taille intrinsèque en points. */
    val width: Float,
    val height: Float,
    /** Rotation ajoutée par l'utilisateur (0, 90, 180, 270). */
    val rotation: Int = 0,
    val annots: List<Annot> = emptyList(),
)

/** Métriques de ligne de la police d'aperçu (Roboto), reprises à l'export. */
object TextMetrics {
    const val ASCENT = 0.928f
    const val LINE = 1.172f
}

/** Coin de l'élément posé (local → page). */
fun Placed.corner(local: Pt): Pt = Pt(x, y) + local.rotate(-rotation)

/** Point de la page → repère local de l'élément. */
fun Placed.toLocal(p: Pt): Pt = (p - Pt(x, y)).rotate(rotation)

/**
 * Parcourt une polyligne en la lissant (quadratiques passant par les milieux).
 * Partagé par l'aperçu Compose et l'export PDF pour un rendu identique.
 */
inline fun smoothPath(
    points: List<Pt>,
    moveTo: (Pt) -> Unit,
    lineTo: (Pt) -> Unit,
    quadTo: (ctrl: Pt, end: Pt) -> Unit,
) {
    if (points.isEmpty()) return
    moveTo(points[0])
    when (points.size) {
        1 -> lineTo(points[0])
        2 -> lineTo(points[1])
        else -> {
            for (i in 1 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                quadTo(a, Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f))
            }
            lineTo(points.last())
        }
    }
}

fun distToSegment(p: Pt, a: Pt, b: Pt): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return dist(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return dist(p, Pt(a.x + t * dx, a.y + t * dy))
}

fun InkAnnot.hit(p: Pt, tol: Float): Boolean {
    val r = width / 2f + tol
    if (points.size == 1) return dist(p, points[0]) <= r
    for (i in 0 until points.size - 1) if (distToSegment(p, points[i], points[i + 1]) <= r) return true
    return false
}

fun ShapeAnnot.hit(p: Pt, tol: Float): Boolean {
    val r = width / 2f + tol
    return when (kind) {
        ShapeKind.LINE, ShapeKind.ARROW -> distToSegment(p, a, b) <= r
        ShapeKind.RECT, ShapeKind.ELLIPSE -> {
            val l = minOf(a.x, b.x) - r
            val t = minOf(a.y, b.y) - r
            val rr = maxOf(a.x, b.x) + r
            val bb = maxOf(a.y, b.y) + r
            p.x in l..rr && p.y in t..bb
        }
    }
}

/** Les deux segments de la pointe de flèche (en coordonnées de page). */
fun arrowHead(a: Pt, b: Pt, width: Float): Pair<Pt, Pt> {
    val len = dist(a, b).coerceAtLeast(0.01f)
    val ux = (b.x - a.x) / len
    val uy = (b.y - a.y) / len
    val head = maxOf(width * 4.5f, 9f).coerceAtMost(len * 0.6f)
    val c = kotlin.math.cos(Math.toRadians(28.0)).toFloat()
    val s = kotlin.math.sin(Math.toRadians(28.0)).toFloat()
    val l = Pt(b.x - head * (ux * c - uy * s), b.y - head * (uy * c + ux * s))
    val r = Pt(b.x - head * (ux * c + uy * s), b.y - head * (uy * c - ux * s))
    return l to r
}

val PEN_COLORS = listOf(0xFF1A1A1F.toInt(), 0xFF1F4FD8.toInt(), 0xFFD93025.toInt(), 0xFF188038.toInt())
val HIGHLIGHT_COLORS = listOf(0xFFFFE14D.toInt(), 0xFF8CF08C.toInt(), 0xFFFF9BD2.toInt(), 0xFF8FD3FF.toInt())
val SIGN_COLORS = listOf(0xFF1A1A1F.toInt(), 0xFF1F3FBF.toInt())
val PEN_WIDTHS = listOf(1.2f, 2.4f, 4.5f)
const val HIGHLIGHT_WIDTH = 12f
val TEXT_SIZES = listOf(10f, 14f, 20f)

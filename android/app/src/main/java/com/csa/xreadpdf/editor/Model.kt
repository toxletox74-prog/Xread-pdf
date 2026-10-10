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

private fun distToSegment(p: Pt, a: Pt, b: Pt): Float {
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

val PEN_COLORS = listOf(0xFF1A1A1F.toInt(), 0xFF1F4FD8.toInt(), 0xFFD93025.toInt(), 0xFF188038.toInt())
val HIGHLIGHT_COLORS = listOf(0xFFFFE14D.toInt(), 0xFF8CF08C.toInt(), 0xFFFF9BD2.toInt(), 0xFF8FD3FF.toInt())
val SIGN_COLORS = listOf(0xFF1A1A1F.toInt(), 0xFF1F3FBF.toInt())
val PEN_WIDTHS = listOf(1.2f, 2.4f, 4.5f)
const val HIGHLIGHT_WIDTH = 12f
val TEXT_SIZES = listOf(10f, 14f, 20f)

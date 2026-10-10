package com.csa.xreadpdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csa.xreadpdf.editor.Pt
import com.csa.xreadpdf.editor.SIGN_COLORS
import com.csa.xreadpdf.editor.Signature
import com.csa.xreadpdf.editor.smoothPath
import java.util.UUID

/** Chemin Compose lissé, identique au tracé exporté en PDF. */
fun smoothComposePath(points: List<Pt>): Path = Path().apply {
    smoothPath(
        points,
        moveTo = { moveTo(it.x, it.y) },
        lineTo = { lineTo(it.x, it.y) },
        quadTo = { c, e -> quadraticBezierTo(c.x, c.y, e.x, e.y) },
    )
}

/** Dessine une signature avec son coin haut-gauche en (0, 0) et une largeur [w]. */
fun DrawScope.drawSignature(sig: Signature, w: Float) {
    val style = Stroke(width = sig.width * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    sig.strokes.forEach { s -> drawPath(smoothComposePath(s.map { it * w }), Color(sig.color), style = style) }
}

/** Aperçu d'une signature, centré dans sa boîte. */
@Composable
fun SignaturePreview(sig: Signature, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = minOf(size.width, size.height * sig.aspect)
        val h = w / sig.aspect
        translate((size.width - w) / 2f, (size.height - h) / 2f) { drawSignature(sig, w) }
    }
}

/** Pad plein écran pour dessiner une nouvelle signature. */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (Signature) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableIntStateOf(SIGN_COLORS[0]) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("Nouvelle signature", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Signez dans le cadre avec le doigt. Elle sera enregistrée pour vos prochains documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.9f)
                        .clip(shape)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    current = listOf(down.position)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        val p = c.position
                                        if ((p - current.last()).getDistance() > 1.5f) current = current + p
                                        c.consume()
                                    }
                                    strokes.add(current)
                                    current = emptyList()
                                }
                            },
                    ) {
                        // Ligne de signature
                        val baseY = size.height * 0.72f
                        drawLine(
                            Color(0xFFB8AFA8), Offset(size.width * 0.08f, baseY), Offset(size.width * 0.92f, baseY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        )
                        val x = size.width * 0.08f
                        val s = 7.dp.toPx()
                        drawLine(Color(0xFFB8AFA8), Offset(x, baseY - 14.dp.toPx()), Offset(x + s, baseY - 14.dp.toPx() + s), 1.5.dp.toPx())
                        drawLine(Color(0xFFB8AFA8), Offset(x + s, baseY - 14.dp.toPx()), Offset(x, baseY - 14.dp.toPx() + s), 1.5.dp.toPx())

                        val style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        (strokes + listOf(current)).forEach { pts ->
                            if (pts.isNotEmpty()) drawPath(smoothComposePath(pts.map { Pt(it.x, it.y) }), Color(color), style = style)
                        }
                    }
                    if (strokes.isEmpty() && current.isEmpty()) {
                        Text(
                            "Signez ici",
                            color = Color(0xFFB8AFA8),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIGN_COLORS.forEach { c ->
                        ColorDot(Color(c), selected = c == color, onClick = { color = c })
                        Spacer(Modifier.size(8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) { Text("Annuler le trait") }
                    TextButton(enabled = strokes.isNotEmpty(), onClick = { strokes.clear() }) { Text("Effacer") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = { normalize(strokes, strokePx, color)?.let(onSave) },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}

/** Ramène les tracés à leur boîte englobante, en unités de largeur. */
private fun normalize(strokes: List<List<Offset>>, strokePx: Float, color: Int): Signature? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = strokePx
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val bw = (all.maxOf { it.x } + pad - minX).coerceAtLeast(1f)
    val bh = (all.maxOf { it.y } + pad - minY).coerceAtLeast(1f)
    return Signature(
        id = UUID.randomUUID().toString(),
        strokes = strokes.map { s -> s.map { Pt((it.x - minX) / bw, (it.y - minY) / bw) } },
        aspect = (bw / bh).coerceIn(0.2f, 20f),
        color = color,
        width = strokePx / bw,
    )
}

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 30.dp) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(size + 10.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier.border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)),
        )
    }
}

/** Écran « Mes signatures ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturesScreen(
    signatures: List<Signature>,
    onBack: () -> Unit,
    onAdd: (Signature) -> Unit,
    onDelete: (Signature) -> Unit,
) {
    StatusBarIcons(lightIcons = false)
    var pad by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Signature?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                title = { Text("Mes signatures") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pad = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nouvelle signature") },
                containerColor = Brand.Coral,
                contentColor = Brand.Ink,
            )
        },
    ) { padding ->
        if (signatures.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(Brand.Sun),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Gesture, contentDescription = null, tint = Brand.Ink, modifier = Modifier.size(44.dp))
                }
                Text("Aucune signature", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
                Text(
                    "Créez votre signature une fois, puis posez-la sur n'importe quel PDF en un geste.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(signatures, key = { it.id }) { sig ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(120.dp)) {
                            SignaturePreview(sig, Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 18.dp))
                            IconButton(onClick = { deleting = sig }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Color(0xFF8A7F79))
                            }
                        }
                    }
                }
            }
        }
    }

    if (pad) {
        SignaturePadDialog(onDismiss = { pad = false }, onSave = { onAdd(it); pad = false })
    }
    deleting?.let { sig ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer cette signature ?") },
            text = { Text("Les PDF déjà signés ne sont pas modifiés.") },
            confirmButton = {
                TextButton(onClick = { onDelete(sig); deleting = null }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

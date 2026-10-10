package com.csa.xreadpdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csa.xreadpdf.PdfDoc
import com.csa.xreadpdf.editor.Annot
import com.csa.xreadpdf.editor.EditPage
import com.csa.xreadpdf.editor.EditorSession
import com.csa.xreadpdf.editor.HIGHLIGHT_COLORS
import com.csa.xreadpdf.editor.HIGHLIGHT_WIDTH
import com.csa.xreadpdf.editor.InkAnnot
import com.csa.xreadpdf.editor.PEN_COLORS
import com.csa.xreadpdf.editor.PEN_WIDTHS
import com.csa.xreadpdf.editor.Placed
import com.csa.xreadpdf.editor.Pt
import com.csa.xreadpdf.editor.SignAnnot
import com.csa.xreadpdf.editor.Signature
import com.csa.xreadpdf.editor.TEXT_SIZES
import com.csa.xreadpdf.editor.TextAnnot
import com.csa.xreadpdf.editor.TextSizer
import com.csa.xreadpdf.editor.Tool
import com.csa.xreadpdf.editor.corner
import com.csa.xreadpdf.editor.dist
import com.csa.xreadpdf.editor.toLocal
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

/** Le texte est mis en page à 4× puis réduit : métriques plus précises aux petites tailles. */
private const val K = 4f

/** Mise en page des textes, en points (partagée par le dessin et les tests de toucher). */
private class ComposeTextSizer(private val measurer: TextMeasurer) : TextSizer {
    private val density = Density(K, 1f)
    fun layout(a: TextAnnot): TextLayoutResult =
        measurer.measure(a.text, TextStyle(color = Color(a.color), fontSize = a.size.sp), density = density)
    override fun size(a: TextAnnot): Pt {
        val l = layout(a)
        return Pt(l.size.width / K, l.size.height / K)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    s: EditorSession,
    signatures: List<Signature>,
    inLibrary: Boolean,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onSave: (asCopy: Boolean) -> Unit,
    onNewSignature: (Signature) -> Unit,
    onMessage: (String) -> Unit,
) {
    StatusBarIcons(lightIcons = false)
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val sizer = remember(measurer) { ComposeTextSizer(measurer) }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var pagesSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = !s.saving) { if (s.dirty) confirmExit = true else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (s.dirty) confirmExit = true else onClose() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer")
                    }
                },
                title = {
                    Column {
                        Text("Modifier", style = MaterialTheme.typography.titleMedium)
                        Text(
                            s.file.nameWithoutExtension,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = s::undo, enabled = s.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler")
                    }
                    IconButton(onClick = s::redo, enabled = s.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rétablir")
                    }
                    Button(
                        onClick = { if (inLibrary) confirmSave = true else onSave(true) },
                        enabled = s.dirty && !s.saving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Enregistrer") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    ToolOptions(s, signatures, sizer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToolBar(s, signatures)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            PageArea(s, sizer)
            PageNav(
                s,
                onPages = { pagesSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }

    if (s.saving) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Enregistrement du PDF…", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    s.textDialog?.let { d ->
        TextDialogUi(
            editing = d.editId != null,
            initial = d.initial,
            onDismiss = { s.textDialog = null },
            onConfirm = s::confirmText,
        )
    }

    if (s.padOpen) {
        SignaturePadDialog(
            onDismiss = { s.padOpen = false },
            onSave = { sig ->
                onNewSignature(sig)
                s.signature = sig
                s.padOpen = false
                s.tool = Tool.SIGNATURE
                onMessage("Touchez la page à l'endroit où signer")
            },
        )
    }

    if (pagesSheet) {
        PagesSheet(s, onDismiss = { pagesSheet = false }, onMessage = onMessage)
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Quitter sans enregistrer ?") },
            text = { Text("Les modifications apportées à ce PDF seront perdues.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onClose() }) {
                    Text("Quitter", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuer") } },
        )
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Enregistrer les modifications") },
            text = { Text("Remplacer le document d'origine, ou garder l'original et créer une copie modifiée ?") },
            confirmButton = {
                TextButton(onClick = { confirmSave = false; onSave(false) }) { Text("Remplacer") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmSave = false }) { Text("Annuler") }
                    TextButton(onClick = { confirmSave = false; onSave(true) }) { Text("Créer une copie") }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Page

@Composable
private fun PageArea(s: EditorSession, sizer: ComposeTextSizer) {
    val page = s.page
    var zoom by remember(s.current) { mutableFloatStateOf(1f) }
    var pan by remember(s.current) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(s.current) {
                // Zoom / déplacement à 2 doigts, intercepté avant les outils de dessin
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 6f)
                            val p = pan + event.calculatePan()
                            val maxX = size.width * (zoom - 1f) / 2f
                            val maxY = size.height * (zoom - 1f) / 2f
                            pan = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val margin = with(density) { 16.dp.toPx() }
        val bottomRoom = with(density) { 64.dp.toPx() } // place pour la navigation de pages
        val availW = (constraints.maxWidth - 2 * margin).coerceAtLeast(1f)
        val availH = (constraints.maxHeight - margin - bottomRoom).coerceAtLeast(1f)
        val turned = page.rotation % 180 != 0
        val ratio = if (turned) page.height / page.width else page.width / page.height
        var dw = availW
        var dh = dw / ratio
        if (dh > availH) { dh = availH; dw = dh * ratio }
        val iw = if (turned) dh else dw
        val ih = if (turned) dw else dh
        val pxPerPt = iw / page.width

        Box(
            Modifier
                .padding(bottom = with(density) { (bottomRoom - margin).coerceAtLeast(0f).toDp() })
                .size(with(density) { dw.toDp() }, with(density) { dh.toDp() })
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
                .shadow(6.dp, RoundedCornerShape(3.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(with(density) { iw.toDp() }, with(density) { ih.toDp() })
                    .graphicsLayer { rotationZ = page.rotation.toFloat() },
            ) {
                PageBitmap(s.doc, page.src, (iw * if (zoom > 1.3f) 2f else 1f).toInt().coerceIn(1, 2400))
                AnnotLayer(s, page, sizer, pxPerPt) { zoom }
            }
        }
    }
}

@Composable
private fun PageBitmap(doc: PdfDoc, index: Int, widthPx: Int) {
    var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, index, widthPx) {
        doc.render(index, widthPx)?.let { bitmap = it.asImageBitmap() }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AnnotLayer(
    s: EditorSession,
    page: EditPage,
    sizer: ComposeTextSizer,
    pxPerPt: Float,
    zoom: () -> Float,
) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(s, s.current, pxPerPt) {
                awaitEachGesture { handleGesture(s, sizer, pxPerPt, zoom()) }
            },
    ) {
        scale(pxPerPt, pivot = Offset.Zero) {
            page.annots.forEach { drawAnnot(it, sizer) }
        }
        // Cadre de sélection + poignée de redimensionnement
        val sel = page.annots.firstOrNull { it.id == s.selected } as? Placed
        if (sel != null) {
            val box = s.boxSize(sel, sizer)
            val corners = listOf(Pt(0f, 0f), Pt(box.x, 0f), Pt(box.x, box.y), Pt(0f, box.y))
                .map { sel.corner(it) * pxPerPt }
            val z = zoom()
            val path = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path, primary,
                style = Stroke(1.5.dp.toPx() / z, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / z, 8f / z))),
            )
            val h = corners[2]
            drawCircle(Color.White, radius = 10.dp.toPx() / z, center = Offset(h.x, h.y))
            drawCircle(primary, radius = 7.dp.toPx() / z, center = Offset(h.x, h.y))
        }
    }
}

private fun DrawScope.drawAnnot(a: Annot, sizer: ComposeTextSizer) {
    when (a) {
        is InkAnnot -> drawPath(
            smoothComposePath(a.points),
            Color(a.color),
            alpha = if (a.highlighter) 0.4f else 1f,
            style = Stroke(a.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = if (a.highlighter) BlendMode.Multiply else BlendMode.SrcOver,
        )
        is TextAnnot -> withTransform({
            translate(a.x, a.y)
            rotate(-a.rotation.toFloat(), pivot = Offset.Zero)
            scale(1f / K, 1f / K, pivot = Offset.Zero)
        }) { drawText(sizer.layout(a)) }
        is SignAnnot -> withTransform({
            translate(a.x, a.y)
            rotate(-a.rotation.toFloat(), pivot = Offset.Zero)
        }) { drawSignature(a.sig, a.width) }
    }
}

/** Suit un doigt jusqu'au relâcher. Renvoie false si un 2e doigt intervient (zoom). */
private suspend fun AwaitPointerEventScope.trackDrag(pointer: PointerId, onMove: (Offset) -> Unit): Boolean {
    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.count { it.pressed } > 1 || ev.changes.any { it.isConsumed }) return false
        val c = ev.changes.firstOrNull { it.id == pointer } ?: return true
        if (!c.pressed) {
            c.consume()
            return true
        }
        if (c.positionChanged()) {
            onMove(c.position)
            c.consume()
        }
    }
}

private suspend fun AwaitPointerEventScope.handleGesture(
    s: EditorSession,
    sizer: ComposeTextSizer,
    pxPerPt: Float,
    zoom: Float,
) {
    val down = awaitFirstDown()
    fun pt(o: Offset) = Pt(o.x / pxPerPt, o.y / pxPerPt)
    val p0 = pt(down.position)
    val tol = 18.dp.toPx() / pxPerPt / zoom

    when (s.tool) {
        Tool.PEN, Tool.HIGHLIGHTER -> {
            down.consume()
            val hl = s.tool == Tool.HIGHLIGHTER
            val color = if (hl) s.highlightColor else s.penColor
            val width = if (hl) HIGHLIGHT_WIDTH else s.penWidth
            val id = s.newId()
            val minStep = 1.dp.toPx() / pxPerPt / zoom
            var pts = listOf(p0)
            fun update() {
                val ink = InkAnnot(id, pts, color, width, hl)
                s.live(s.mapAnnots { l -> l.filterNot { it.id == id } + ink })
            }
            s.beginGesture()
            update()
            val ok = trackDrag(down.id) { o ->
                val p = pt(o)
                if (dist(p, pts.last()) >= minStep) {
                    pts = pts + p
                    update()
                }
            }
            if (ok) s.endGesture() else s.cancelGesture()
        }

        Tool.ERASER -> {
            down.consume()
            s.beginGesture()
            fun erase(p: Pt) {
                val hit = s.hitAny(p, tol, sizer).toSet()
                if (hit.isNotEmpty()) s.live(s.mapAnnots { l -> l.filterNot { it.id in hit } })
            }
            erase(p0)
            val ok = trackDrag(down.id) { erase(pt(it)) }
            if (ok) s.endGesture() else s.cancelGesture()
        }

        Tool.TEXT -> {
            val up = waitForUpOrCancellation() ?: return
            up.consume()
            val hit = s.hitPlaced(p0, tol, sizer)
            if (hit is TextAnnot) s.openEditText(hit) else s.openNewText(p0)
        }

        Tool.SIGNATURE -> {
            val up = waitForUpOrCancellation() ?: return
            up.consume()
            val sig = s.signature
            if (sig == null) s.padOpen = true else s.placeSignature(p0, sig)
        }

        Tool.SELECT -> {
            val previous = s.annot(s.selected) as? Placed
            val onHandle = previous != null &&
                dist(previous.corner(s.boxSize(previous, sizer)), p0) <= tol * 1.4f
            val target = if (onHandle) previous else s.hitPlaced(p0, tol, sizer)
            if (target == null) {
                s.selected = null
                return
            }
            down.consume()
            val wasSelected = s.selected == target.id
            s.selected = target.id
            val startBox = s.boxSize(target, sizer)
            val slop = viewConfiguration.touchSlop / pxPerPt / zoom
            var moved = false
            s.beginGesture()
            val ok = trackDrag(down.id) { o ->
                val p = pt(o)
                if (!moved && dist(p, p0) < slop) return@trackDrag
                moved = true
                val updated: Placed = if (onHandle) resize(target, startBox, p) else target.at(target.x + p.x - p0.x, target.y + p.y - p0.y)
                s.live(s.replace(updated))
            }
            when {
                !ok -> s.cancelGesture()
                moved -> s.endGesture()
                else -> {
                    s.cancelGesture()
                    if (wasSelected && !onHandle && target is TextAnnot) s.openEditText(target)
                }
            }
        }
    }
}

/** Redimensionne depuis la poignée (coin bas-droit), proportions conservées. */
private fun resize(a: Placed, box: Pt, p: Pt): Placed {
    val l = a.toLocal(p)
    val f = max(l.x / box.x.coerceAtLeast(1f), l.y / box.y.coerceAtLeast(1f)).coerceAtLeast(0.05f)
    return when (a) {
        is SignAnnot -> a.copy(width = (a.width * f).coerceIn(16f, 2000f))
        is TextAnnot -> a.copy(size = (a.size * f).coerceIn(4f, 160f))
    }
}

// ---------------------------------------------------------------- Navigation de pages

@Composable
private fun PageNav(s: EditorSession, onPages: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Brand.Night,
        contentColor = Brand.Cream,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { s.goTo(s.current - 1) }, enabled = s.current > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Page précédente",
                    tint = Brand.Cream.copy(alpha = if (s.current > 0) 1f else 0.35f))
            }
            Text(
                "Page ${s.current + 1} / ${s.pages.size}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = { s.goTo(s.current + 1) }, enabled = s.current < s.pages.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Page suivante",
                    tint = Brand.Cream.copy(alpha = if (s.current < s.pages.lastIndex) 1f else 0.35f))
            }
            VerticalDivider(Modifier.height(24.dp), color = Brand.Cream.copy(alpha = 0.25f))
            IconButton(onClick = onPages) {
                Icon(Icons.Filled.GridView, contentDescription = "Organiser les pages", tint = Brand.Sun)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagesSheet(s: EditorSession, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Organiser les pages", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pivoter, déplacer ou supprimer. Touchez une page pour l'ouvrir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(118.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            itemsIndexed(s.pages, key = { _, p -> p.src }) { i, p ->
                val current = i == s.current
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { s.goTo(i); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            PageThumb(s.doc, p)
                            Surface(
                                shape = CircleShape,
                                color = Brand.Night,
                                contentColor = Brand.Cream,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            if (p.annots.isNotEmpty()) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp)
                                        .clip(CircleShape).background(Brand.Coral),
                                )
                            }
                        }
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            SmallIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Avancer", enabled = i > 0) { s.movePage(i, -1) }
                            SmallIcon(Icons.Filled.RotateRight, "Pivoter") { s.rotatePage(i) }
                            SmallIcon(Icons.Filled.Delete, "Supprimer") {
                                if (!s.deletePage(i)) onMessage("Un PDF doit garder au moins une page")
                            }
                            SmallIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Reculer", enabled = i < s.pages.lastIndex) { s.movePage(i, 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(30.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PageThumb(doc: PdfDoc, p: EditPage) {
    val widthPx = with(LocalDensity.current) { 140.dp.roundToPx() }
    var bitmap by remember(doc, p.src, p.rotation) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, p.src, p.rotation) {
        doc.render(p.src, widthPx, p.rotation)?.let { bitmap = it.asImageBitmap() }
    }
    val b = bitmap
    if (b != null) {
        Image(
            b, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(2.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        )
    } else {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

// ---------------------------------------------------------------- Outils

private data class ToolDef(val tool: Tool, val icon: ImageVector)

private val TOOLS = listOf(
    ToolDef(Tool.SELECT, Icons.Filled.TouchApp),
    ToolDef(Tool.PEN, Icons.Filled.Draw),
    ToolDef(Tool.HIGHLIGHTER, Icons.Filled.BorderColor),
    ToolDef(Tool.TEXT, Icons.Filled.TextFields),
    ToolDef(Tool.SIGNATURE, Icons.Filled.Gesture),
    ToolDef(Tool.ERASER, Icons.Filled.CleaningServices),
)

@Composable
private fun ToolBar(s: EditorSession, signatures: List<Signature>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        TOOLS.forEach { def ->
            val active = s.tool == def.tool
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        s.tool = def.tool
                        if (def.tool == Tool.SIGNATURE && signatures.isEmpty()) s.padOpen = true
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 52.dp, height = 32.dp)
                        .clip(CircleShape)
                        .background(if (active) Brand.Coral else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        def.icon, contentDescription = null,
                        tint = if (active) Brand.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    def.tool.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolOptions(s: EditorSession, signatures: List<Signature>, sizer: ComposeTextSizer) {
    Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        when (s.tool) {
            Tool.SELECT -> {
                val sel = s.annot(s.selected) as? Placed
                if (sel == null) {
                    Hint("Touchez un texte ou une signature pour le déplacer. Pincez pour zoomer.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (sel is TextAnnot) {
                            AssistChip(
                                onClick = { s.openEditText(sel) },
                                label = { Text("Modifier le texte") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                        AssistChip(
                            onClick = s::deleteSelected,
                            label = { Text("Supprimer") },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            },
                        )
                        Text(
                            "Poignée ● pour agrandir",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Tool.PEN -> Row(verticalAlignment = Alignment.CenterVertically) {
                PEN_COLORS.forEach { c -> ColorDot(Color(c), c == s.penColor, { s.penColor = c }, size = 26.dp) }
                Spacer(Modifier.weight(1f))
                PEN_WIDTHS.forEach { w ->
                    WidthChoice(w, s.penWidth == w, Color(s.penColor)) { s.penWidth = w }
                }
            }
            Tool.HIGHLIGHTER -> Row(verticalAlignment = Alignment.CenterVertically) {
                HIGHLIGHT_COLORS.forEach { c -> ColorDot(Color(c), c == s.highlightColor, { s.highlightColor = c }, size = 26.dp) }
                Spacer(Modifier.width(12.dp))
                Hint("Glissez sur le texte à surligner")
            }
            Tool.TEXT -> Row(verticalAlignment = Alignment.CenterVertically) {
                PEN_COLORS.forEach { c -> ColorDot(Color(c), c == s.textColor, { s.textColor = c }, size = 26.dp) }
                Spacer(Modifier.weight(1f))
                TEXT_SIZES.forEachIndexed { i, size ->
                    SizeChoice(listOf("S", "M", "L")[i], s.textSize == size) { s.textSize = size }
                }
            }
            Tool.SIGNATURE -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Surface(
                        onClick = { s.padOpen = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Brand.Sun,
                        contentColor = Brand.Ink,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nouvelle", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(signatures, key = { it.id }) { sig ->
                    val active = s.signature?.id == sig.id
                    Box(
                        Modifier
                            .size(width = 92.dp, height = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(
                                if (active) 2.5.dp else 1.dp,
                                if (active) Brand.Coral else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { s.signature = sig }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { SignaturePreview(sig, Modifier.fillMaxSize()) }
                }
                if (signatures.isNotEmpty()) {
                    item { Hint("Touchez la page à l'endroit où signer") }
                }
            }
            Tool.ERASER -> Hint("Glissez sur un trait, un texte ou une signature pour l'effacer.")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
}

@Composable
private fun WidthChoice(width: Float, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((width * 3.2f).dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun SizeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Saisie de texte

@Composable
private fun TextDialogUi(editing: Boolean, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun insert(t: String) {
        val sep = if (value.text.isEmpty() || value.text.endsWith(" ") || value.text.endsWith("\n")) "" else " "
        val text = value.text + sep + t
        value = TextFieldValue(text, TextRange(text.length))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
        title = { Text(if (editing) "Modifier le texte" else "Ajouter du texte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 2,
                    maxLines = 6,
                    label = { Text("Texte") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { insert(DateFormat.getDateInstance(DateFormat.SHORT).format(Date())) },
                        label = { Text("Date du jour") },
                    )
                    AssistChip(onClick = { insert("Lu et approuvé") }, label = { Text("Lu et approuvé") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

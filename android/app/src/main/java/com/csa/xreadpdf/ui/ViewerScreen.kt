package com.csa.xreadpdf.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.DocMeta
import com.csa.xreadpdf.PdfDoc
import com.csa.xreadpdf.tools.OutlineEntry
import com.csa.xreadpdf.tools.PageText
import com.csa.xreadpdf.tools.PdfTools
import com.csa.xreadpdf.tools.SearchHit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/** Zoom global de la visionneuse (pincer à 2 doigts, glisser à 2 doigts pour se déplacer). */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun reset() { scale = 1f; offset = Offset.Zero }

    fun transform(zoom: Float, pan: Offset, size: IntSize) {
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        offset = Offset(
            (offset.x + pan.x).coerceIn(-maxX, maxX),
            (offset.y + pan.y).coerceIn(-maxY, maxY),
        )
    }
}

/** Inverse les couleurs (mode nuit). */
private val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.9f, 0f, 0f, 0f, 235f,
            0f, -0.9f, 0f, 0f, 235f,
            0f, 0f, -0.9f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** Actions de la visionneuse fournies par l'écran principal. */
class ViewerActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onSaveAs: () -> Unit,
    val onEdit: () -> Unit,
    val onSign: () -> Unit,
    val onPrint: () -> Unit,
    val onTools: () -> Unit,
    val onFillForm: () -> Unit,
    val onUnlock: (password: String, onWrong: () -> Unit) -> Unit,
    val onPageChanged: (Int) -> Unit,
    val onToggleBookmark: (Int) -> Unit,
    val loadOutline: suspend () -> List<OutlineEntry>,
    val loadTexts: suspend () -> List<PageText>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    file: File,
    inLibrary: Boolean,
    meta: DocMeta,
    snackbar: SnackbarHostState,
    a: ViewerActions,
) {
    StatusBarIcons(lightIcons = false)
    val zoom = remember(file) { ZoomState() }
    val scope = rememberCoroutineScope()
    val docResult by produceState<Result<PdfDoc>?>(null, file) {
        val result = runCatching { PdfDoc.open(file) }
        value = result
        awaitDispose { result.getOrNull()?.close() }
    }
    val doc = docResult?.getOrNull()
    val pageCount = doc?.pageCount ?: 0

    var night by rememberSaveable { mutableStateOf(false) }
    var pageMode by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var goToDialog by remember { mutableStateOf(false) }
    var pagesSheet by remember { mutableStateOf(false) }
    var outlineSheet by remember { mutableStateOf(false) }
    var textSheet by remember { mutableStateOf(false) }

    // Recherche
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember(file) { mutableStateOf<List<SearchHit>>(emptyList()) }
    var hitIndex by remember(file) { mutableIntStateOf(0) }
    var searchedOnce by remember(file) { mutableStateOf(false) }
    var texts by remember(file) { mutableStateOf<List<PageText>?>(null) }
    var searchBusy by remember { mutableStateOf(false) }

    // Navigation (défilement continu ou page par page)
    val startPage = remember(file) { if (inLibrary) meta.lastPage else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
    val currentPage by remember(pageMode) {
        derivedStateOf { if (pageMode) pagerState.currentPage else listState.firstVisibleItemIndex }
    }
    fun jumpTo(index: Int) {
        if (pageCount == 0) return
        val i = index.coerceIn(0, pageCount - 1)
        scope.launch { if (pageMode) pagerState.scrollToPage(i) else listState.scrollToItem(i) }
    }
    var lastMode by remember { mutableStateOf(pageMode) }
    LaunchedEffect(pageMode, pageCount) {
        // Garde la page courante en changeant de mode
        if (lastMode != pageMode && pageCount > 0) {
            val i = if (pageMode) listState.firstVisibleItemIndex else pagerState.currentPage
            zoom.reset()
            if (pageMode) pagerState.scrollToPage(i) else listState.scrollToItem(i)
        }
        lastMode = pageMode
    }
    LaunchedEffect(file, pageMode) {
        snapshotFlow { currentPage }.collectLatest { a.onPageChanged(it) }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searchBusy = true
            val t = texts ?: a.loadTexts().also { texts = it }
            hits = PdfTools.search(t, q)
            hitIndex = 0
            searchedOnce = true
            searchBusy = false
            hits.firstOrNull()?.let { jumpTo(it.page) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (searching) { searching = false; hits = emptyList(); searchedOnce = false } else a.onBack()
                    }) {
                        Icon(
                            if (searching) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (searching) "Fermer la recherche" else "Retour",
                        )
                    }
                },
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Rechercher dans le PDF") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Column {
                            Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pageCount > 0) {
                                Text(
                                    if (pageCount > 1) "$pageCount pages" else "1 page",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { runSearch() }) { Icon(Icons.Filled.Search, contentDescription = "Rechercher") }
                    } else if (doc != null) {
                        if (zoom.scale > 1f) {
                            IconButton(onClick = zoom::reset) { Icon(Icons.Filled.FitScreen, contentDescription = "Réinitialiser le zoom") }
                        }
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Rechercher") }
                        if (inLibrary) {
                            val marked = currentPage in meta.bookmarks
                            IconButton(onClick = { a.onToggleBookmark(currentPage) }) {
                                Icon(
                                    if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = if (marked) "Retirer le signet" else "Ajouter un signet",
                                    tint = if (marked) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            IconButton(onClick = a.onSave) { Icon(Icons.Filled.SaveAlt, contentDescription = "Enregistrer dans Mes PDF") }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Plus") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                MenuItem("Aller à la page…", Icons.Filled.Pin) { menu = false; goToDialog = true }
                                MenuItem("Toutes les pages", Icons.Filled.GridView) { menu = false; pagesSheet = true }
                                MenuItem("Sommaire et signets", Icons.AutoMirrored.Filled.MenuBook) { menu = false; outlineSheet = true }
                                MenuItem("Texte de la page", Icons.AutoMirrored.Filled.Notes) { menu = false; textSheet = true }
                                HorizontalDivider()
                                CheckItem("Mode nuit", Icons.Filled.DarkMode, night) { night = !night; menu = false }
                                CheckItem("Page par page", Icons.Filled.ViewCarousel, pageMode) { pageMode = !pageMode; menu = false }
                                HorizontalDivider()
                                MenuItem("Remplir le formulaire", Icons.Filled.EditNote) { menu = false; a.onFillForm() }
                                MenuItem("Outils PDF…", Icons.Filled.Build) { menu = false; a.onTools() }
                                MenuItem("Imprimer", Icons.Filled.Print) { menu = false; a.onPrint() }
                                MenuItem("Enregistrer sous…", Icons.Filled.SaveAs) { menu = false; a.onSaveAs() }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(if (night) Color(0xFF121214) else MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            val result = docResult
            when {
                result == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                doc != null -> {
                    val current = hits.getOrNull(hitIndex)
                    val rectsByPage = remember(hits) { hits.groupBy { it.page }.mapValues { e -> e.value.flatMap { it.rects } } }
                    PdfPages(
                        doc = doc,
                        zoom = zoom,
                        night = night,
                        pageMode = pageMode,
                        listState = listState,
                        pagerState = pagerState,
                        highlights = rectsByPage,
                        current = current,
                        currentPage = currentPage,
                    )
                    if (searching) {
                        SearchBar(
                            busy = searchBusy,
                            count = hits.size,
                            index = hitIndex,
                            searched = searchedOnce,
                            snippet = current?.snippet,
                            onPrev = { if (hits.isNotEmpty()) { hitIndex = (hitIndex - 1 + hits.size) % hits.size; jumpTo(hits[hitIndex].page) } },
                            onNext = { if (hits.isNotEmpty()) { hitIndex = (hitIndex + 1) % hits.size; jumpTo(hits[hitIndex].page) } },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    } else {
                        ActionBar(
                            onEdit = a.onEdit,
                            onSign = a.onSign,
                            onShare = a.onShare,
                            onTools = a.onTools,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        )
                    }
                }
                result.exceptionOrNull() is SecurityException -> LockedPdf(
                    onUnlock = a.onUnlock,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Text(
                    "Impossible d'ouvrir ce PDF.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }

    if (goToDialog && pageCount > 0) {
        GoToDialog(pageCount, currentPage, onDismiss = { goToDialog = false }) { jumpTo(it); goToDialog = false }
    }
    if (pagesSheet && doc != null) {
        PagesGridSheet(doc, currentPage, meta.bookmarks, night, onDismiss = { pagesSheet = false }) { jumpTo(it); pagesSheet = false }
    }
    if (outlineSheet) {
        OutlineSheet(
            load = a.loadOutline,
            bookmarks = if (inLibrary) meta.bookmarks.sorted() else emptyList(),
            canBookmark = inLibrary,
            onDismiss = { outlineSheet = false },
            onGo = { jumpTo(it); outlineSheet = false },
        )
    }
    if (textSheet) {
        PageTextSheet(currentPage, load = { texts ?: a.loadTexts().also { texts = it } }, onDismiss = { textSheet = false })
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@Composable
private fun CheckItem(label: String, icon: ImageVector, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------- Pages

@Composable
private fun PdfPages(
    doc: PdfDoc,
    zoom: ZoomState,
    night: Boolean,
    pageMode: Boolean,
    listState: LazyListState,
    pagerState: PagerState,
    highlights: Map<Int, List<RectF>>,
    current: SearchHit?,
    currentPage: Int,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(zoom, pageMode) {
                // Passe "Initial" : on intercepte les gestes à 2 doigts avant le défilement.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2) {
                            zoom.transform(event.calculateZoom(), event.calculatePan(), size)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (zoom.scale > 1f) {
                            val pan = event.calculatePan()
                            if (pageMode || abs(pan.x) > abs(pan.y)) {
                                zoom.transform(1f, if (pageMode) pan else Offset(pan.x, 0f), size)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val renderWidth = (constraints.maxWidth * if (zoom.scale > 1.3f) 2f else 1f).toInt().coerceIn(1, 2400)
        val boxRatio = constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)
        val layer = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
        }

        if (pageMode) {
            HorizontalPager(
                state = pagerState,
                modifier = layer,
                userScrollEnabled = zoom.scale <= 1f,
                beyondViewportPageCount = 1,
            ) { index ->
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    val ratio = doc.pageRatios[index]
                    val wide = ratio >= boxRatio
                    Box(
                        (if (wide) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                            .aspectRatio(ratio, matchHeightConstraintsFirst = !wide),
                    ) {
                        PdfPage(doc, index, ratio, renderWidth, night, highlights[index].orEmpty(), current?.takeIf { it.page == index })
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = layer,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(doc.pageCount) { index ->
                    PdfPage(doc, index, doc.pageRatios[index], renderWidth, night, highlights[index].orEmpty(), current?.takeIf { it.page == index })
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
        ) {
            Text(
                "${currentPage + 1} / ${doc.pageCount}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PdfPage(
    doc: PdfDoc,
    index: Int,
    ratio: Float,
    renderWidth: Int,
    night: Boolean,
    highlights: List<RectF>,
    current: SearchHit?,
) {
    var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, index, renderWidth) {
        doc.render(index, renderWidth)?.let { bitmap = it.asImageBitmap() }
    }
    val pageW = doc.pageSizes[index].width
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .shadow(3.dp, RoundedCornerShape(3.dp))
            .background(if (night) Color(0xFF1E1E22) else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                b, contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = if (night) NightFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (highlights.isNotEmpty()) {
            val currentRects = current?.rects.orEmpty()
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / pageW
                for (r in highlights) {
                    val strong = currentRects.any { it == r }
                    drawRect(
                        if (strong) Color(0xFFFF7A00).copy(alpha = 0.45f) else Color(0xFFFFE14D).copy(alpha = 0.45f),
                        topLeft = Offset(r.left * k, r.top * k),
                        size = Size(r.width() * k, r.height() * k),
                    )
                }
            }
        }
    }
}

/** Barre d'actions flottante aux couleurs de la marque. */
@Composable
private fun ActionBar(onEdit: () -> Unit, onSign: () -> Unit, onShare: () -> Unit, onTools: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Brand.Night,
        contentColor = Brand.Cream,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BarAction("Modifier", Icons.Filled.Edit, onEdit)
            BarAction("Signer", Icons.Filled.Gesture, onSign, accent = true)
            BarAction(null, Icons.Filled.Share, onShare, description = "Partager")
            BarAction(null, Icons.Filled.Build, onTools, description = "Outils PDF")
        }
    }
}

@Composable
private fun BarAction(label: String?, icon: ImageVector, onClick: () -> Unit, accent: Boolean = false, description: String? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (accent) Brand.Coral else Color.Transparent,
        contentColor = if (accent) Brand.Ink else Brand.Cream,
    ) {
        Row(
            Modifier.padding(horizontal = if (label == null) 13.dp else 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------- Recherche

@Composable
private fun SearchBar(
    busy: Boolean,
    count: Int,
    index: Int,
    searched: Boolean,
    snippet: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = Brand.Night, contentColor = Brand.Cream, shadowElevation = 8.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false).padding(vertical = 6.dp)) {
                Text(
                    when {
                        busy -> "Recherche…"
                        !searched -> "Tapez un mot puis Rechercher"
                        count == 0 -> "Aucun résultat (scan ? essayez Outils › OCR)"
                        else -> "Résultat ${index + 1} / $count"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (snippet != null) {
                    Text(snippet, style = MaterialTheme.typography.bodySmall, color = Brand.Cream.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (count > 0) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Précédent", tint = Brand.Cream) }
                IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Suivant", tint = Brand.Cream) }
            }
        }
    }
}

// ---------------------------------------------------------------- PDF protégé

@Composable
private fun LockedPdf(onUnlock: (String, () -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var ask by remember { mutableStateOf(true) }
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("PDF protégé par mot de passe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        FilledTonalButton(onClick = { ask = true }, modifier = Modifier.padding(top = 16.dp)) { Text("Saisir le mot de passe") }
    }
    if (ask) PasswordAskDialog(onDismiss = { ask = false }, onUnlock = onUnlock)
}

@Composable
fun PasswordAskDialog(onDismiss: () -> Unit, onUnlock: (String, () -> Unit) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text("Mot de passe du PDF") },
        text = {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it; wrong = false },
                singleLine = true,
                isError = wrong,
                supportingText = { if (wrong) Text("Mot de passe incorrect") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                label = { Text("Mot de passe") },
            )
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty(), onClick = { onUnlock(pw) { wrong = true } }) { Text("Ouvrir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------- Aller à la page

@Composable
private fun GoToDialog(count: Int, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("${current + 1}") }
    val n = text.trim().toIntOrNull()
    val target = n?.takeIf { it in 1..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Pin, contentDescription = null) },
        title = { Text("Aller à la page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text("Page (1 à $count)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (target != null) onGo(target - 1) }),
            )
        },
        confirmButton = { TextButton(enabled = target != null, onClick = { if (target != null) onGo(target - 1) }) { Text("Aller") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------- Miniatures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagesGridSheet(doc: PdfDoc, current: Int, bookmarks: Set<Int>, night: Boolean, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Toutes les pages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items((0 until doc.pageCount).toList()) { i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(doc.pageRatios[i].coerceIn(0.5f, 1.5f))
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                if (i == current) 2.5.dp else 1.dp,
                                if (i == current) Brand.Coral else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .background(Color.White)
                            .clickable { onGo(i) },
                    ) {
                        Thumb(doc, i, night)
                        if (i in bookmarks) {
                            Icon(
                                Icons.Filled.Bookmark, contentDescription = "Signet", tint = Brand.Coral,
                                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp),
                            )
                        }
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Thumb(doc: PdfDoc, index: Int, night: Boolean) {
    val widthPx = with(LocalDensity.current) { 110.dp.roundToPx() }
    var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, index) { doc.render(index, widthPx)?.let { bitmap = it.asImageBitmap() } }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.Fit, colorFilter = if (night) NightFilter else null, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
    }
}

// ---------------------------------------------------------------- Sommaire et signets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineSheet(
    load: suspend () -> List<OutlineEntry>,
    bookmarks: List<Int>,
    canBookmark: Boolean,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val outline by produceState<List<OutlineEntry>?>(null) { value = load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Sommaire") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Signets (${bookmarks.size})") })
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 520.dp).navigationBarsPadding()) {
            if (tab == 0) {
                val o = outline
                when {
                    o == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    o.isEmpty() -> EmptyNote("Ce PDF n'a pas de sommaire.")
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(o) { e ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = e.page >= 0) { onGo(e.page) }
                                    .padding(start = (20 + e.level * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    e.title,
                                    style = if (e.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (e.page >= 0) Text("${e.page + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                when {
                    !canBookmark -> EmptyNote("Enregistrez ce PDF dans Mes PDF pour y poser des signets.")
                    bookmarks.isEmpty() -> EmptyNote("Aucun signet. Touchez l'icône signet en haut pour marquer la page affichée.")
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(bookmarks) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onGo(p) }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Brand.Coral)
                                Spacer(Modifier.width(14.dp))
                                Text("Page ${p + 1}", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

// ---------------------------------------------------------------- Texte de la page

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTextSheet(page: Int, load: suspend () -> List<PageText>, onDismiss: () -> Unit) {
    val texts by produceState<List<PageText>?>(null) { value = load() }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val text = texts?.getOrNull(page)?.text?.trim()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Texte de la page ${page + 1}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Appui long pour sélectionner une partie",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!text.isNullOrEmpty()) {
                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copié" else "Tout copier")
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 520.dp).navigationBarsPadding()) {
            when {
                texts == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                text.isNullOrEmpty() -> EmptyNote("Aucun texte sur cette page. S'il s'agit d'un scan, utilisez Outils › Rendre le texte cherchable (OCR).")
                else -> SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    )
                }
            }
        }
    }
}

package com.csa.xreadpdf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.csa.xreadpdf.PdfDoc
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    file: File,
    inLibrary: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val zoom = remember(file) { ZoomState() }
    val docResult by produceState<Result<PdfDoc>?>(null, file) {
        val result = runCatching { PdfDoc.open(file) }
        value = result
        awaitDispose { result.getOrNull()?.close() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = { Text(file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (zoom.scale > 1f) {
                        IconButton(onClick = zoom::reset) {
                            Icon(Icons.Filled.FitScreen, contentDescription = "Réinitialiser le zoom")
                        }
                    }
                    if (!inLibrary) {
                        IconButton(onClick = onSave) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = "Enregistrer dans Mes PDF")
                        }
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Partager")
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val result = docResult
            val doc = result?.getOrNull()
            when {
                result == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                doc != null -> PdfPages(doc, zoom)
                else -> Text(
                    if (result.exceptionOrNull() is SecurityException)
                        "Ce PDF est protégé par mot de passe : non pris en charge."
                    else "Impossible d'ouvrir ce PDF.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfPages(doc: PdfDoc, zoom: ZoomState) {
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(zoom) {
                // Passe "Initial" : on intercepte les gestes à 2 doigts avant le défilement de la liste.
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
                            if (abs(pan.x) > abs(pan.y)) {
                                zoom.transform(1f, Offset(pan.x, 0f), size)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        // Rendu plus fin quand on zoome (2x au-delà de 130 %)
        val renderWidth = (constraints.maxWidth * if (zoom.scale > 1.3f) 2f else 1f)
            .toInt().coerceIn(1, 2400)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(doc.pageCount) { index ->
                PdfPage(doc, index, doc.pageRatios[index], renderWidth)
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
            Text(
                "$currentPage / ${doc.pageCount}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, ratio: Float, renderWidth: Int) {
    var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(doc, index, renderWidth) {
        doc.render(index, renderWidth)?.let { bitmap = it.asImageBitmap() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .shadow(2.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

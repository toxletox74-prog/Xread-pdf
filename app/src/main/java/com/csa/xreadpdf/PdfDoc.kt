package com.csa.xreadpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enveloppe autour de PdfRenderer (non thread-safe) : tous les accès passent
 * par un dispatcher à parallélisme 1, ce qui sérialise rendu et fermeture.
 */
/** Taille d'une page en points PDF, telle qu'affichée (CropBox + /Rotate). */
data class PageSize(val width: Float, val height: Float)

class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<PageSize>,
) {
    @Volatile private var closed = false

    /** Ratio largeur / hauteur de chaque page. */
    val pageRatios: List<Float> = pageSizes.map { (it.width / it.height).coerceIn(0.1f, 10f) }

    val pageCount: Int get() = pageSizes.size

    /** Rendu de la page [index] ; [widthPx] = largeur avant [rotation] (horaire, multiple de 90°). */
    suspend fun render(index: Int, widthPx: Int, rotation: Int = 0): Bitmap? = withContext(renderDispatcher) {
        if (closed || index !in pageSizes.indices) return@withContext null
        val bmp = renderPage(renderer, index, widthPx)
        if (rotation % 360 == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    fun close() {
        CoroutineScope(renderDispatcher).launch {
            if (!closed) {
                closed = true
                renderer.close()
                pfd.close()
            }
        }
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)

        suspend fun open(file: File): PdfDoc = withContext(renderDispatcher) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                val sizes = List(renderer.pageCount) { i ->
                    val page = renderer.openPage(i)
                    try {
                        PageSize(page.width.toFloat().coerceAtLeast(1f), page.height.toFloat().coerceAtLeast(1f))
                    } finally {
                        page.close()
                    }
                }
                PdfDoc(pfd, renderer, sizes)
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }

        /** Miniature de la première page, ou null si illisible. */
        suspend fun thumbnail(file: File, widthPx: Int): Bitmap? = withContext(renderDispatcher) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    try {
                        if (renderer.pageCount == 0) null else renderPage(renderer, 0, widthPx)
                    } finally {
                        renderer.close()
                    }
                }
            }.getOrNull()
        }

        private fun renderPage(renderer: PdfRenderer, index: Int, widthPx: Int): Bitmap {
            val page = renderer.openPage(index)
            try {
                val w = widthPx.coerceAtLeast(1)
                val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally {
                page.close()
            }
        }
    }
}

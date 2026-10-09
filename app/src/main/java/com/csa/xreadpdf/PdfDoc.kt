package com.csa.xreadpdf

import android.graphics.Bitmap
import android.graphics.Color
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
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Ratio largeur / hauteur de chaque page. */
    val pageRatios: List<Float>,
) {
    @Volatile private var closed = false

    val pageCount: Int get() = pageRatios.size

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(renderDispatcher) {
        if (closed) null else renderPage(renderer, index, widthPx)
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
                val ratios = List(renderer.pageCount) { i ->
                    val page = renderer.openPage(i)
                    try {
                        (page.width.toFloat() / page.height).coerceIn(0.1f, 10f)
                    } finally {
                        page.close()
                    }
                }
                PdfDoc(pfd, renderer, ratios)
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

// Rendu des pages avec pdf.js.
import * as pdfjs from '../vendor/pdf.min.js';

pdfjs.GlobalWorkerOptions.workerSrc = new URL('../vendor/pdf.worker.min.js', import.meta.url).href;

/** Ouvre un PDF (copie des octets : pdf.js transfère le tampon au worker). */
export function openPdf(bytes) {
  return pdfjs.getDocument({ data: bytes.slice(0), isEvalSupported: false }).promise;
}

/** Tailles intrinsèques des pages en points (CropBox tournée de /Rotate). */
export async function pageSizes(doc) {
  const out = [];
  for (let i = 1; i <= doc.numPages; i++) {
    const p = await doc.getPage(i);
    const v = p.getViewport({ scale: 1 });
    out.push({ width: v.width, height: v.height });
  }
  return out;
}

/** Dessine la page [index] (0-based) dans [canvas] à la largeur CSS [cssWidth]. */
export async function renderPage(doc, index, canvas, cssWidth, rotation = 0, maxDpr = 2) {
  const page = await doc.getPage(index + 1);
  const base = page.getViewport({ scale: 1, rotation: page.rotate + rotation });
  const dpr = Math.min(window.devicePixelRatio || 1, maxDpr);
  // Plafond de pixels (Safari iOS limite la mémoire des canvas)
  let scale = (cssWidth / base.width) * dpr;
  const maxPixels = 5_000_000;
  if (base.width * base.height * scale * scale > maxPixels) scale = Math.sqrt(maxPixels / (base.width * base.height));
  const vp = page.getViewport({ scale, rotation: page.rotate + rotation });
  canvas.width = Math.floor(vp.width);
  canvas.height = Math.floor(vp.height);
  const task = page.render({ canvasContext: canvas.getContext('2d'), viewport: vp, background: 'white' });
  canvas._task?.cancel?.();
  canvas._task = task;
  try { await task.promise; } catch (e) { if (e?.name !== 'RenderingCancelledException') throw e; }
}

/** Miniature JPEG de la première page. */
export async function thumbnail(bytes, width = 360) {
  const doc = await openPdf(bytes);
  try {
    const c = document.createElement('canvas');
    await renderPage(doc, 0, c, width, 0, 1);
    return await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.8));
  } finally {
    doc.destroy();
  }
}

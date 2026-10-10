// Export PDF avec pdf-lib : le contenu d'origine est conservé tel quel (vectoriel, texte
// sélectionnable) ; les annotations sont ajoutées au flux de chaque page, puis rotations,
// suppressions et ordre des pages sont appliqués. Même méthode que la version Android.
import {
  PDFDocument, StandardFonts, PDFName, PDFNumber, degrees,
  pushGraphicsState, popGraphicsState, concatTransformationMatrix,
  moveTo, lineTo, appendBezierCurve, stroke, setLineWidth, setLineCap, setLineJoin,
  setStrokingRgbColor, setFillingRgbColor, setGraphicsState,
  beginText, endText, setFontAndSize, moveText, showText,
  LineCapStyle, LineJoinStyle,
} from '../vendor/pdf-lib.esm.min.js';
import { normDeg, smoothPath, ASCENT, LINE, mul } from './model.js';

const rgb = (c) => [((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255];

/**
 * @param {Uint8Array} bytes  PDF source
 * @param {Array} pages       [{src, width, height, rotation, annots}] — tailles intrinsèques (pdf.js)
 * @returns {Promise<Uint8Array>}
 */
export async function exportPdf(bytes, pages) {
  const doc = await PDFDocument.load(bytes, { ignoreEncryption: true, updateMetadata: false });
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const originals = doc.getPages();

  for (const ep of pages) {
    const page = originals[ep.src];
    if (!page) continue;
    const r0 = normDeg(page.getRotation().angle);
    if (ep.annots.length) drawAnnots(doc, page, r0, ep, font);
    if (ep.rotation) page.setRotation(degrees(normDeg(r0 + ep.rotation)));
  }

  const order = pages.map((p) => p.src);
  const identity = order.length === originals.length && order.every((s, i) => s === i);
  if (!identity) {
    // Retire toutes les pages puis les réinsère dans le nouvel ordre (sans les pages supprimées)
    for (let i = originals.length - 1; i >= 0; i--) doc.removePage(i);
    order.forEach((s, i) => doc.insertPage(i, originals[s]));
  }
  doc.setProducer('Xread PDF');
  return doc.save();
}

function drawAnnots(doc, page, r0, ep, font) {
  const crop = page.getCropBox();
  const { x: x0, y: y0, width: w, height: h } = crop;
  const dw = r0 % 180 === 0 ? w : h;
  const dh = r0 % 180 === 0 ? h : w;

  // Repère d'affichage (Y vers le HAUT, en points) → espace utilisateur PDF
  const m = {
    0: [1, 0, 0, 1, x0, y0],
    90: [0, 1, -1, 0, x0 + w, y0],
    180: [-1, 0, 0, -1, x0 + w, y0 + h],
    270: [0, -1, 1, 0, x0, y0 + h],
  }[r0];

  const fontKey = page.node.newFontDictionary(font.name, font.ref);
  const gs = doc.context.obj({ Type: 'ExtGState', CA: 0.4, ca: 0.4, BM: 'Multiply' });
  const gsKey = page.node.newExtGState('GSxr', doc.context.register(gs));

  const ops = [pushGraphicsState(), concatTransformationMatrix(...m)];
  // Les tailles mémorisées viennent de pdf.js : recalage sur la CropBox
  ops.push(concatTransformationMatrix(dw / ep.width, 0, 0, dh / ep.height, 0, 0));
  const top = ep.height;

  for (const a of ep.annots) {
    ops.push(pushGraphicsState());
    if (a.type === 'ink') {
      if (a.highlighter) ops.push(setGraphicsState(gsKey));
      strokeOps(ops, [a.points.map((p) => ({ x: p.x, y: top - p.y }))], a.color, a.width);
    } else if (a.type === 'text') {
      rotateAt(ops, a.rotation, a.x, top - a.y);
      ops.push(setFillingRgbColor(...rgb(a.color)), beginText(), setFontAndSize(fontKey, a.size),
        moveText(0, -ASCENT * a.size));
      a.text.split('\n').forEach((line, i) => {
        if (i > 0) ops.push(moveText(0, -LINE * a.size));
        ops.push(showText(font.encodeText(winAnsi(line, font))));
      });
      ops.push(endText());
    } else if (a.type === 'sign') {
      rotateAt(ops, a.rotation, a.x, top - a.y);
      const k = a.width;
      strokeOps(ops, a.sig.strokes.map((s) => s.map((p) => ({ x: p.x * k, y: -p.y * k }))), a.sig.color, a.sig.width * k);
    }
    ops.push(popGraphicsState());
  }
  ops.push(popGraphicsState());
  page.pushOperators(...ops);
}

/** Rotation (sens trigonométrique, repère Y vers le haut) autour de (x, y). */
function rotateAt(ops, deg, x, y) {
  const t = (deg * Math.PI) / 180;
  const c = Math.round(Math.cos(t) * 1e6) / 1e6, s = Math.round(Math.sin(t) * 1e6) / 1e6;
  ops.push(concatTransformationMatrix(c, s, -s, c, x, y));
}

function strokeOps(ops, strokes, color, width) {
  ops.push(setStrokingRgbColor(...rgb(color)), setLineWidth(width),
    setLineCap(LineCapStyle.Round), setLineJoin(LineJoinStyle.Round));
  let cur = { x: 0, y: 0 };
  const path = {
    moveTo: (x, y) => { ops.push(moveTo(x, y)); cur = { x, y }; },
    lineTo: (x, y) => { ops.push(lineTo(x, y)); cur = { x, y }; },
    quadTo: (cx, cy, x, y) => {
      // Quadratique → cubique
      const c1 = { x: cur.x + (2 / 3) * (cx - cur.x), y: cur.y + (2 / 3) * (cy - cur.y) };
      const c2 = { x: x + (2 / 3) * (cx - x), y: y + (2 / 3) * (cy - y) };
      ops.push(appendBezierCurve(c1.x, c1.y, c2.x, c2.y, x, y));
      cur = { x, y };
    },
  };
  for (const s of strokes) if (s.length) smoothPath(s, path);
  ops.push(stroke());
}

/** Helvetica standard (WinAnsi) : couvre le français ; les autres caractères deviennent « ? ». */
function winAnsi(text, font) {
  let out = '';
  for (const ch of text.replace(/\t/g, ' ')) {
    try { font.encodeText(ch); out += ch; } catch { out += '?'; }
  }
  return out;
}

/** Largeur d'un texte en Helvetica (pour les mesures côté export si besoin). */
export const helveticaWidth = (font) => (text, size) => font.widthOfTextAtSize(text, size);

export { mul };

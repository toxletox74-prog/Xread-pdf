// Modèle d'édition, identique aux versions Android et iOS.
// Coordonnées en POINTS PDF dans l'espace "intrinsèque" de la page (CropBox + /Rotate
// d'origine), origine en haut à gauche, y vers le bas. La rotation ajoutée par l'utilisateur
// (page.rotation) est appliquée par-dessus, à l'affichage comme à l'export.

export const PEN_COLORS = [0x1a1a1f, 0x1f4fd8, 0xd93025, 0x188038];
export const HIGHLIGHT_COLORS = [0xffe14d, 0x8cf08c, 0xff9bd2, 0x8fd3ff];
export const SIGN_COLORS = [0x1a1a1f, 0x1f3fbf];
export const PEN_WIDTHS = [1.2, 2.4, 4.5];
export const HIGHLIGHT_WIDTH = 12;
export const TEXT_SIZES = [10, 14, 20];

/** Police du texte ajouté : Helvetica (PDF standard) et ses métriques de ligne. */
export const TEXT_FONT = 'Helvetica, Arial, sans-serif';
export const ASCENT = 0.77;
export const LINE = 1.15;

export const normDeg = (d) => ((d % 360) + 360) % 360;
export const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
export const add = (a, b) => ({ x: a.x + b.x, y: a.y + b.y });
export const sub = (a, b) => ({ x: a.x - b.x, y: a.y - b.y });
export const mul = (a, k) => ({ x: a.x * k, y: a.y * k });

/** Rotation horaire (repère y vers le bas) d'un multiple de 90°. */
export function rot(p, deg) {
  switch (normDeg(deg)) {
    case 90: return { x: -p.y, y: p.x };
    case 180: return { x: -p.x, y: -p.y };
    case 270: return { x: p.y, y: -p.x };
    default: return p;
  }
}

export const hex = (c) => '#' + (c & 0xffffff).toString(16).padStart(6, '0');

// ---- Texte : mesure partagée (canvas hors écran) ----
let measureCtx = null;
function ctx2d() {
  if (!measureCtx) {
    const c = typeof OffscreenCanvas !== 'undefined' ? new OffscreenCanvas(8, 8) : document.createElement('canvas');
    measureCtx = c.getContext('2d');
  }
  return measureCtx;
}

/** Taille d'un texte en points. [widthOf] permet d'injecter une mesure (export). */
export function textSize(text, size, widthOf) {
  const lines = text.split('\n');
  let w = 0;
  if (widthOf) {
    for (const l of lines) w = Math.max(w, widthOf(l, size));
  } else {
    const c = ctx2d();
    c.font = `${size * 4}px ${TEXT_FONT}`;
    for (const l of lines) w = Math.max(w, c.measureText(l).width / 4);
  }
  return { w: Math.max(w, size * 0.3), h: lines.length * LINE * size };
}

// ---- Éléments posés (texte, signature) : ancre haut-gauche + rotation ----
export const isPlaced = (a) => a.type === 'text' || a.type === 'sign';

export function boxSize(a, widthOf) {
  if (a.type === 'text') return textSize(a.text, a.size, widthOf);
  if (a.type === 'sign') return { w: a.width, h: a.width / a.sig.aspect };
  return { w: 0, h: 0 };
}

/** Point local de l'élément → page. */
export const corner = (a, local) => add({ x: a.x, y: a.y }, rot(local, -a.rotation));
/** Point de la page → repère local de l'élément. */
export const toLocal = (a, p) => rot(sub(p, { x: a.x, y: a.y }), a.rotation);

function distToSegment(p, a, b) {
  const dx = b.x - a.x, dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  if (len2 === 0) return dist(p, a);
  const t = Math.min(Math.max(((p.x - a.x) * dx + (p.y - a.y) * dy) / len2, 0), 1);
  return dist(p, { x: a.x + t * dx, y: a.y + t * dy });
}

export function hit(a, p, tol) {
  if (a.type === 'ink') {
    const r = a.width / 2 + tol;
    if (a.points.length === 1) return dist(p, a.points[0]) <= r;
    for (let i = 0; i < a.points.length - 1; i++) if (distToSegment(p, a.points[i], a.points[i + 1]) <= r) return true;
    return false;
  }
  const l = toLocal(a, p);
  const s = boxSize(a);
  return l.x >= -tol && l.x <= s.w + tol && l.y >= -tol && l.y <= s.h + tol;
}

/**
 * Tracé lissé (quadratiques passant par les milieux), partagé aperçu / export.
 * Appelle path.moveTo / lineTo / quadTo(cx, cy, x, y).
 */
export function smoothPath(points, path) {
  if (!points.length) return;
  path.moveTo(points[0].x, points[0].y);
  if (points.length === 1) { path.lineTo(points[0].x, points[0].y); return; }
  if (points.length === 2) { path.lineTo(points[1].x, points[1].y); return; }
  for (let i = 1; i < points.length - 1; i++) {
    const a = points[i], b = points[i + 1];
    path.quadTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2);
  }
  const last = points[points.length - 1];
  path.lineTo(last.x, last.y);
}

/** Redimensionne depuis la poignée (coin bas-droit), proportions conservées. */
export function resized(a, box, p) {
  const l = toLocal(a, p);
  const f = Math.max(Math.max(l.x / Math.max(box.w, 1), l.y / Math.max(box.h, 1)), 0.05);
  if (a.type === 'sign') return { ...a, width: Math.min(Math.max(a.width * f, 16), 2000) };
  if (a.type === 'text') return { ...a, size: Math.min(Math.max(a.size * f, 4), 160) };
  return a;
}

// ---- Dessin canvas 2D (aperçu), repère y vers le bas en points ----
export function drawAnnots(ctx, annots) {
  for (const a of annots) drawAnnot(ctx, a);
}

function strokePaths(ctx, strokes, color, width) {
  ctx.strokeStyle = hex(color);
  ctx.lineWidth = width;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.beginPath();
  const p = { moveTo: (x, y) => ctx.moveTo(x, y), lineTo: (x, y) => ctx.lineTo(x, y), quadTo: (cx, cy, x, y) => ctx.quadraticCurveTo(cx, cy, x, y) };
  for (const s of strokes) smoothPath(s, p);
  ctx.stroke();
}

export function drawSignature(ctx, sig, w) {
  strokePaths(ctx, sig.strokes.map((s) => s.map((q) => mul(q, w))), sig.color, sig.width * w);
}

export function drawAnnot(ctx, a) {
  ctx.save();
  if (a.type === 'ink') {
    if (a.highlighter) { ctx.globalAlpha = 0.4; ctx.globalCompositeOperation = 'multiply'; }
    strokePaths(ctx, [a.points], a.color, a.width);
  } else if (a.type === 'text') {
    ctx.translate(a.x, a.y);
    ctx.rotate((-a.rotation * Math.PI) / 180);
    ctx.fillStyle = hex(a.color);
    // Le texte est dessiné à 4× puis réduit (meilleure précision aux petites tailles)
    ctx.scale(0.25, 0.25);
    ctx.font = `${a.size * 4}px ${TEXT_FONT}`;
    ctx.textBaseline = 'alphabetic';
    a.text.split('\n').forEach((l, i) => ctx.fillText(l, 0, (ASCENT + i * LINE) * a.size * 4));
  } else if (a.type === 'sign') {
    ctx.translate(a.x, a.y);
    ctx.rotate((-a.rotation * Math.PI) / 180);
    drawSignature(ctx, a.sig, a.width);
  }
  ctx.restore();
}

// Scanner : photos → recadrage 4 coins (correction de perspective) → filtre → PDF.
import { useEffect, useRef, useState } from '../vendor/hooks.module.js';
import { html, Icon, Confirm, toast, pickFiles } from './ui.js';
import * as A from './actions.js';
import { PDFDocument } from '../vendor/pdf-lib.esm.min.js';

const MAX_SRC = 2200;   // côté max de l'image source conservée
const MAX_OUT = 1754;   // côté max de la page produite (~150 dpi en A4)

async function loadImage(file) {
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.decoding = 'async';
    img.src = url;
    await img.decode();
    const k = Math.min(1, MAX_SRC / Math.max(img.naturalWidth, img.naturalHeight));
    const c = document.createElement('canvas');
    c.width = Math.round(img.naturalWidth * k);
    c.height = Math.round(img.naturalHeight * k);
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
    return c;
  } finally {
    URL.revokeObjectURL(url);
  }
}

const fullQuad = () => [{ x: 0.03, y: 0.03 }, { x: 0.97, y: 0.03 }, { x: 0.97, y: 0.97 }, { x: 0.03, y: 0.97 }];

function rotateCanvas(src) {
  const c = document.createElement('canvas');
  c.width = src.height; c.height = src.width;
  const ctx = c.getContext('2d');
  ctx.translate(c.width, 0);
  ctx.rotate(Math.PI / 2);
  ctx.drawImage(src, 0, 0);
  return c;
}

/** Homographie qui envoie les coins du rectangle (0,0)-(W,H) sur le quadrilatère [q]. */
function homography(W, H, q) {
  const src = [[0, 0], [W, 0], [W, H], [0, H]];
  const A = [], b = [];
  for (let i = 0; i < 4; i++) {
    const [u, v] = src[i], { x, y } = q[i];
    A.push([u, v, 1, 0, 0, 0, -u * x, -v * x]); b.push(x);
    A.push([0, 0, 0, u, v, 1, -u * y, -v * y]); b.push(y);
  }
  // Élimination de Gauss avec pivot partiel
  for (let c = 0; c < 8; c++) {
    let p = c;
    for (let r = c + 1; r < 8; r++) if (Math.abs(A[r][c]) > Math.abs(A[p][c])) p = r;
    [A[c], A[p]] = [A[p], A[c]]; [b[c], b[p]] = [b[p], b[c]];
    for (let r = c + 1; r < 8; r++) {
      const f = A[r][c] / A[c][c];
      for (let k = c; k < 8; k++) A[r][k] -= f * A[c][k];
      b[r] -= f * b[c];
    }
  }
  const h = new Array(8);
  for (let r = 7; r >= 0; r--) {
    let s = b[r];
    for (let k = r + 1; k < 8; k++) s -= A[r][k] * h[k];
    h[r] = s / A[r][r];
  }
  return h;
}

/** Redresse la zone [quad] (coordonnées normalisées) de [src] et applique le filtre. */
function warp(src, quad, filter) {
  const q = quad.map((p) => ({ x: p.x * src.width, y: p.y * src.height }));
  const d = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
  let W = Math.max(d(q[0], q[1]), d(q[3], q[2]));
  let H = Math.max(d(q[0], q[3]), d(q[1], q[2]));
  const k = Math.min(1, MAX_OUT / Math.max(W, H));
  W = Math.max(1, Math.round(W * k)); H = Math.max(1, Math.round(H * k));
  const h = homography(W, H, q);
  const sd = src.getContext('2d').getImageData(0, 0, src.width, src.height).data;
  const out = new ImageData(W, H);
  const o = out.data, sw = src.width, sh = src.height;
  for (let v = 0; v < H; v++) {
    for (let u = 0; u < W; u++) {
      const z = h[6] * u + h[7] * v + 1;
      const x = (h[0] * u + h[1] * v + h[2]) / z, y = (h[3] * u + h[4] * v + h[5]) / z;
      const x0 = Math.min(Math.max(Math.floor(x), 0), sw - 2), y0 = Math.min(Math.max(Math.floor(y), 0), sh - 2);
      const fx = Math.min(Math.max(x - x0, 0), 1), fy = Math.min(Math.max(y - y0, 0), 1);
      const i00 = (y0 * sw + x0) * 4, i10 = i00 + 4, i01 = i00 + sw * 4, i11 = i01 + 4;
      const j = (v * W + u) * 4;
      for (let c = 0; c < 3; c++) {
        const top = sd[i00 + c] + (sd[i10 + c] - sd[i00 + c]) * fx;
        const bot = sd[i01 + c] + (sd[i11 + c] - sd[i01 + c]) * fx;
        o[j + c] = top + (bot - top) * fy;
      }
      o[j + 3] = 255;
    }
  }
  if (filter === 'doc') documentFilter(o);
  const c = document.createElement('canvas');
  c.width = W; c.height = H;
  c.getContext('2d').putImageData(out, 0, 0);
  return c;
}

/** Filtre « document » : niveaux de gris, papier blanc, encre foncée. */
function documentFilter(o) {
  const hist = new Uint32Array(256);
  const n = o.length / 4;
  for (let i = 0; i < o.length; i += 4) {
    const g = (o[i] * 299 + o[i + 1] * 587 + o[i + 2] * 114) / 1000 | 0;
    o[i] = g; hist[g]++;
  }
  const pct = (p) => { let acc = 0; for (let v = 0; v < 256; v++) { acc += hist[v]; if (acc >= n * p) return v; } return 255; };
  const lo = pct(0.02), hi = Math.max(pct(0.75), lo + 20);
  for (let i = 0; i < o.length; i += 4) {
    let t = (o[i] - lo) / (hi - lo);
    t = Math.min(Math.max(t, 0), 1);
    const g = Math.round(255 * Math.pow(t, 1.6));
    o[i] = o[i + 1] = o[i + 2] = g;
  }
}

export function Scanner() {
  const [pages, setPages] = useState([]);
  const [editing, setEditing] = useState(null);   // index
  const [busy, setBusy] = useState(false);
  const [confirmExit, setConfirmExit] = useState(false);
  const started = useRef(false);

  async function add(capture) {
    const files = await pickFiles({ accept: 'image/*', multiple: !capture, capture: capture ? 'environment' : null });
    if (!files.length) return;
    setBusy(true);
    const added = [];
    for (const f of files) {
      try { added.push({ src: await loadImage(f), quad: fullQuad(), filter: 'doc' }); } catch { toast('Image illisible'); }
    }
    setBusy(false);
    if (!added.length) return;
    setPages((p) => [...p, ...added]);
    if (added.length === 1) setEditing(pages.length);
  }

  useEffect(() => {
    if (started.current) return;
    started.current = true;
  }, []);

  async function create() {
    setBusy(true);
    await new Promise((r) => setTimeout(r, 30));
    try {
      const pdf = await PDFDocument.create();
      for (const p of pages) {
        const c = warp(p.src, p.quad, p.filter);
        const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.75));
        const img = await pdf.embedJpg(new Uint8Array(await blob.arrayBuffer()));
        const w = 595, h = (595 * c.height) / c.width;
        pdf.addPage([w, h]).drawImage(img, { x: 0, y: 0, width: w, height: h });
      }
      pdf.setProducer('Xread PDF');
      const bytes = await pdf.save();
      const d = new Date(), z = (n) => String(n).padStart(2, '0');
      const name = `Scan ${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())} ${z(d.getHours())}-${z(d.getMinutes())}-${z(d.getSeconds())}`;
      await A.saveNewPdf(name, bytes);
      toast('Scan enregistré');
    } catch (e) {
      console.error(e);
      toast('Échec de la création du PDF');
      setBusy(false);
    }
  }

  if (editing != null && pages[editing]) {
    return html`<${CropView} page=${pages[editing]}
      onDone=${(np) => { setPages((ps) => ps.map((p, i) => (i === editing ? np : p))); setEditing(null); }}
      onDelete=${() => { setPages((ps) => ps.filter((_, i) => i !== editing)); setEditing(null); }} />`;
  }

  return html`<div class="screen scanner">
    <header class="bar">
      <button class="icon-btn" aria-label="Fermer" onClick=${() => (pages.length ? setConfirmExit(true) : A.goLibrary())}><${Icon} name="x" /></button>
      <div class="bar-title"><div class="title">Scanner</div><div class="sub">${pages.length ? `${pages.length} page${pages.length > 1 ? 's' : ''}` : 'Photographiez vos documents'}</div></div>
      <button class="btn primary small" disabled=${!pages.length || busy} onClick=${create}>Créer le PDF</button>
    </header>
    <div class="scan-body">
      ${pages.length === 0 && html`<div class="empty">
        <span class="round-icon coral"><${Icon} name="camera" size=${40} /></span>
        <h2>Photographiez une page</h2>
        <p>Cadrez le document à plat, bien éclairé. Vous pourrez ajuster les 4 coins pour le redresser.</p>
      </div>`}
      <div class="scan-grid">
        ${pages.map((p, i) => html`<button class="scan-thumb" onClick=${() => setEditing(i)} aria-label=${`Page ${i + 1}, ajuster`}>
          <${Preview} page=${p} /><span class="num">${i + 1}</span>
        </button>`)}
      </div>
    </div>
    <div class="scan-actions">
      <button class="tile coral" onClick=${() => add(true)}><span class="tile-icon"><${Icon} name="camera" /></span><span class="tile-label">Photo</span></button>
      <button class="tile sky" onClick=${() => add(false)}><span class="tile-icon"><${Icon} name="image" /></span><span class="tile-label">Galerie</span></button>
    </div>
    ${busy && html`<div class="sheet-backdrop center"><div class="dialog row"><div class="spinner small"></div>Traitement…</div></div>`}
    ${confirmExit && html`<${Confirm} title="Abandonner le scan ?" text="Les pages photographiées seront perdues."
      onClose=${() => setConfirmExit(false)}
      actions=${[{ label: 'Continuer', onClick: () => setConfirmExit(false) }, { label: 'Abandonner', kind: 'danger', onClick: A.goLibrary }]} />`}
  </div>`;
}

/** Aperçu redressé (petite taille). */
function Preview({ page }) {
  const box = useRef(null);
  useEffect(() => {
    const small = document.createElement('canvas');
    const k = 500 / Math.max(page.src.width, page.src.height);
    small.width = Math.round(page.src.width * k); small.height = Math.round(page.src.height * k);
    small.getContext('2d').drawImage(page.src, 0, 0, small.width, small.height);
    const c = warp(small, page.quad, page.filter);
    c.className = 'fill-contain';
    box.current.replaceChildren(c);
  }, [page]);
  return html`<div class="preview" ref=${box}></div>`;
}

/** Ajustement des 4 coins + filtre + rotation. */
function CropView({ page, onDone, onDelete }) {
  const [src, setSrc] = useState(page.src);
  const [quad, setQuad] = useState(page.quad);
  const [filter, setFilter] = useState(page.filter);
  const wrap = useRef(null);
  const canvasHost = useRef(null);
  const [size, setSize] = useState({ w: 0, h: 0 });

  useEffect(() => {
    const el = wrap.current;
    const fit = () => {
      const W = el.clientWidth - 32, H = el.clientHeight - 32;
      const k = Math.min(W / src.width, H / src.height);
      setSize({ w: src.width * k, h: src.height * k });
    };
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, [src]);

  useEffect(() => {
    src.className = 'fill';
    canvasHost.current.replaceChildren(src);
  }, [src]);

  function drag(i) {
    return (e) => {
      e.preventDefault();
      const r = e.currentTarget.parentElement.getBoundingClientRect();
      const move = (ev) => {
        const x = Math.min(Math.max((ev.clientX - r.left) / r.width, 0), 1);
        const y = Math.min(Math.max((ev.clientY - r.top) / r.height, 0), 1);
        setQuad((q) => q.map((p, j) => (j === i ? { x, y } : p)));
      };
      const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); };
      window.addEventListener('pointermove', move);
      window.addEventListener('pointerup', up);
    };
  }

  const pts = quad.map((p) => `${p.x * 100},${p.y * 100}`).join(' ');
  return html`<div class="screen crop">
    <header class="bar">
      <button class="icon-btn" aria-label="Supprimer la page" onClick=${onDelete}><${Icon} name="trash" /></button>
      <div class="bar-title"><div class="title">Ajuster la page</div><div class="sub">Déplacez les 4 coins sur les bords du document</div></div>
      <button class="btn primary small" onClick=${() => onDone({ src, quad, filter })}>OK</button>
    </header>
    <div class="crop-area" ref=${wrap}>
      <div class="crop-box" style=${{ width: `${size.w}px`, height: `${size.h}px` }}>
        <div class="crop-img" ref=${canvasHost}></div>
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" class="crop-svg"><polygon points=${pts} /></svg>
        ${quad.map((p, i) => html`<span class="handle" style=${{ left: `${p.x * 100}%`, top: `${p.y * 100}%` }}
          role="slider" aria-label=${`Coin ${i + 1}`} onPointerDown=${drag(i)}></span>`)}
      </div>
    </div>
    <div class="crop-actions">
      <div class="segmented">
        <button class=${filter === 'doc' ? 'on' : ''} onClick=${() => setFilter('doc')}>Document</button>
        <button class=${filter === 'color' ? 'on' : ''} onClick=${() => setFilter('color')}>Couleur</button>
      </div>
      <button class="chip" onClick=${() => setQuad(fullQuad())}>Tout</button>
      <button class="chip" aria-label="Pivoter" onClick=${() => {
        setSrc(rotateCanvas(src));
        setQuad((q) => [3, 0, 1, 2].map((j) => ({ x: 1 - q[j].y, y: q[j].x })));
      }}><${Icon} name="rotate" size=${18} /></button>
    </div>
  </div>`;
}

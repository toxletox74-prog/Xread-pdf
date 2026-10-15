// Signatures : aperçu, pad de création, écran « Mes signatures ».
import { useEffect, useRef, useState } from '../vendor/hooks.module.js';
import { html, Icon, Confirm, app } from './ui.js';
import * as A from './actions.js';
import { newId } from './store.js';
import { SIGN_COLORS, hex, drawSignature, smoothPath } from './model.js';

export function SignaturePreview({ sig }) {
  const c = useRef(null);
  useEffect(() => {
    const el = c.current;
    const dpr = window.devicePixelRatio || 1;
    const W = el.clientWidth, H = el.clientHeight;
    el.width = W * dpr; el.height = H * dpr;
    const ctx = el.getContext('2d');
    const w = Math.min(W, H * sig.aspect), h = w / sig.aspect;
    ctx.setTransform(dpr, 0, 0, dpr, ((W - w) / 2) * dpr, ((H - h) / 2) * dpr);
    drawSignature(ctx, sig, w);
  }, [sig]);
  return html`<canvas class="sig-preview" ref=${c} aria-hidden="true"></canvas>`;
}

export function SignaturePad({ onCancel, onSave }) {
  const canvas = useRef(null);
  const strokes = useRef([]);
  const [count, setCount] = useState(0);
  const [color, setColor] = useState(SIGN_COLORS[0]);
  const STROKE = 3;

  function redraw() {
    const el = canvas.current;
    const dpr = window.devicePixelRatio || 1;
    const W = el.clientWidth, H = el.clientHeight;
    if (el.width !== W * dpr) { el.width = W * dpr; el.height = H * dpr; }
    const ctx = el.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);
    const base = H * 0.72;
    ctx.strokeStyle = '#b8afa8';
    ctx.lineWidth = 1;
    ctx.setLineDash([6, 5]);
    ctx.beginPath(); ctx.moveTo(W * 0.08, base); ctx.lineTo(W * 0.92, base); ctx.stroke();
    ctx.setLineDash([]);
    ctx.lineWidth = 1.5;
    const x0 = W * 0.08, y0 = base - 18;
    ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x0 + 8, y0 + 8); ctx.moveTo(x0 + 8, y0); ctx.lineTo(x0, y0 + 8); ctx.stroke();
    ctx.strokeStyle = hex(color);
    ctx.lineWidth = STROKE;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    const p = { moveTo: (x, y) => ctx.moveTo(x, y), lineTo: (x, y) => ctx.lineTo(x, y), quadTo: (a, b, x, y) => ctx.quadraticCurveTo(a, b, x, y) };
    strokes.current.forEach((s) => smoothPath(s, p));
    ctx.stroke();
  }

  useEffect(() => {
    redraw();
    const el = canvas.current;
    let cur = null;
    const pos = (e) => { const r = el.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
    const down = (e) => { el.setPointerCapture?.(e.pointerId); cur = [pos(e)]; strokes.current.push(cur); redraw(); };
    const move = (e) => {
      if (!cur) return;
      const p = pos(e), l = cur[cur.length - 1];
      if (Math.hypot(p.x - l.x, p.y - l.y) >= 1.5) { cur.push(p); redraw(); }
    };
    const up = () => { if (cur) { cur = null; setCount(strokes.current.length); } };
    el.addEventListener('pointerdown', down);
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    return () => {
      el.removeEventListener('pointerdown', down);
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up);
      el.removeEventListener('pointercancel', up);
    };
  }, [color]);

  function save() {
    const all = strokes.current.flat();
    if (!all.length) return;
    const pad = STROKE;
    const minX = Math.min(...all.map((p) => p.x)) - pad, minY = Math.min(...all.map((p) => p.y)) - pad;
    const bw = Math.max(Math.max(...all.map((p) => p.x)) + pad - minX, 1);
    const bh = Math.max(Math.max(...all.map((p) => p.y)) + pad - minY, 1);
    onSave({
      id: newId(),
      strokes: strokes.current.map((s) => s.map((p) => ({ x: (p.x - minX) / bw, y: (p.y - minY) / bw }))),
      aspect: Math.min(Math.max(bw / bh, 0.2), 20),
      color,
      width: STROKE / bw,
    });
  }

  return html`<div class="sheet-backdrop">
    <div class="sheet pad-sheet" role="dialog" aria-modal="true" aria-label="Nouvelle signature">
      <div class="sheet-head">
        <button class="btn text" onClick=${onCancel}>Annuler</button>
        <div class="sheet-title">Nouvelle signature</div>
        <button class="btn text strong" data-id="pad-save" disabled=${!count} onClick=${save}>Enregistrer</button>
      </div>
      <p class="muted">Signez dans le cadre avec le doigt. Elle sera enregistrée pour vos prochains documents.</p>
      <div class="pad">
        <canvas ref=${canvas} data-id="pad"></canvas>
        ${!count && html`<span class="pad-hint">Signez ici</span>`}
      </div>
      <div class="row">
        ${SIGN_COLORS.map((c) => html`<button class=${'dot' + (color === c ? ' active' : '')} style=${{ '--c': hex(c) }}
          aria-label="Couleur" onClick=${() => setColor(c)}><i></i></button>`)}
        <span class="grow"></span>
        <button class="btn text" disabled=${!count} onClick=${() => { strokes.current.pop(); setCount(strokes.current.length); redraw(); }}>Annuler le trait</button>
        <button class="btn text" disabled=${!count} onClick=${() => { strokes.current = []; setCount(0); redraw(); }}>Effacer</button>
      </div>
    </div>
  </div>`;
}

export function Signatures() {
  const { signatures } = app.state;
  const [pad, setPad] = useState(false);
  const [deleting, setDeleting] = useState(null);
  return html`<div class="screen signatures">
    <header class="bar">
      <button class="icon-btn" aria-label="Retour" onClick=${A.goLibrary}><${Icon} name="left" /></button>
      <div class="bar-title"><div class="title big">Mes signatures</div></div>
    </header>
    ${signatures.length === 0
      ? html`<div class="empty">
          <span class="round-icon sun"><${Icon} name="signature" size=${40} /></span>
          <h2>Aucune signature</h2>
          <p>Créez votre signature une fois, puis posez-la sur n'importe quel PDF en un geste.</p>
        </div>`
      : html`<div class="sig-list">
          ${signatures.map((g) => html`<div key=${g.id} class="sig-card">
            <${SignaturePreview} sig=${g} />
            <button class="icon-btn" aria-label="Supprimer" onClick=${() => setDeleting(g)}><${Icon} name="trash" /></button>
          </div>`)}
        </div>`}
    <button class="fab" onClick=${() => setPad(true)}><${Icon} name="plus" />Nouvelle signature</button>
    ${pad && html`<${SignaturePad} onCancel=${() => setPad(false)} onSave=${async (sig) => { await A.addSignature(sig); setPad(false); }} />`}
    ${deleting && html`<${Confirm} title="Supprimer cette signature ?" text="Les PDF déjà signés ne sont pas modifiés."
      onClose=${() => setDeleting(null)}
      actions=${[
        { label: 'Annuler', onClick: () => setDeleting(null) },
        { label: 'Supprimer', kind: 'danger', onClick: () => { A.removeSignature(deleting); setDeleting(null); } },
      ]} />`}
  </div>`;
}

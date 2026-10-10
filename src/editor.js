// Éditeur : page + couche d'annotations, outils, pages, enregistrement.
import { useEffect, useLayoutEffect, useRef, useState } from '../vendor/hooks.module.js';
import { html, Icon, Sheet, Confirm, useObservable, app, toast } from './ui.js';
import * as A from './actions.js';
import { renderPage } from './pdfview.js';
import { TOOLS } from './session.js';
import {
  PEN_COLORS, HIGHLIGHT_COLORS, PEN_WIDTHS, TEXT_SIZES, HIGHLIGHT_WIDTH,
  hex, dist, add, sub, mul, rot, isPlaced, boxSize, corner, resized, drawAnnots,
} from './model.js';
import { SignaturePad, SignaturePreview } from './signatures.js';

export function Editor({ session, returnTo }) {
  const s = useObservable(session);
  const signatures = app.state.signatures;
  const [confirmExit, setConfirmExit] = useState(false);
  const [confirmSave, setConfirmSave] = useState(false);
  const [pagesSheet, setPagesSheet] = useState(false);
  const close = () => (s.dirty ? setConfirmExit(true) : A.closeEditor(s, returnTo));

  return html`<div class="screen editor">
    <header class="bar">
      <button class="icon-btn" aria-label="Fermer" onClick=${close}><${Icon} name="x" /></button>
      <div class="bar-title"><div class="title">Modifier</div><div class="sub">${s.name}</div></div>
      <button class="icon-btn" aria-label="Annuler" data-id="undo" disabled=${!s.canUndo} onClick=${() => s.undo()}><${Icon} name="undo" /></button>
      <button class="icon-btn" aria-label="Rétablir" data-id="redo" disabled=${!s.canRedo} onClick=${() => s.redo()}><${Icon} name="redo" /></button>
      <button class="btn primary small" data-id="save" disabled=${!s.dirty || s.saving}
        onClick=${() => (s.fileId ? setConfirmSave(true) : A.saveEdits(s, true))}>Enregistrer</button>
    </header>

    <div class="editor-area">
      <${PageArea} s=${s} />
      <div class="pagenav">
        <button aria-label="Page précédente" disabled=${s.current === 0} onClick=${() => s.goTo(s.current - 1)}><${Icon} name="left" size=${20} /></button>
        <span>Page ${s.current + 1} / ${s.pages.length}</span>
        <button aria-label="Page suivante" disabled=${s.current >= s.pages.length - 1} onClick=${() => s.goTo(s.current + 1)}><${Icon} name="right" size=${20} /></button>
        <i class="sep"></i>
        <button class="sun" aria-label="Organiser les pages" onClick=${() => setPagesSheet(true)}><${Icon} name="grid" size=${20} /></button>
      </div>
    </div>

    <footer class="tools">
      <${ToolOptions} s=${s} signatures=${signatures} />
      <nav class="toolbar">
        ${TOOLS.map((t) => html`<button key=${t.id} data-id=${'tool-' + t.id} class=${s.tool === t.id ? 'active' : ''}
          aria-pressed=${s.tool === t.id} onClick=${() => { s.setTool(t.id); if (t.id === 'signature' && !signatures.length) s.set({ padOpen: true }); }}>
          <span class="tool-icon"><${Icon} name=${t.icon} size=${20} /></span><span>${t.label}</span>
        </button>`)}
      </nav>
    </footer>

    ${s.textDialog && html`<${TextDialog} d=${s.textDialog} onCancel=${() => s.set({ textDialog: null })} onOk=${(t) => s.confirmText(t)} />`}
    ${s.padOpen && html`<${SignaturePad} onCancel=${() => s.set({ padOpen: false })}
      onSave=${async (sig) => { await A.addSignature(sig); s.signature = sig; s.padOpen = false; s.setTool('signature'); toast('Touchez la page à l\'endroit où signer'); }} />`}
    ${pagesSheet && html`<${PagesSheet} s=${s} onClose=${() => setPagesSheet(false)} />`}
    ${confirmExit && html`<${Confirm} title="Quitter sans enregistrer ?" text="Les modifications apportées à ce PDF seront perdues."
      onClose=${() => setConfirmExit(false)}
      actions=${[
        { label: 'Continuer', onClick: () => setConfirmExit(false) },
        { label: 'Quitter', kind: 'danger', onClick: () => A.closeEditor(s, returnTo) },
      ]} />`}
    ${confirmSave && html`<${Confirm} title="Enregistrer les modifications"
      text="Remplacer le document d'origine, ou garder l'original et créer une copie modifiée ?"
      onClose=${() => setConfirmSave(false)}
      actions=${[
        { label: 'Annuler', onClick: () => setConfirmSave(false) },
        { label: 'Créer une copie', onClick: () => { setConfirmSave(false); A.saveEdits(s, true); } },
        { label: 'Remplacer', kind: 'primary', onClick: () => { setConfirmSave(false); A.saveEdits(s, false); } },
      ]} />`}
    ${s.saving && html`<div class="sheet-backdrop center"><div class="dialog row"><div class="spinner small"></div>Enregistrement du PDF…</div></div>`}
  </div>`;
}

// ---------------------------------------------------------------- Page

function PageArea({ s }) {
  const area = useRef(null);
  const pageCanvas = useRef(null);
  const overlay = useRef(null);
  const outer = useRef(null);
  const [box, setBox] = useState({ w: 0, h: 0 });
  const [view, setView] = useState({ zoom: 1, x: 0, y: 0 });
  const viewRef = useRef(view);
  viewRef.current = view;
  const page = s.page;
  const E = page.rotation;

  useEffect(() => {
    const ro = new ResizeObserver(() => setBox({ w: area.current.clientWidth, h: area.current.clientHeight }));
    ro.observe(area.current);
    return () => ro.disconnect();
  }, []);
  useEffect(() => setView({ zoom: 1, x: 0, y: 0 }), [s.current, page.src, E]);

  // Taille affichée : la page tournée tient dans la zone (marges + place pour la navigation)
  const turned = E % 180 !== 0;
  const ratio = turned ? page.height / page.width : page.width / page.height;
  const availW = Math.max(box.w - 32, 1), availH = Math.max(box.h - 16 - 64, 1);
  let dw = availW, dh = availW / ratio;
  if (dh > availH) { dh = availH; dw = dh * ratio; }
  const iw = turned ? dh : dw, ih = turned ? dw : dh;
  const pxPerPt = iw / page.width;
  const dpr = Math.min(window.devicePixelRatio || 1, 3);

  // Rendu de la page (plus fin quand on zoome)
  const hiRes = view.zoom > 1.3 ? Math.min(view.zoom, 3) : 1;
  useEffect(() => {
    if (!box.w || !pageCanvas.current) return;
    const t = setTimeout(() => renderPage(s.doc, page.src, pageCanvas.current, iw * hiRes, 0, 3).catch(() => {}), 30);
    return () => clearTimeout(t);
  }, [page.src, iw, hiRes, box.w]);

  // Dessin des annotations + sélection
  useLayoutEffect(() => {
    const c = overlay.current;
    if (!c || !box.w) return;
    const k = dpr * hiRes;
    const W = Math.round(iw * k), H = Math.round(ih * k);
    if (c.width !== W || c.height !== H) { c.width = W; c.height = H; }
    const ctx = c.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, W, H);
    ctx.setTransform(k * pxPerPt, 0, 0, k * pxPerPt, 0, 0);
    drawAnnots(ctx, page.annots);
    const sel = s.annot(s.selected);
    if (sel && isPlaced(sel)) {
      const b = boxSize(sel);
      const cs = [{ x: 0, y: 0 }, { x: b.w, y: 0 }, { x: b.w, y: b.h }, { x: 0, y: b.h }].map((p) => mul(corner(sel, p), pxPerPt * k));
      const z = view.zoom;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.strokeStyle = '#c7362c';
      ctx.lineWidth = (1.5 * k) / z;
      ctx.setLineDash([(8 * k) / z, (6 * k) / z]);
      ctx.beginPath();
      cs.forEach((p, i) => (i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)));
      ctx.closePath();
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = '#fff';
      ctx.beginPath(); ctx.arc(cs[2].x, cs[2].y, (11 * k) / z, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#c7362c';
      ctx.beginPath(); ctx.arc(cs[2].x, cs[2].y, (8 * k) / z, 0, Math.PI * 2); ctx.fill();
    }
  });

  // ---- Gestes : 1 doigt = outil, 2 doigts = zoom / déplacement ----
  useEffect(() => {
    const el = area.current;
    const pointers = new Map();
    let drag = null;       // geste d'outil en cours
    let pinch = null;      // zoom en cours

    const toPt = (cx, cy) => {
      const r = outer.current.getBoundingClientRect();
      const z = viewRef.current.zoom;
      const v = { x: (cx - (r.left + r.width / 2)) / z, y: (cy - (r.top + r.height / 2)) / z };
      const local = add(rot(v, -s.page.rotation), { x: iwRef.current / 2, y: ihRef.current / 2 });
      return mul(local, 1 / pxRef.current);
    };
    const tol = () => 18 / pxRef.current / viewRef.current.zoom;

    function cancelDrag() {
      if (drag && drag.kind !== 'tap') s.cancelGesture();
      drag = null;
    }

    function down(e) {
      el.setPointerCapture?.(e.pointerId);
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pointers.size === 2) {
        cancelDrag();
        const [a, b] = [...pointers.values()];
        pinch = { d: Math.hypot(a.x - b.x, a.y - b.y), m: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }, v: viewRef.current };
        return;
      }
      if (pointers.size > 2) return;
      const p0 = toPt(e.clientX, e.clientY);
      const t = s.tool;
      if (t === 'pen' || t === 'highlighter') {
        const hl = t === 'highlighter';
        drag = { kind: 'ink', id: s.newId(), color: hl ? s.highlightColor : s.penColor, width: hl ? HIGHLIGHT_WIDTH : s.penWidth, hl, points: [p0] };
        s.beginGesture();
        updateInk();
      } else if (t === 'eraser') {
        drag = { kind: 'erase' };
        s.beginGesture();
        erase(p0);
      } else if (t === 'text' || t === 'signature') {
        drag = { kind: 'tap', p0 };
      } else {
        const prev = s.annot(s.selected);
        let onHandle = false;
        if (prev && isPlaced(prev)) {
          const b = boxSize(prev);
          onHandle = dist(corner(prev, { x: b.w, y: b.h }), p0) <= tol() * 1.4;
        }
        const target = onHandle ? prev : s.hitPlaced(p0, tol());
        if (!target) { if (s.selected != null) s.set({ selected: null }); drag = null; return; }
        const wasSelected = s.selected === target.id;
        s.selected = target.id;
        s.beginGesture();
        drag = { kind: 'move', target, p0, onHandle, box: boxSize(target), moved: false, wasSelected };
        s.emit();
      }
    }

    function move(e) {
      if (!pointers.has(e.pointerId)) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pinch && pointers.size >= 2) {
        const [a, b] = [...pointers.values()];
        const d = Math.hypot(a.x - b.x, a.y - b.y);
        const m = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
        const zoom = Math.min(Math.max(pinch.v.zoom * (d / pinch.d), 1), 6);
        const maxX = (box2.current.w * (zoom - 1)) / 2 + 40, maxY = (box2.current.h * (zoom - 1)) / 2 + 40;
        const x = Math.min(Math.max(pinch.v.x + m.x - pinch.m.x, -maxX), maxX);
        const y = Math.min(Math.max(pinch.v.y + m.y - pinch.m.y, -maxY), maxY);
        setView(zoom === 1 ? { zoom: 1, x: 0, y: 0 } : { zoom, x, y });
        return;
      }
      if (!drag) return;
      const p = toPt(e.clientX, e.clientY);
      if (drag.kind === 'ink') {
        if (dist(p, drag.points[drag.points.length - 1]) >= 1 / pxRef.current / viewRef.current.zoom) {
          drag.points = [...drag.points, p];
          updateInk();
        }
      } else if (drag.kind === 'erase') {
        erase(p);
      } else if (drag.kind === 'move') {
        if (!drag.moved && dist(p, drag.p0) < 8 / pxRef.current / viewRef.current.zoom) return;
        drag.moved = true;
        const t = drag.target;
        const upd = drag.onHandle ? resized(t, drag.box, p) : { ...t, ...add({ x: t.x, y: t.y }, sub(p, drag.p0)) };
        s.live(s.replacing(upd));
      }
    }

    function up(e) {
      pointers.delete(e.pointerId);
      if (pinch) { if (pointers.size < 2) pinch = null; return; }
      const d = drag;
      drag = null;
      if (!d) return;
      if (e.type === 'pointercancel') { if (d.kind !== 'tap') s.cancelGesture(); return; }
      if (d.kind === 'ink' || d.kind === 'erase') s.endGesture();
      else if (d.kind === 'move') {
        if (d.moved) s.endGesture();
        else {
          s.cancelGesture();
          if (d.wasSelected && !d.onHandle && d.target.type === 'text') s.openEditText(d.target);
        }
      } else if (d.kind === 'tap') {
        if (s.tool === 'text') {
          const h = s.hitPlaced(d.p0, tol());
          if (h && h.type === 'text') s.openEditText(h); else s.openNewText(d.p0);
        } else if (s.signature) s.placeSignature(d.p0, s.signature);
        else s.set({ padOpen: true });
      }
    }

    function updateInk() {
      const ink = { type: 'ink', id: drag.id, points: drag.points, color: drag.color, width: drag.width, highlighter: drag.hl };
      s.live(s.mapAnnots((l) => [...l.filter((a) => a.id !== drag.id), ink]));
    }
    function erase(p) {
      const hit = s.hitAny(p, tol());
      if (hit.size) s.live(s.mapAnnots((l) => l.filter((a) => !hit.has(a.id))));
    }

    const noGesture = (e) => e.preventDefault();
    el.addEventListener('pointerdown', down);
    el.addEventListener('pointermove', move);
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    el.addEventListener('gesturestart', noGesture);
    return () => {
      el.removeEventListener('pointerdown', down);
      el.removeEventListener('pointermove', move);
      el.removeEventListener('pointerup', up);
      el.removeEventListener('pointercancel', up);
      el.removeEventListener('gesturestart', noGesture);
    };
  }, [s]);

  // Valeurs courantes lues par les gestionnaires (installés une seule fois)
  const iwRef = useRef(iw); iwRef.current = iw;
  const ihRef = useRef(ih); ihRef.current = ih;
  const pxRef = useRef(pxPerPt); pxRef.current = pxPerPt;
  const box2 = useRef(box); box2.current = box;

  return html`<div class="page-area" ref=${area}>
    ${box.w > 0 && html`<div class="page-outer" ref=${outer} style=${{
      width: `${dw}px`, height: `${dh}px`,
      left: `${(box.w - dw) / 2}px`, top: `${Math.max((box.h - 64 - dh) / 2, 8)}px`,
      transform: `translate(${view.x}px, ${view.y}px) scale(${view.zoom})`,
    }}>
      <div class="page-inner" style=${{
        width: `${iw}px`, height: `${ih}px`,
        left: `${(dw - iw) / 2}px`, top: `${(dh - ih) / 2}px`,
        transform: `rotate(${E}deg)`,
      }}>
        <canvas ref=${pageCanvas} class="fill"></canvas>
        <canvas ref=${overlay} class="fill"></canvas>
      </div>
    </div>`}
  </div>`;
}

// ---------------------------------------------------------------- Outils

function Dot({ color, active, onClick }) {
  return html`<button class=${'dot' + (active ? ' active' : '')} style=${{ '--c': hex(color) }} aria-label="Couleur"
    aria-pressed=${active} onClick=${onClick}><i></i></button>`;
}

function ToolOptions({ s, signatures }) {
  let body;
  switch (s.tool) {
    case 'select': {
      const sel = s.annot(s.selected);
      body = sel && isPlaced(sel)
        ? html`${sel.type === 'text' && html`<button class="chip" onClick=${() => s.openEditText(sel)}><${Icon} name="pencil" size=${16} />Modifier le texte</button>`}
            <button class="chip danger" onClick=${() => s.deleteSelected()}><${Icon} name="trash" size=${16} />Supprimer</button>
            ${sel.type !== 'text' && html`<span class="hint">Poignée ● pour agrandir</span>`}`
        : html`<span class="hint">Touchez un texte ou une signature pour le déplacer. Pincez pour zoomer.</span>`;
      break;
    }
    case 'pen':
      body = html`${PEN_COLORS.map((c) => html`<${Dot} color=${c} active=${s.penColor === c} onClick=${() => s.set({ penColor: c })} />`)}
        <span class="grow"></span>
        ${PEN_WIDTHS.map((w) => html`<button class=${'size' + (s.penWidth === w ? ' active' : '')} aria-label="Épaisseur" onClick=${() => s.set({ penWidth: w })}>
          <i style=${{ width: `${w * 3.2}px`, height: `${w * 3.2}px`, background: hex(s.penColor) }}></i></button>`)}`;
      break;
    case 'highlighter':
      body = html`${HIGHLIGHT_COLORS.map((c) => html`<${Dot} color=${c} active=${s.highlightColor === c} onClick=${() => s.set({ highlightColor: c })} />`)}
        <span class="hint">Glissez sur le texte à surligner</span>`;
      break;
    case 'text':
      body = html`${PEN_COLORS.map((c) => html`<${Dot} color=${c} active=${s.textColor === c} onClick=${() => s.set({ textColor: c })} />`)}
        <span class="grow"></span>
        ${TEXT_SIZES.map((z, i) => html`<button class=${'size' + (s.textSize === z ? ' active' : '')} onClick=${() => s.set({ textSize: z })}>${['S', 'M', 'L'][i]}</button>`)}`;
      break;
    case 'signature':
      body = html`<button class="chip sun" onClick=${() => s.set({ padOpen: true })}><${Icon} name="plus" size=${16} />Nouvelle</button>
        ${signatures.map((g) => html`<button key=${g.id} class=${'sigchip' + (s.signature?.id === g.id ? ' active' : '')}
          aria-label="Choisir cette signature" onClick=${() => s.set({ signature: g })}><${SignaturePreview} sig=${g} /></button>`)}
        ${signatures.length > 0 && html`<span class="hint nowrap">Touchez la page à l'endroit où signer</span>`}`;
      break;
    default:
      body = html`<span class="hint">Glissez sur un trait, un texte ou une signature pour l'effacer.</span>`;
  }
  return html`<div class="options">${body}</div>`;
}

// ---------------------------------------------------------------- Pages

function PagesSheet({ s, onClose }) {
  return html`<${Sheet} title="Organiser les pages" onClose=${onClose} tall>
    <p class="muted pad-x">Pivoter, déplacer ou supprimer. Touchez une page pour l'ouvrir.</p>
    <div class="pages-grid">
      ${s.pages.map((p, i) => html`<div key=${p.src} class=${'page-card' + (i === s.current ? ' current' : '')}>
        <button class="page-thumb" onClick=${() => { s.goTo(i); onClose(); }} aria-label=${`Ouvrir la page ${i + 1}`}>
          <${Thumb} doc=${s.doc} page=${p} />
          <span class="num">${i + 1}</span>
          ${p.annots.length > 0 && html`<span class="mark"></span>`}
        </button>
        <div class="page-actions">
          <button aria-label="Avancer" disabled=${i === 0} onClick=${() => s.movePage(i, -1)}><${Icon} name="left" size=${18} /></button>
          <button aria-label="Pivoter" onClick=${() => s.rotatePage(i)}><${Icon} name="rotate" size=${18} /></button>
          <button aria-label="Supprimer" onClick=${() => { if (!s.deletePage(i)) toast('Un PDF doit garder au moins une page'); }}><${Icon} name="trash" size=${18} /></button>
          <button aria-label="Reculer" disabled=${i === s.pages.length - 1} onClick=${() => s.movePage(i, 1)}><${Icon} name="right" size=${18} /></button>
        </div>
      </div>`)}
    </div>
  <//>`;
}

function Thumb({ doc, page }) {
  const c = useRef(null);
  useEffect(() => { renderPage(doc, page.src, c.current, 120, page.rotation, 2).catch(() => {}); }, [page.src, page.rotation]);
  return html`<canvas ref=${c}></canvas>`;
}

// ---------------------------------------------------------------- Texte

function TextDialog({ d, onCancel, onOk }) {
  const [text, setText] = useState(d.initial);
  const ta = useRef(null);
  useEffect(() => { ta.current?.focus(); }, []);
  const insert = (t) => setText((v) => (v && !/[\s]$/.test(v) ? `${v} ${t}` : v + t));
  return html`<div class="sheet-backdrop" onClick=${onCancel}>
    <div class="sheet" role="dialog" aria-modal="true" aria-label="Texte" onClick=${(e) => e.stopPropagation()}>
      <div class="sheet-head">
        <button class="btn text" onClick=${onCancel}>Annuler</button>
        <div class="sheet-title">${d.editId == null ? 'Ajouter du texte' : 'Modifier le texte'}</div>
        <button class="btn text strong" onClick=${() => onOk(text)}>OK</button>
      </div>
      <textarea ref=${ta} class="field" rows="3" aria-label="Texte" value=${text} onInput=${(e) => setText(e.target.value)}></textarea>
      <div class="row gap">
        <button class="chip" onClick=${() => insert(new Date().toLocaleDateString('fr-FR'))}>Date du jour</button>
        <button class="chip" onClick=${() => insert('Lu et approuvé')}>Lu et approuvé</button>
      </div>
    </div>
  </div>`;
}

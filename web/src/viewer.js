// Visionneuse : pages en défilement vertical, rendu à la demande, zoom au pincement.
import { useEffect, useLayoutEffect, useRef, useState } from '../vendor/hooks.module.js';
import { html, Icon, shareFile } from './ui.js';
import * as A from './actions.js';
import { openPdf, pageSizes, renderPage } from './pdfview.js';

export function Viewer({ doc }) {
  const [pdf, setPdf] = useState(null);
  const [sizes, setSizes] = useState([]);
  const [failed, setFailed] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [current, setCurrent] = useState(1);
  const [width, setWidth] = useState(0);
  const scroller = useRef(null);
  const content = useRef(null);
  const pending = useRef(null);

  useEffect(() => {
    let alive = true, d = null;
    openPdf(doc.bytes).then(async (p) => {
      d = p;
      const s = await pageSizes(p);
      if (alive) { setPdf(p); setSizes(s); }
    }).catch(() => alive && setFailed(true));
    return () => { alive = false; d?.destroy(); };
  }, [doc.bytes]);

  useEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    return () => ro.disconnect();
  }, [pdf]);

  // Garde le point de pincement immobile après changement de zoom
  useLayoutEffect(() => {
    const p = pending.current;
    if (!p || !scroller.current) return;
    pending.current = null;
    scroller.current.scrollLeft = p.left;
    scroller.current.scrollTop = p.top;
  }, [zoom]);

  // Pincement : transformation CSS pendant le geste, nouveau rendu à la fin
  useEffect(() => {
    const el = scroller.current, c = content.current;
    if (!el || !c) return;
    let start = null;
    const mid = (t) => ({ x: (t[0].clientX + t[1].clientX) / 2, y: (t[0].clientY + t[1].clientY) / 2 });
    const gap = (t) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY);
    const onStart = (e) => {
      if (e.touches.length !== 2) return;
      const r = el.getBoundingClientRect(), m = mid(e.touches);
      start = { d: gap(e.touches), vx: m.x - r.left, vy: m.y - r.top, cx: m.x - r.left + el.scrollLeft, cy: m.y - r.top + el.scrollTop, s: 1 };
      c.style.transformOrigin = `${start.cx}px ${start.cy}px`;
    };
    const onMove = (e) => {
      if (!start || e.touches.length !== 2) return;
      e.preventDefault();
      start.s = Math.min(Math.max(gap(e.touches) / start.d, 1 / zoom), 5 / zoom);
      c.style.transform = `scale(${start.s})`;
    };
    const onEnd = (e) => {
      if (!start || e.touches.length > 0 && e.touches.length !== 1) return;
      const s = start.s, z = Math.min(Math.max(zoom * s, 1), 5);
      const k = z / zoom;
      c.style.transform = '';
      pending.current = { left: start.cx * k - start.vx, top: start.cy * k - start.vy };
      start = null;
      if (k !== 1) setZoom(z);
    };
    el.addEventListener('touchstart', onStart, { passive: true });
    el.addEventListener('touchmove', onMove, { passive: false });
    el.addEventListener('touchend', onEnd);
    el.addEventListener('touchcancel', onEnd);
    return () => {
      el.removeEventListener('touchstart', onStart);
      el.removeEventListener('touchmove', onMove);
      el.removeEventListener('touchend', onEnd);
      el.removeEventListener('touchcancel', onEnd);
    };
  }, [pdf, zoom]);

  const pageW = Math.max(width - 24, 100) * zoom;

  function onScroll() {
    const el = scroller.current;
    if (!el || !sizes.length) return;
    const kids = content.current.children;
    const mid = el.scrollTop + el.clientHeight / 3;
    for (let i = 0; i < kids.length; i++) {
      if (kids[i].offsetTop + kids[i].offsetHeight > mid) { setCurrent(i + 1); break; }
    }
  }

  return html`<div class="screen viewer">
    <header class="bar">
      <button class="icon-btn" aria-label="Retour" onClick=${A.goLibrary}><${Icon} name="left" /></button>
      <div class="bar-title">
        <div class="title">${doc.name}</div>
        ${pdf && html`<div class="sub">${pdf.numPages > 1 ? `${pdf.numPages} pages` : '1 page'}</div>`}
      </div>
      ${!doc.fileId && html`<button class="icon-btn" aria-label="Enregistrer dans Mes PDF" onClick=${() => A.saveToLibrary(doc)}><${Icon} name="download" /></button>`}
    </header>
    <div class="viewer-area">
      ${failed && html`<p class="center pad">Impossible d'ouvrir ce PDF (protégé par mot de passe ou endommagé).</p>`}
      ${!pdf && !failed && html`<div class="spinner" aria-label="Chargement"></div>`}
      <div class="scroller" ref=${scroller} onScroll=${onScroll}>
        <div class="pages" ref=${content} style=${{ width: `${pageW + 24}px` }}>
          ${pdf && width > 0 && sizes.map((s, i) => html`<${Page} key=${i} pdf=${pdf} index=${i} size=${s} width=${pageW} root=${scroller} />`)}
        </div>
      </div>
      ${pdf && html`<div class="pill top">${current} / ${pdf.numPages}</div>`}
      ${pdf && html`<div class="actionbar">
        <button onClick=${() => A.edit(doc, 'select', { name: 'viewer', doc })}><${Icon} name="pencil" size=${20} />Modifier</button>
        <button class="accent" onClick=${() => A.edit(doc, 'signature', { name: 'viewer', doc })}><${Icon} name="signature" size=${20} />Signer</button>
        <button onClick=${() => shareFile(doc.bytes, doc.name)}><${Icon} name="share" size=${20} />Partager</button>
      </div>`}
    </div>
  </div>`;
}

function Page({ pdf, index, size, width, root }) {
  const canvas = useRef(null);
  const box = useRef(null);
  const [visible, setVisible] = useState(false);
  const h = (width * size.height) / size.width;

  useEffect(() => {
    const io = new IntersectionObserver(([e]) => setVisible(e.isIntersecting), { root: root.current, rootMargin: '600px 0px' });
    io.observe(box.current);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    const c = canvas.current;
    if (!visible) { c.width = 0; c.height = 0; return; } // libère la mémoire
    const t = setTimeout(() => renderPage(pdf, index, c, width).catch(() => {}), 60);
    return () => clearTimeout(t);
  }, [visible, width]);

  return html`<div class="page" ref=${box} style=${{ width: `${width}px`, height: `${h}px` }}>
    <canvas ref=${canvas} aria-label=${`Page ${index + 1}`}></canvas>
  </div>`;
}

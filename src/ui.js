// Briques d'interface communes : html (htm + Preact), icônes, état global, toast.
import { h, render } from '../vendor/preact.module.js';
import { useEffect, useReducer } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { ICONS } from './icons.js';

export const html = htm.bind(h);
export { render };

export function Icon({ name, size = 22, stroke = 2, cls = '' }) {
  return html`<svg class=${'icon ' + cls} width=${size} height=${size} viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width=${stroke} stroke-linecap="round" stroke-linejoin="round"
    aria-hidden="true" dangerouslySetInnerHTML=${{ __html: ICONS[name] || '' }}></svg>`;
}

/** Petit magasin observable : objet + abonnés. */
export function createStore(initial) {
  const listeners = new Set();
  const store = {
    state: initial,
    set(patch) {
      store.state = { ...store.state, ...(typeof patch === 'function' ? patch(store.state) : patch) };
      listeners.forEach((l) => l());
    },
    subscribe(l) { listeners.add(l); return () => listeners.delete(l); },
  };
  return store;
}

/** Ré-affiche le composant quand l'objet observable [obs] (avec subscribe) change. */
export function useObservable(obs) {
  const [, force] = useReducer((x) => x + 1, 0);
  useEffect(() => obs.subscribe(force), [obs]);
  return obs;
}

export const app = createStore({ screen: { name: 'library' }, files: [], signatures: [], toast: null });

let toastTimer = 0;
export function toast(text) {
  app.set({ toast: text });
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => app.set({ toast: null }), 2600);
}

export const isStandalone = () =>
  window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;

export const isIOS = () => /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

export function formatSize(n) {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} Ko`;
  return `${(n / 1024 / 1024).toFixed(1).replace('.', ',')} Mo`;
}

export function formatDate(t) {
  return new Date(t).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Feuille d'actions en bas d'écran. */
export function Sheet({ title, onClose, children, tall = false }) {
  useEffect(() => {
    const k = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [onClose]);
  return html`<div class="sheet-backdrop" onClick=${onClose}>
    <div class=${'sheet' + (tall ? ' tall' : '')} role="dialog" aria-modal="true" aria-label=${title}
      onClick=${(e) => e.stopPropagation()}>
      <div class="sheet-grip"></div>
      ${title && html`<div class="sheet-title">${title}</div>`}
      ${children}
    </div>
  </div>`;
}

/** Boîte de confirmation. */
export function Confirm({ title, text, actions, onClose }) {
  return html`<div class="sheet-backdrop center" onClick=${onClose}>
    <div class="dialog" role="alertdialog" aria-modal="true" aria-label=${title} onClick=${(e) => e.stopPropagation()}>
      <div class="dialog-title">${title}</div>
      ${text && html`<p class="dialog-text">${text}</p>`}
      <div class="dialog-actions">
        ${actions.map((a) => html`<button class=${'btn ' + (a.kind || 'text')} onClick=${a.onClick}>${a.label}</button>`)}
      </div>
    </div>
  </div>`;
}

/** Partage (feuille système iOS) avec repli sur un téléchargement. */
export async function shareFile(bytes, name) {
  const file = new File([bytes], `${name}.pdf`, { type: 'application/pdf' });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: name });
      return;
    } catch (e) {
      if (e?.name === 'AbortError') return;
    }
  }
  downloadFile(bytes, name);
}

export function downloadFile(bytes, name) {
  const url = URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `${name}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

/** Choisit un fichier (PDF ou images). */
export function pickFiles({ accept, multiple = false, capture = null }) {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;
    if (capture) input.setAttribute('capture', capture);
    input.style.display = 'none';
    input.onchange = () => { resolve([...(input.files || [])]); input.remove(); };
    document.body.appendChild(input);
    input.click();
  });
}

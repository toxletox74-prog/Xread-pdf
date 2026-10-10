// Accueil « Mes PDF ».
import { useEffect, useState } from '../vendor/hooks.module.js';
import { html, Icon, app, toast, Sheet, Confirm, formatDate, formatSize, shareFile, pickFiles, isIOS, isStandalone } from './ui.js';
import * as A from './actions.js';
import * as store from './store.js';
import { thumbnail } from './pdfview.js';

export function Library() {
  const { files, signatures } = app.state;
  const [query, setQuery] = useState('');
  const [menu, setMenu] = useState(null);       // { meta, bytes }
  const [renaming, setRenaming] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const q = query.trim().toLowerCase();
  const shown = q ? files.filter((f) => f.name.toLowerCase().includes(q)) : files;
  const n = signatures.length;

  async function openMenu(meta) {
    setMenu({ meta, bytes: null });
    // Octets préchargés : le partage iOS doit partir directement du geste
    const bytes = await store.getBytes(meta.id);
    setMenu((m) => (m && m.meta.id === meta.id ? { meta, bytes } : m));
  }

  async function importPdf() {
    const [f] = await pickFiles({ accept: 'application/pdf,.pdf' });
    if (f) A.openExternal(f);
  }

  return html`<div class="screen library">
    <header class="hero">
      <div class="hero-brand">
        <img class="brandmark" src="icons/mark.png" alt="" width="52" height="52" />
        <div>
          <h1>Xread PDF</h1>
          <p>Scanner · Modifier · Signer · Partager</p>
        </div>
      </div>
      <div class="tiles">
        <button class="tile coral" onClick=${() => A.go({ name: 'scanner' })}>
          <span class="tile-icon"><${Icon} name="scan" /></span>
          <span class="tile-label">Scanner</span><span class="tile-hint">Appareil photo</span>
        </button>
        <button class="tile sky" onClick=${importPdf}>
          <span class="tile-icon"><${Icon} name="folder" /></span>
          <span class="tile-label">Ouvrir</span><span class="tile-hint">Un PDF</span>
        </button>
        <button class="tile sun" onClick=${() => A.go({ name: 'signatures' })}>
          <span class="tile-icon"><${Icon} name="signature" /></span>
          <span class="tile-label">Signatures</span>
          <span class="tile-hint">${n === 0 ? 'À créer' : `${n} enregistrée${n > 1 ? 's' : ''}`}</span>
        </button>
      </div>
    </header>

    ${isIOS() && !isStandalone() && html`<${InstallHint} />`}

    ${files.length === 0
      ? html`<div class="empty">
          <img class="brandmark big" src="icons/mark.png" alt="" width="96" height="96" />
          <h2>Rien ici… pour l'instant</h2>
          <p>Scannez un document ou ouvrez un PDF pour le lire, l'annoter et le signer.</p>
        </div>`
      : html`<section class="docs">
          <label class="search">
            <${Icon} name="search" size=${20} />
            <input type="search" placeholder="Rechercher un document" value=${query}
              onInput=${(e) => setQuery(e.target.value)} aria-label="Rechercher un document" />
            ${query && html`<button class="icon-btn small" aria-label="Effacer la recherche" onClick=${() => setQuery('')}><${Icon} name="x" size=${18} /></button>`}
          </label>
          <div class="section-title">Mes documents <span class="badge">${shown.length}</span></div>
          ${shown.length === 0 && html`<p class="muted center pad">Aucun document ne correspond à « ${query} ».</p>`}
          <div class="grid">
            ${shown.map((f) => html`<${DocCard} key=${f.id} meta=${f} onMenu=${() => openMenu(f)} />`)}
          </div>
        </section>`}

    ${menu && html`<${Sheet} title=${menu.meta.name} onClose=${() => setMenu(null)}>
      <div class="menu">
        <button onClick=${() => { setMenu(null); A.openFile(menu.meta); }}><${Icon} name="file" />Ouvrir</button>
        <button onClick=${async () => { const m = menu.meta; setMenu(null); A.edit({ fileId: m.id, name: m.name, bytes: await store.getBytes(m.id) }, 'select', null); }}><${Icon} name="pencil" />Modifier</button>
        <button onClick=${async () => { const m = menu.meta; setMenu(null); A.edit({ fileId: m.id, name: m.name, bytes: await store.getBytes(m.id) }, 'signature', null); }}><${Icon} name="signature" />Signer</button>
        <button disabled=${!menu.bytes} onClick=${() => { shareFile(menu.bytes, menu.meta.name); setMenu(null); }}><${Icon} name="share" />Partager</button>
        <button onClick=${() => { setRenaming(menu.meta); setMenu(null); }}><${Icon} name="rename" />Renommer</button>
        <button class="danger" onClick=${() => { setDeleting(menu.meta); setMenu(null); }}><${Icon} name="trash" />Supprimer</button>
      </div>
    <//>`}

    ${renaming && html`<${RenameDialog} meta=${renaming} onClose=${() => setRenaming(null)} />`}
    ${deleting && html`<${Confirm} title="Supprimer ce PDF ?" text=${`« ${deleting.name} » sera définitivement supprimé.`}
      onClose=${() => setDeleting(null)}
      actions=${[
        { label: 'Annuler', onClick: () => setDeleting(null) },
        { label: 'Supprimer', kind: 'danger', onClick: () => { A.remove(deleting); setDeleting(null); } },
      ]} />`}
  </div>`;
}

function InstallHint() {
  const [hidden, setHidden] = useState(() => { try { return localStorage.getItem('xr-hint') === '1'; } catch { return false; } });
  if (hidden) return null;
  const close = () => { setHidden(true); try { localStorage.setItem('xr-hint', '1'); } catch {} };
  return html`<div class="install">
    <${Icon} name="download" />
    <div>
      <strong>Installer l'app sur l'iPhone</strong>
      <p>Touchez <b>Partager</b> <${Icon} name="share" size=${15} /> puis <b>Sur l'écran d'accueil</b>. Elle s'ouvrira en plein écran, même hors ligne.</p>
    </div>
    <button class="icon-btn small" aria-label="Fermer" onClick=${close}><${Icon} name="x" size=${18} /></button>
  </div>`;
}

function DocCard({ meta, onMenu }) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    let alive = true, made = null;
    (async () => {
      let blob = await store.getThumb(meta.id, meta.modified);
      if (!blob) {
        const bytes = await store.getBytes(meta.id);
        blob = bytes && (await thumbnail(bytes).catch(() => null));
        if (blob) store.putThumb(meta.id, meta.modified, blob);
      }
      if (alive && blob) setUrl((made = URL.createObjectURL(blob)));
    })();
    return () => { alive = false; if (made) URL.revokeObjectURL(made); };
  }, [meta.id, meta.modified]);

  return html`<article class="card">
    <button class="thumb" onClick=${() => A.openFile(meta)} aria-label=${'Ouvrir ' + meta.name}>
      ${url ? html`<img src=${url} alt="" />` : html`<${Icon} name="file" size=${36} cls="muted" />`}
    </button>
    <div class="card-row">
      <div class="card-text">
        <div class="card-name">${meta.name}</div>
        <div class="card-meta">${formatDate(meta.modified)} · ${formatSize(meta.size)}</div>
      </div>
      <button class="icon-btn" aria-label="Actions" onClick=${onMenu}><${Icon} name="more" /></button>
    </div>
  </article>`;
}

function RenameDialog({ meta, onClose }) {
  const [name, setName] = useState(meta.name);
  return html`<div class="sheet-backdrop center" onClick=${onClose}>
    <form class="dialog" onClick=${(e) => e.stopPropagation()} onSubmit=${(e) => { e.preventDefault(); A.rename(meta, name); onClose(); }}>
      <div class="dialog-title">Renommer</div>
      <input class="field" value=${name} onInput=${(e) => setName(e.target.value)} aria-label="Nom du document"
        ref=${(el) => el && !el.dataset.f && (el.dataset.f = '1', setTimeout(() => el.select(), 50))} />
      <div class="dialog-actions">
        <button type="button" class="btn text" onClick=${onClose}>Annuler</button>
        <button type="submit" class="btn primary" disabled=${!name.trim()}>OK</button>
      </div>
    </form>
  </div>`;
}

export { toast };

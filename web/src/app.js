// Point d'entrée : état global, navigation entre écrans, service worker.
import { useEffect } from '../vendor/hooks.module.js';
import { html, render, app, useObservable } from './ui.js';
import * as A from './actions.js';
import { askPersistence } from './store.js';
import { Library } from './library.js';
import { Viewer } from './viewer.js';
import { Editor } from './editor.js';
import { Signatures } from './signatures.js';
import { Scanner } from './scanner.js';

function Root() {
  useObservable(app);
  const { screen, toast } = app.state;
  useEffect(() => { A.refreshFiles(); A.refreshSignatures(); askPersistence(); }, []);
  let view;
  switch (screen.name) {
    case 'viewer': view = html`<${Viewer} key=${screen.doc.fileId || 'ext'} doc=${screen.doc} />`; break;
    case 'editor': view = html`<${Editor} session=${screen.session} returnTo=${screen.returnTo} />`; break;
    case 'signatures': view = html`<${Signatures} />`; break;
    case 'scanner': view = html`<${Scanner} />`; break;
    default: view = html`<${Library} />`;
  }
  return html`${view}${toast && html`<div class="toast" role="status">${toast}</div>`}`;
}

render(html`<${Root} />`, document.getElementById('app'));

// Pas de zoom de toute la page (on gère le pincement nous-mêmes)
document.addEventListener('gesturestart', (e) => e.preventDefault());

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

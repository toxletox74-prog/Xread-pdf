// Actions de l'application (navigation, fichiers, édition).
import { app, toast } from './ui.js';
import * as store from './store.js';
import { openPdf, pageSizes } from './pdfview.js';
import { EditorSession } from './session.js';
import { exportPdf } from './exporter.js';

export async function refreshFiles() { app.set({ files: await store.listFiles() }); }
export async function refreshSignatures() { app.set({ signatures: await store.listSignatures() }); }

export const go = (screen) => { app.set({ screen }); window.scrollTo(0, 0); };
export const goLibrary = () => go({ name: 'library' });

/** Document ouvert : { fileId (null si externe), name, bytes }. */
export async function openFile(meta) {
  const bytes = await store.getBytes(meta.id);
  if (!bytes) return toast('Fichier introuvable');
  go({ name: 'viewer', doc: { fileId: meta.id, name: meta.name, bytes } });
}

export async function openExternal(file) {
  try {
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (String.fromCharCode(...bytes.slice(0, 5)) !== '%PDF-') return toast('Ce fichier n\'est pas un PDF');
    go({ name: 'viewer', doc: { fileId: null, name: file.name.replace(/\.pdf$/i, '') || 'Document', bytes } });
  } catch {
    toast('Impossible d\'ouvrir ce fichier');
  }
}

export async function saveToLibrary(doc) {
  const name = store.uniqueName(doc.name, app.state.files);
  const meta = await store.saveFile({ name, bytes: doc.bytes });
  await refreshFiles();
  go({ name: 'viewer', doc: { ...doc, fileId: meta.id, name } });
  toast('Ajouté à Mes PDF');
}

export async function saveNewPdf(name, bytes) {
  const meta = await store.saveFile({ name: store.uniqueName(name, app.state.files), bytes });
  await refreshFiles();
  go({ name: 'viewer', doc: { fileId: meta.id, name: meta.name, bytes } });
}

export async function rename(meta, name) {
  const clean = name.replace(/[\\/:*?"<>|\n\r]/g, '_').trim().slice(0, 100);
  if (!clean) return;
  if (app.state.files.some((f) => f.name === clean && f.id !== meta.id)) return toast('Un fichier porte déjà ce nom');
  await store.renameFile(meta.id, clean);
  await refreshFiles();
}

export async function remove(meta) {
  await store.deleteFile(meta.id);
  await refreshFiles();
  toast('PDF supprimé');
}

export async function edit(doc, tool, returnTo) {
  let pdf;
  try { pdf = await openPdf(doc.bytes); } catch { return toast('Impossible de modifier ce PDF (protégé ?)'); }
  if (!pdf.numPages) return toast('Ce PDF ne contient aucune page');
  const session = new EditorSession({ fileId: doc.fileId, name: doc.name, bytes: doc.bytes, doc: pdf, sizes: await pageSizes(pdf), tool });
  session.signature = app.state.signatures[0] || null;
  if (tool === 'signature' && !session.signature) session.padOpen = true;
  go({ name: 'editor', session, returnTo });
}

export function closeEditor(session, returnTo) {
  session.doc.destroy();
  go(returnTo || { name: 'library' });
}

export async function saveEdits(session, asCopy) {
  if (session.saving) return;
  session.set({ saving: true });
  try {
    const out = await exportPdf(session.bytes, session.pages);
    let meta;
    if (session.fileId && !asCopy) {
      meta = await store.saveFile({ id: session.fileId, name: session.name, bytes: out });
    } else {
      const base = session.fileId ? `${session.name} - modifié` : session.name;
      meta = await store.saveFile({ name: store.uniqueName(base, app.state.files), bytes: out });
    }
    await refreshFiles();
    session.doc.destroy();
    go({ name: 'viewer', doc: { fileId: meta.id, name: meta.name, bytes: out } });
    toast(meta.id === session.fileId ? 'Modifications enregistrées' : `Enregistré : ${meta.name}`);
  } catch (e) {
    console.error(e);
    session.set({ saving: false });
    toast('Échec de l\'enregistrement du PDF');
  }
}

export async function addSignature(sig) {
  await store.saveSignature(sig);
  await refreshSignatures();
}

export async function removeSignature(sig) {
  await store.deleteSignature(sig.id);
  await refreshSignatures();
}

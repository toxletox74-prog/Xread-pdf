// Stockage local (IndexedDB) : métadonnées et contenu des PDF séparés, pour lister vite.
const DB = 'xreadpdf';
let dbp = null;

function db() {
  if (!dbp) {
    dbp = new Promise((resolve, reject) => {
      const r = indexedDB.open(DB, 1);
      r.onupgradeneeded = () => {
        const d = r.result;
        d.createObjectStore('meta', { keyPath: 'id' });
        d.createObjectStore('data', { keyPath: 'id' });
        d.createObjectStore('thumbs', { keyPath: 'id' });
        d.createObjectStore('signatures', { keyPath: 'id' });
      };
      r.onsuccess = () => resolve(r.result);
      r.onerror = () => reject(r.error);
    });
  }
  return dbp;
}

function tx(stores, mode, fn) {
  return db().then((d) => new Promise((resolve, reject) => {
    const t = d.transaction(stores, mode);
    let result;
    Promise.resolve(fn(t)).then((r) => { result = r; });
    t.oncomplete = () => resolve(result);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  }));
}

const req = (r) => new Promise((resolve, reject) => { r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });

export const newId = () => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));

export async function listFiles() {
  const all = await tx(['meta'], 'readonly', (t) => req(t.objectStore('meta').getAll()));
  return all.sort((a, b) => b.modified - a.modified);
}

export async function getBytes(id) {
  const rec = await tx(['data'], 'readonly', (t) => req(t.objectStore('data').get(id)));
  return rec ? new Uint8Array(rec.bytes) : null;
}

/** Enregistre (ou remplace si [id] existe) un PDF. */
export async function saveFile({ id = newId(), name, bytes }) {
  const buf = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
  const meta = { id, name, modified: Date.now(), size: bytes.byteLength };
  await tx(['meta', 'data', 'thumbs'], 'readwrite', (t) => {
    t.objectStore('meta').put(meta);
    t.objectStore('data').put({ id, bytes: buf });
    t.objectStore('thumbs').delete(id);
  });
  return meta;
}

export async function renameFile(id, name) {
  return tx(['meta'], 'readwrite', (t) => {
    const s = t.objectStore('meta');
    const r = s.get(id);
    // Écriture dans le rappel même : la transaction reste active (Safari)
    r.onsuccess = () => { if (r.result) s.put({ ...r.result, name }); };
  });
}

export async function deleteFile(id) {
  return tx(['meta', 'data', 'thumbs'], 'readwrite', (t) => {
    t.objectStore('meta').delete(id);
    t.objectStore('data').delete(id);
    t.objectStore('thumbs').delete(id);
  });
}

export async function getThumb(id, modified) {
  const rec = await tx(['thumbs'], 'readonly', (t) => req(t.objectStore('thumbs').get(id)));
  return rec && rec.modified === modified ? rec.blob : null;
}

export async function putThumb(id, modified, blob) {
  return tx(['thumbs'], 'readwrite', (t) => { t.objectStore('thumbs').put({ id, modified, blob }); });
}

export async function listSignatures() {
  const all = await tx(['signatures'], 'readonly', (t) => req(t.objectStore('signatures').getAll()));
  return all.sort((a, b) => b.created - a.created);
}

export async function saveSignature(sig) {
  return tx(['signatures'], 'readwrite', (t) => { t.objectStore('signatures').put({ ...sig, created: sig.created || Date.now() }); });
}

export async function deleteSignature(id) {
  return tx(['signatures'], 'readwrite', (t) => { t.objectStore('signatures').delete(id); });
}

/** Demande au navigateur de ne pas effacer les données (iOS : app installée). */
export function askPersistence() {
  navigator.storage?.persist?.().catch(() => {});
}

/** Nom unique parmi les fichiers existants. */
export function uniqueName(base, files) {
  const names = new Set(files.map((f) => f.name));
  let n = base, i = 2;
  while (names.has(n)) n = `${base} (${i++})`;
  return n;
}

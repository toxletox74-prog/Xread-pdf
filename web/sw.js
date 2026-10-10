// Service worker : l'app fonctionne hors ligne (tout est mis en cache à l'installation).
const VERSION = '__BUILD__';
const CACHE = `xreadpdf-${VERSION}`;
const FILES = [
  './', 'index.html', 'manifest.webmanifest',
  'src/styles.css', 'src/app.js', 'src/ui.js', 'src/icons.js', 'src/store.js', 'src/pdfview.js', 'src/model.js',
  'src/session.js', 'src/exporter.js', 'src/actions.js', 'src/library.js', 'src/viewer.js', 'src/editor.js',
  'src/signatures.js', 'src/scanner.js',
  'vendor/preact.module.js', 'vendor/hooks.module.js', 'vendor/htm.module.js',
  'vendor/pdf.min.js', 'vendor/pdf.worker.min.js', 'vendor/pdf-lib.esm.min.js',
  'icons/mark.png', 'icons/apple-touch-icon.png', 'icons/icon-192.png', 'icons/icon-512.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('xreadpdf-') && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Cache d'abord (rapide, hors ligne) ; la nouvelle version s'installe en arrière-plan
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    caches.match(req, { ignoreSearch: true }).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
      return res;
    }))
  );
});

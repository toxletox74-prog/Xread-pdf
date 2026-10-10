// Vérifie l'export pdf-lib : mêmes fichiers d'entrée et mêmes contrôles que la version iOS
// (ios/Tests/export_check.py). Usage : node test/export-check.mjs <dossier>
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { exportPdf } from '../src/exporter.js';

const dir = process.argv[2];
const sizes = { 0: [300, 500], 90: [500, 300], 180: [300, 500], 270: [500, 300] };
for (const r0 of [0, 90, 180, 270]) {
  const bytes = readFileSync(join(dir, `in${r0}.pdf`));
  const [w, h] = sizes[r0];
  for (const e of [0, 90]) {
    const annots = [
      { type: 'ink', id: 1, points: [{ x: 100, y: 60 }, { x: 180, y: 60 }], color: 0x1f4fd8, width: 10, highlighter: false },
      { type: 'text', id: 2, text: 'Bonjour é', x: 100, y: 150, size: 20, color: 0x188038, rotation: e },
    ];
    const out = await exportPdf(new Uint8Array(bytes), [{ src: 0, width: w, height: h, rotation: e, annots }]);
    writeFileSync(join(dir, `out${r0}_${e}.pdf`), out);
  }
}
// Ordre / suppression : 3 pages -> [2, 0]
const { PDFDocument } = await import('../vendor/pdf-lib.esm.min.js');
const d = await PDFDocument.create();
for (const s of [[100, 100], [200, 200], [300, 300]]) d.addPage(s);
const out = await exportPdf(await d.save(), [{ src: 2, width: 300, height: 300, rotation: 0, annots: [] }, { src: 0, width: 100, height: 100, rotation: 90, annots: [] }]);
const back = await PDFDocument.load(out);
const res = back.getPages().map((p) => `${p.getWidth()}x${p.getHeight()} rot${p.getRotation().angle}`);
console.log('ordre:', res.join(', '), res.join() === '300x300 rot0,100x100 rot90' ? 'OK' : 'KO');

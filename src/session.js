// État d'une séance d'édition (portage de EditorSession Android / iOS).
import { rot, sub, isPlaced, hit, PEN_COLORS, HIGHLIGHT_COLORS, PEN_WIDTHS, TEXT_SIZES } from './model.js';

export const TOOLS = [
  { id: 'select', label: 'Sélection', icon: 'pointer' },
  { id: 'pen', label: 'Stylo', icon: 'pencil' },
  { id: 'highlighter', label: 'Surligner', icon: 'highlighter' },
  { id: 'text', label: 'Texte', icon: 'type' },
  { id: 'signature', label: 'Signature', icon: 'signature' },
  { id: 'eraser', label: 'Gomme', icon: 'eraser' },
];

export class EditorSession {
  constructor({ fileId, name, bytes, doc, sizes, tool }) {
    Object.assign(this, { fileId, name, bytes, doc });
    this.pages = sizes.map((s, i) => ({ src: i, width: s.width, height: s.height, rotation: 0, annots: [] }));
    this.undoStack = [];
    this.redoStack = [];
    this.dirty = false;
    this.current = 0;
    this._tool = tool;
    this.penColor = PEN_COLORS[0];
    this.penWidth = PEN_WIDTHS[1];
    this.highlightColor = HIGHLIGHT_COLORS[0];
    this.textColor = PEN_COLORS[0];
    this.textSize = TEXT_SIZES[1];
    this.signature = null;
    this.selected = null;
    this.textDialog = null;
    this.padOpen = false;
    this.saving = false;
    this.nextId = 1;
    this.gestureStart = null;
    this.listeners = new Set();
  }

  subscribe(l) { this.listeners.add(l); return () => this.listeners.delete(l); }
  emit() { this.listeners.forEach((l) => l()); }
  set(patch) { Object.assign(this, patch); this.emit(); }

  get tool() { return this._tool; }
  set tool(t) { this._tool = t; if (t !== 'select') this.selected = null; }
  setTool(t) { this.tool = t; this.emit(); }

  newId() { return this.nextId++; }
  get page() { return this.pages[Math.min(Math.max(this.current, 0), this.pages.length - 1)]; }
  get canUndo() { return this.undoStack.length > 0; }
  get canRedo() { return this.redoStack.length > 0; }

  // ---- Historique ----
  commit(pages) {
    if (pages === this.pages) return;
    const old = this.pages;
    this.pages = pages;
    this.push(old);
    this.emit();
  }
  push(old) {
    this.undoStack.push(old);
    if (this.undoStack.length > 100) this.undoStack.shift();
    this.redoStack = [];
    this.dirty = true;
    this.sync();
  }
  undo() {
    const prev = this.undoStack.pop();
    if (!prev) return;
    this.redoStack.push(this.pages);
    this.pages = prev;
    this.sync();
    this.emit();
  }
  redo() {
    const next = this.redoStack.pop();
    if (!next) return;
    this.undoStack.push(this.pages);
    this.pages = next;
    this.sync();
    this.emit();
  }
  sync() {
    this.current = Math.min(Math.max(this.current, 0), this.pages.length - 1);
    if (this.selected != null && !this.page.annots.some((a) => a.id === this.selected)) this.selected = null;
  }

  beginGesture() { this.gestureStart = this.pages; }
  live(pages) { this.pages = pages; this.emit(); }
  endGesture() {
    const start = this.gestureStart;
    this.gestureStart = null;
    if (start && start !== this.pages) { this.push(start); this.emit(); }
  }
  cancelGesture() {
    if (this.gestureStart) this.pages = this.gestureStart;
    this.gestureStart = null;
    this.emit();
  }

  // ---- Annotations de la page courante ----
  mapPage(index, f) {
    return this.pages.map((p, i) => (i === index ? f(p) : p));
  }
  mapAnnots(f) { return this.mapPage(this.current, (p) => ({ ...p, annots: f(p.annots) })); }
  replacing(a) { return this.mapAnnots((l) => l.map((x) => (x.id === a.id ? a : x))); }
  annot(id) { return id == null ? null : this.page.annots.find((a) => a.id === id) || null; }

  deleteSelected() {
    const id = this.selected;
    if (id == null) return;
    this.selected = null;
    this.commit(this.mapAnnots((l) => l.filter((a) => a.id !== id)));
  }

  hitPlaced(p, tol) { return [...this.page.annots].reverse().find((a) => isPlaced(a) && hit(a, p, tol)) || null; }
  hitAny(p, tol) { return new Set(this.page.annots.filter((a) => hit(a, p, tol)).map((a) => a.id)); }

  // ---- Texte ----
  openNewText(at) { this.set({ textDialog: { editId: null, at, initial: '' } }); }
  openEditText(a) { this.set({ textDialog: { editId: a.id, at: { x: a.x, y: a.y }, initial: a.text } }); }
  confirmText(text) {
    const d = this.textDialog;
    if (!d) return;
    this.textDialog = null;
    const clean = text.replace(/\s+$/, '');
    const existing = this.annot(d.editId);
    if (existing && existing.type === 'text') {
      if (!clean.trim()) {
        this.selected = null;
        this.commit(this.mapAnnots((l) => l.filter((a) => a.id !== existing.id)));
      } else {
        this.commit(this.replacing({ ...existing, text: clean }));
      }
    } else if (clean.trim()) {
      const a = { type: 'text', id: this.newId(), text: clean, x: d.at.x, y: d.at.y, size: this.textSize, color: this.textColor, rotation: this.page.rotation };
      this.tool = 'select';
      this.selected = a.id;
      this.commit(this.mapAnnots((l) => [...l, a]));
    } else {
      this.emit();
    }
  }

  // ---- Signature ----
  placeSignature(at, sig) {
    const p = this.page;
    const w = Math.min(p.width, p.height) * 0.35;
    const h = w / sig.aspect;
    const anchor = sub(at, rot({ x: w / 2, y: h / 2 }, -p.rotation));
    const a = { type: 'sign', id: this.newId(), sig, x: anchor.x, y: anchor.y, width: w, rotation: p.rotation };
    this.tool = 'select';
    this.selected = a.id;
    this.commit(this.mapAnnots((l) => [...l, a]));
  }

  // ---- Pages ----
  goTo(i) { this.current = Math.min(Math.max(i, 0), this.pages.length - 1); this.selected = null; this.emit(); }
  rotatePage(i) { this.commit(this.mapPage(i, (p) => ({ ...p, rotation: (p.rotation + 90) % 360 }))); }
  deletePage(i) {
    if (this.pages.length <= 1) return false;
    this.selected = null;
    this.commit(this.pages.filter((_, j) => j !== i));
    this.current = Math.min(this.current, this.pages.length - 1);
    this.emit();
    return true;
  }
  movePage(i, delta) {
    const j = i + delta;
    if (j < 0 || j >= this.pages.length) return;
    const copy = [...this.pages];
    [copy[i], copy[j]] = [copy[j], copy[i]];
    if (this.current === i) this.current = j; else if (this.current === j) this.current = i;
    this.commit(copy);
  }
}

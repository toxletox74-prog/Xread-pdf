import Foundation
import PDFKit
import SwiftUI

enum Tool: String, CaseIterable, Identifiable {
    case select, pen, highlighter, text, signature, eraser
    var id: String { rawValue }

    var label: String {
        switch self {
        case .select: return "Sélection"
        case .pen: return "Stylo"
        case .highlighter: return "Surligner"
        case .text: return "Texte"
        case .signature: return "Signature"
        case .eraser: return "Gomme"
        }
    }

    var icon: String {
        switch self {
        case .select: return "hand.point.up.left"
        case .pen: return "pencil.tip"
        case .highlighter: return "highlighter"
        case .text: return "textformat"
        case .signature: return "signature"
        case .eraser: return "eraser"
        }
    }
}

/// Saisie de texte : création (editId nil) ou modification.
struct TextDialog: Identifiable, Equatable {
    let id = UUID()
    var editId: Int?
    var at: Pt
    var initial: String
}

/// État d'une séance d'édition. L'historique stocke des instantanés de la liste de pages.
@MainActor
final class EditorSession: ObservableObject {
    let url: URL
    let document: PDFDocument

    @Published private(set) var pages: [EditPage]
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    @Published private(set) var dirty = false
    @Published private(set) var current = 0

    @Published var tool: Tool { didSet { if tool != .select { selected = nil } } }
    @Published var penColor = PEN_COLORS[0]
    @Published var penWidth = PEN_WIDTHS[1]
    @Published var highlightColor = HIGHLIGHT_COLORS[0]
    @Published var textColor = PEN_COLORS[0]
    @Published var textSize = TEXT_SIZES[1]
    @Published var signature: Signature?

    @Published var selected: Int?
    @Published var textDialog: TextDialog?
    @Published var padOpen = false
    @Published var saving = false

    private var undoStack: [[EditPage]] = []
    private var redoStack: [[EditPage]] = []
    private var gestureStart: [EditPage]?
    private var nextId = 1

    init(url: URL, document: PDFDocument, tool: Tool) {
        self.url = url
        self.document = document
        let list: [EditPage] = (0..<document.pageCount).compactMap { i in
            guard let p = document.page(at: i) else { return nil }
            let s = PdfExporter.intrinsicSize(p)
            return EditPage(src: i, width: s.width, height: s.height)
        }
        _tool = Published(initialValue: tool)
        _pages = Published(initialValue: list)
    }

    func newId() -> Int { defer { nextId += 1 }; return nextId }

    var page: EditPage { pages[min(max(current, 0), pages.count - 1)] }

    // MARK: Historique

    func commit(_ new: [EditPage]) {
        guard new != pages else { return }
        push(pages)
        pages = new
    }

    private func push(_ old: [EditPage]) {
        undoStack.append(old)
        if undoStack.count > 100 { undoStack.removeFirst() }
        redoStack.removeAll()
        dirty = true
        sync()
    }

    func undo() {
        guard let prev = undoStack.popLast() else { return }
        redoStack.append(pages)
        pages = prev
        sync()
    }

    func redo() {
        guard let next = redoStack.popLast() else { return }
        undoStack.append(pages)
        pages = next
        sync()
    }

    private func sync() {
        canUndo = !undoStack.isEmpty
        canRedo = !redoStack.isEmpty
        current = min(max(current, 0), pages.count - 1)
        if let s = selected, !page.annots.contains(where: { $0.id == s }) { selected = nil }
    }

    /// Geste continu : modifications sans historique, validées en une étape à la fin.
    func beginGesture() { gestureStart = pages }
    func live(_ new: [EditPage]) { pages = new }
    func endGesture() {
        guard let start = gestureStart else { return }
        gestureStart = nil
        if start != pages { push(start) }
    }
    func cancelGesture() {
        if let start = gestureStart { pages = start }
        gestureStart = nil
    }

    // MARK: Annotations de la page courante

    func mapPage(_ index: Int? = nil, _ f: (inout EditPage) -> Void) -> [EditPage] {
        var copy = pages
        f(&copy[index ?? current])
        return copy
    }

    func mapAnnots(_ f: (inout [Annot]) -> Void) -> [EditPage] { mapPage { f(&$0.annots) } }

    func replacing(_ a: Annot) -> [EditPage] {
        mapAnnots { list in
            if let i = list.firstIndex(where: { $0.id == a.id }) { list[i] = a }
        }
    }

    func annot(_ id: Int?) -> Annot? {
        guard let id else { return nil }
        return page.annots.first { $0.id == id }
    }

    func deleteSelected() {
        guard let id = selected else { return }
        commit(mapAnnots { $0.removeAll { $0.id == id } })
        selected = nil
    }

    func hitPlaced(_ p: Pt, tol: CGFloat) -> Annot? {
        page.annots.reversed().first { $0.isPlaced && $0.hit(p, tol: tol) }
    }

    func hitAny(_ p: Pt, tol: CGFloat) -> Set<Int> {
        Set(page.annots.filter { $0.hit(p, tol: tol) }.map(\.id))
    }

    // MARK: Texte

    func openNewText(_ at: Pt) { textDialog = TextDialog(editId: nil, at: at, initial: "") }

    func openEditText(_ a: TextAnnot) { textDialog = TextDialog(editId: a.id, at: Pt(x: a.x, y: a.y), initial: a.text) }

    func confirmText(_ text: String) {
        guard let d = textDialog else { return }
        textDialog = nil
        let clean = text.replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression)
        if let id = d.editId, case .text(var existing)? = annot(id) {
            if clean.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                commit(mapAnnots { $0.removeAll { $0.id == id } })
                selected = nil
            } else {
                existing.text = clean
                commit(replacing(.text(existing)))
            }
        } else if !clean.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let a = TextAnnot(id: newId(), text: clean, x: d.at.x, y: d.at.y, size: textSize, color: textColor, rotation: page.rotation)
            commit(mapAnnots { $0.append(.text(a)) })
            tool = .select
            selected = a.id
        }
    }

    // MARK: Signature

    /// Pose la signature centrée sur [at], largeur ~35 % du petit côté de la page.
    func placeSignature(at: Pt, _ sig: Signature) {
        let p = page
        let w = min(p.width, p.height) * 0.35
        let h = w / sig.aspect
        let anchor = at - Pt(x: w / 2, y: h / 2).rotated(-p.rotation)
        let a = SignAnnot(id: newId(), sig: sig, x: anchor.x, y: anchor.y, width: w, rotation: p.rotation)
        commit(mapAnnots { $0.append(.sign(a)) })
        tool = .select
        selected = a.id
    }

    // MARK: Pages

    func goTo(_ index: Int) {
        current = min(max(index, 0), pages.count - 1)
        selected = nil
    }

    func rotatePage(_ i: Int) { commit(mapPage(i) { $0.rotation = ($0.rotation + 90) % 360 }) }

    @discardableResult
    func deletePage(_ i: Int) -> Bool {
        guard pages.count > 1 else { return false }
        var copy = pages
        copy.remove(at: i)
        commit(copy)
        current = min(current, pages.count - 1)
        selected = nil
        return true
    }

    func movePage(_ i: Int, by delta: Int) {
        let j = i + delta
        guard pages.indices.contains(j) else { return }
        var copy = pages
        copy.swapAt(i, j)
        commit(copy)
        if current == i { current = j } else if current == j { current = i }
    }
}

/// Redimensionne depuis la poignée (coin bas-droit), proportions conservées.
func resized(_ a: Annot, box: CGSize, to p: Pt) -> Annot {
    let l = a.toLocal(p)
    let f = max(max(l.x / max(box.width, 1), l.y / max(box.height, 1)), 0.05)
    switch a {
    case .sign(var s): s.width = min(max(s.width * f, 16), 2000); return .sign(s)
    case .text(var t): t.size = min(max(t.size * f, 4), 160); return .text(t)
    case .ink: return a
    }
}

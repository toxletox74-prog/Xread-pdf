import Foundation
import PDFKit
import UIKit

/// Un document de la bibliothèque.
struct PdfFile: Identifiable, Equatable, Hashable {
    let url: URL
    let modified: Date
    let size: Int64
    var id: String { url.path + "|\(modified.timeIntervalSince1970)" }
    var name: String { url.deletingPathExtension().lastPathComponent }
}

/// Stockage :
///  - Documents/ : la bibliothèque « Mes PDF » (visible dans l'app Fichiers, « Sur mon iPhone »)
///  - tmp/open : copie d'un PDF ouvert depuis une autre app
@MainActor
final class Library: ObservableObject {
    @Published private(set) var files: [PdfFile] = []

    let dir: URL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    private let openDir = FileManager.default.temporaryDirectory.appendingPathComponent("open", isDirectory: true)

    init() { refresh() }

    func refresh() {
        let fm = FileManager.default
        let urls = (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey])) ?? []
        files = urls
            .filter { $0.pathExtension.lowercased() == "pdf" }
            .map { u in
                let v = try? u.resourceValues(forKeys: [.contentModificationDateKey, .fileSizeKey])
                return PdfFile(url: u, modified: v?.contentModificationDate ?? .distantPast, size: Int64(v?.fileSize ?? 0))
            }
            .sorted { $0.modified > $1.modified }
    }

    func isInLibrary(_ url: URL) -> Bool {
        url.deletingLastPathComponent().standardizedFileURL.path == dir.standardizedFileURL.path
    }

    /// Copie un PDF externe (Fichiers, « Ouvrir avec ») dans un dossier temporaire.
    func openExternal(_ src: URL) throws -> URL {
        let scoped = src.startAccessingSecurityScopedResource()
        defer { if scoped { src.stopAccessingSecurityScopedResource() } }
        let fm = FileManager.default
        try? fm.removeItem(at: openDir)
        try fm.createDirectory(at: openDir, withIntermediateDirectories: true)
        let dest = openDir.appendingPathComponent(sanitize(src.deletingPathExtension().lastPathComponent) + ".pdf")
        try fm.copyItem(at: src, to: dest)
        // Les fichiers reçus via « Ouvrir avec » arrivent dans Documents/Inbox : on nettoie
        if src.path.contains("/Documents/Inbox/") { try? fm.removeItem(at: src) }
        return dest
    }

    func saveToLibrary(_ url: URL) throws -> URL {
        if isInLibrary(url) { return url }
        let dest = uniqueURL(url.deletingPathExtension().lastPathComponent)
        try FileManager.default.copyItem(at: url, to: dest)
        refresh()
        return dest
    }

    /// Scan : une page par image, JPEG compressé, largeur A4 (595 pt).
    func saveScan(_ images: [UIImage]) throws -> URL {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH-mm-ss"
        let dest = uniqueURL("Scan " + f.string(from: Date()))
        let first = images.first?.size ?? CGSize(width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: first))
        let data = renderer.pdfData { ctx in
            for img in images {
                let w: CGFloat = 595
                let h = w * img.size.height / max(img.size.width, 1)
                let rect = CGRect(x: 0, y: 0, width: w, height: h)
                ctx.beginPage(withBounds: rect, pageInfo: [:])
                let jpeg = img.jpegData(compressionQuality: 0.7).flatMap(UIImage.init(data:)) ?? img
                jpeg.draw(in: rect)
            }
        }
        try data.write(to: dest)
        refresh()
        return dest
    }

    /// Enregistre une version modifiée : [write] produit le PDF dans un fichier temporaire, qui
    /// remplace l'original (s'il est dans la bibliothèque et que asCopy est faux) ou devient une copie.
    func saveEdited(source: URL, asCopy: Bool, write: (URL) throws -> Void) throws -> URL {
        let fm = FileManager.default
        let tmp = fm.temporaryDirectory.appendingPathComponent("edit-\(UUID().uuidString).pdf")
        defer { try? fm.removeItem(at: tmp) }
        try write(tmp)
        let inLib = isInLibrary(source)
        let dest: URL
        if inLib && !asCopy {
            dest = source
            _ = try fm.replaceItemAt(source, withItemAt: tmp)
        } else {
            let base = source.deletingPathExtension().lastPathComponent
            dest = uniqueURL(inLib ? base + " - modifié" : base)
            try fm.moveItem(at: tmp, to: dest)
        }
        refresh()
        return dest
    }

    /// Renvoie false si le nom est déjà pris.
    func rename(_ file: PdfFile, to name: String) -> Bool {
        let dest = dir.appendingPathComponent(sanitize(name) + ".pdf")
        if dest.path == file.url.path { return true }
        if FileManager.default.fileExists(atPath: dest.path) { return false }
        do { try FileManager.default.moveItem(at: file.url, to: dest) } catch { return false }
        refresh()
        return true
    }

    func delete(_ file: PdfFile) {
        try? FileManager.default.removeItem(at: file.url)
        refresh()
    }

    private func uniqueURL(_ base: String) -> URL {
        let clean = sanitize(base)
        var candidate = dir.appendingPathComponent(clean + ".pdf")
        var n = 2
        while FileManager.default.fileExists(atPath: candidate.path) {
            candidate = dir.appendingPathComponent("\(clean) (\(n)).pdf")
            n += 1
        }
        return candidate
    }

    private func sanitize(_ name: String) -> String {
        var s = name
        if s.lowercased().hasSuffix(".pdf") { s = String(s.dropLast(4)) }
        let bad = CharacterSet(charactersIn: "\\/:*?\"<>|\n\r")
        s = s.components(separatedBy: bad).joined(separator: "_").trimmingCharacters(in: .whitespaces)
        s = String(s.prefix(100))
        return s.isEmpty ? "Document" : s
    }
}

/// Miniatures de première page, en cache mémoire.
enum Thumbnails {
    private static let cache = NSCache<NSString, UIImage>()

    static func cached(_ file: PdfFile) -> UIImage? { cache.object(forKey: file.id as NSString) }

    static func load(_ file: PdfFile, width: CGFloat) async -> UIImage? {
        if let img = cached(file) { return img }
        let img = await Task.detached(priority: .utility) { () -> UIImage? in
            guard let page = PDFDocument(url: file.url)?.page(at: 0) else { return nil }
            let b = page.bounds(for: .cropBox).size
            let rotated = normDeg(page.rotation) % 180 != 0
            let ratio = rotated ? b.width / max(b.height, 1) : b.height / max(b.width, 1)
            return page.thumbnail(of: CGSize(width: width, height: width * ratio), for: .cropBox)
        }.value
        if let img { cache.setObject(img, forKey: file.id as NSString) }
        return img
    }
}

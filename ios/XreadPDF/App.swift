import PDFKit
import SwiftUI

@main
struct XreadPDFApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(model.library)
                .environmentObject(model.signatures)
                .tint(Brand.primary)
                .onOpenURL { model.openExternal($0) }
        }
    }
}

enum Screen: Equatable {
    case library
    case viewer(URL)
    case editor(EditorSession, returnTo: URL?)
    case signatures

    static func == (a: Screen, b: Screen) -> Bool {
        switch (a, b) {
        case (.library, .library), (.signatures, .signatures): return true
        case let (.viewer(x), .viewer(y)): return x == y
        case let (.editor(x, _), .editor(y, _)): return x === y
        default: return false
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    let library = Library()
    let signatures = SignatureStore()
    @Published var screen: Screen = .library
    @Published var toast: String?

    init() {
        Task { @MainActor in DebugSeed.apply(self) }
    }

    func message(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toast == text { toast = nil }
        }
    }

    func open(_ url: URL) { screen = .viewer(url) }
    func back() { screen = .library }

    func openExternal(_ url: URL) {
        do { screen = .viewer(try library.openExternal(url)) } catch { message("Impossible d'ouvrir ce fichier") }
    }

    func saveToLibrary(_ url: URL) {
        do {
            let saved = try library.saveToLibrary(url)
            screen = .viewer(saved)
            message("Ajouté à Mes PDF")
        } catch { message("Échec de l'enregistrement") }
    }

    func scanned(_ images: [UIImage]) {
        guard !images.isEmpty else { return }
        do {
            let url = try library.saveScan(images)
            screen = .viewer(url)
            message("Scan enregistré")
        } catch { message("Échec de l'enregistrement du scan") }
    }

    func edit(_ url: URL, tool: Tool, returnTo: URL?) {
        guard let doc = PDFDocument(url: url) else { message("Impossible de modifier ce PDF"); return }
        if doc.isLocked { message("Ce PDF est protégé par mot de passe"); return }
        guard doc.pageCount > 0 else { message("Ce PDF ne contient aucune page"); return }
        let session = EditorSession(url: url, document: doc, tool: tool)
        session.signature = signatures.signatures.first
        if tool == .signature && session.signature == nil { session.padOpen = true }
        screen = .editor(session, returnTo: returnTo)
    }

    func closeEditor() {
        guard case .editor(_, let returnTo) = screen else { return }
        screen = returnTo.map { .viewer($0) } ?? .library
    }

    func saveEdits(asCopy: Bool) {
        guard case .editor(let session, _) = screen, !session.saving else { return }
        session.saving = true
        let pages = session.pages
        let doc = session.document
        let source = session.url
        Task {
            do {
                // Export hors du fil principal (l'indicateur reste animé)
                let tmp = try await Task.detached(priority: .userInitiated) { () throws -> URL in
                    let t = FileManager.default.temporaryDirectory.appendingPathComponent("edit-\(UUID().uuidString).pdf")
                    try PdfExporter.export(document: doc, pages: pages, to: t)
                    return t
                }.value
                let saved = try library.placeEdited(tmp, source: source, asCopy: asCopy)
                screen = .viewer(saved)
                message(saved == source ? "Modifications enregistrées" : "Enregistré : \(saved.deletingPathExtension().lastPathComponent)")
            } catch {
                session.saving = false
                message("Échec de l'enregistrement du PDF")
            }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var library: Library
    @EnvironmentObject var signatures: SignatureStore

    var body: some View {
        ZStack {
            switch model.screen {
            case .library:
                LibraryView()
                    .transition(.opacity)
            case .viewer(let url):
                ViewerView(url: url, inLibrary: library.isInLibrary(url))
                    .id(url.path + "\(library.files.first(where: { $0.url == url })?.modified.timeIntervalSince1970 ?? 0)")
                    .transition(.opacity)
            case .editor(let session, _):
                EditorView(
                    session: session,
                    signatures: signatures.signatures,
                    inLibrary: library.isInLibrary(session.url),
                    onClose: model.closeEditor,
                    onSave: { model.saveEdits(asCopy: $0) },
                    onNewSignature: { signatures.add($0) }
                )
                .transition(.opacity)
            case .signatures:
                SignaturesView(store: signatures, onBack: model.back)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.screen)
        .overlay(alignment: .bottom) {
            if let t = model.toast {
                Text(t)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Brand.cream)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .background(Capsule().fill(Brand.night))
                    .padding(.bottom, 110)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.9), value: model.toast)
    }
}

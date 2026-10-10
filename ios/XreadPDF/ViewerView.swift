import PDFKit
import SwiftUI
import VisionKit

struct ViewerView: View {
    let url: URL
    let inLibrary: Bool
    @EnvironmentObject var model: AppModel
    @State private var document: PDFDocument?
    @State private var failed = false
    @State private var currentPage = 1

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                Button(action: model.back) {
                    Image(systemName: "chevron.left").font(.title3.weight(.semibold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Retour")
                VStack(alignment: .leading, spacing: 0) {
                    Text(url.deletingPathExtension().lastPathComponent).font(.headline).lineLimit(1)
                    if let d = document {
                        Text(d.pageCount > 1 ? "\(d.pageCount) pages" : "1 page")
                            .font(.caption).foregroundStyle(Brand.secondaryText)
                    }
                }
                Spacer()
                if !inLibrary {
                    Button { model.saveToLibrary(url) } label: {
                        Image(systemName: "square.and.arrow.down").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Enregistrer dans Mes PDF")
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)

            ZStack(alignment: .bottom) {
                Brand.surfaceHigh.ignoresSafeArea()
                if let document {
                    PDFKitView(document: document, currentPage: $currentPage)
                        .ignoresSafeArea(edges: .bottom)
                    Text("\(currentPage) / \(document.pageCount)")
                        .font(.footnote.weight(.semibold)).monospacedDigit()
                        .foregroundStyle(Brand.cream)
                        .padding(.horizontal, 14).padding(.vertical, 6)
                        .background(Capsule().fill(Brand.night.opacity(0.85)))
                        .frame(maxHeight: .infinity, alignment: .top)
                        .padding(.top, 10)
                    ActionBar(url: url)
                        .padding(.bottom, 20)
                } else if failed {
                    Text("Impossible d'ouvrir ce PDF (fichier protégé par mot de passe ou endommagé).")
                        .multilineTextAlignment(.center).padding(24)
                        .frame(maxHeight: .infinity)
                } else {
                    ProgressView().frame(maxHeight: .infinity)
                }
            }
        }
        .background(Brand.background)
        .task(id: url) {
            let doc = PDFDocument(url: url)
            if let doc, !doc.isLocked { document = doc } else { failed = true }
        }
    }
}

private struct ActionBar: View {
    let url: URL
    @EnvironmentObject var model: AppModel

    var body: some View {
        HStack(spacing: 4) {
            item("Modifier", "pencil", accent: false) { model.edit(url, tool: .select, returnTo: url) }
            item("Signer", "signature", accent: true) { model.edit(url, tool: .signature, returnTo: url) }
            ShareLink(item: url) {
                label("Partager", "square.and.arrow.up", accent: false)
            }
        }
        .padding(6)
        .background(Capsule().fill(Brand.night).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
    }

    private func item(_ title: String, _ icon: String, accent: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { label(title, icon, accent: accent) }.buttonStyle(.plain)
    }

    private func label(_ title: String, _ icon: String, accent: Bool) -> some View {
        Label(title, systemImage: icon)
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(Capsule().fill(accent ? Brand.coral : Color.clear))
            .foregroundStyle(accent ? Brand.ink : Brand.cream)
    }
}

/// PDFView en défilement vertical continu, avec suivi de la page courante.
struct PDFKitView: UIViewRepresentable {
    let document: PDFDocument
    @Binding var currentPage: Int

    func makeUIView(context: Context) -> PDFView {
        let v = PDFView()
        v.document = document
        v.autoScales = true
        v.displayMode = .singlePageContinuous
        v.displayDirection = .vertical
        v.pageShadowsEnabled = true
        v.backgroundColor = .clear
        v.pageBreakMargins = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        context.coordinator.observe(v)
        return v
    }

    func updateUIView(_ v: PDFView, context: Context) {
        if v.document !== document { v.document = document }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject {
        let parent: PDFKitView
        init(_ p: PDFKitView) { parent = p }

        func observe(_ v: PDFView) {
            NotificationCenter.default.addObserver(self, selector: #selector(changed(_:)), name: .PDFViewPageChanged, object: v)
        }

        @objc func changed(_ n: Notification) {
            guard let v = n.object as? PDFView, let page = v.currentPage, let doc = v.document else { return }
            let i = doc.index(for: page) + 1
            DispatchQueue.main.async { self.parent.currentPage = i }
        }
    }
}

/// Scanner de documents Apple (détection des bords, recadrage, multi-pages).
struct DocumentScanner: UIViewControllerRepresentable {
    var onFinish: ([UIImage]) -> Void
    var onCancel: () -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let vc = VNDocumentCameraViewController()
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let parent: DocumentScanner
        init(_ p: DocumentScanner) { parent = p }

        func documentCameraViewController(_ c: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            parent.onFinish((0..<scan.pageCount).map { scan.imageOfPage(at: $0) })
        }

        func documentCameraViewControllerDidCancel(_ c: VNDocumentCameraViewController) { parent.onCancel() }

        func documentCameraViewController(_ c: VNDocumentCameraViewController, didFailWithError error: Error) { parent.onCancel() }
    }
}

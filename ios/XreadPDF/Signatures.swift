import Foundation
import SwiftUI

/// Signatures enregistrées : un fichier JSON par signature (même format que la version Android),
/// dans Application Support (non visible dans l'app Fichiers).
@MainActor
final class SignatureStore: ObservableObject {
    @Published private(set) var signatures: [Signature] = []

    private let dir: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let d = base.appendingPathComponent("signatures", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()

    init() { load() }

    private func load() {
        let urls = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        signatures = urls
            .filter { $0.pathExtension == "json" }
            .sorted {
                let a = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let b = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return a > b
            }
            .compactMap { u in (try? Data(contentsOf: u)).flatMap { Self.parse(id: u.deletingPathExtension().lastPathComponent, $0) } }
    }

    func add(_ sig: Signature) {
        let json: [String: Any] = [
            "aspect": Double(sig.aspect),
            "color": Int(sig.color),
            "width": Double(sig.width),
            "strokes": sig.strokes.map { s in s.flatMap { [round4($0.x), round4($0.y)] } },
        ]
        if let data = try? JSONSerialization.data(withJSONObject: json) {
            try? data.write(to: dir.appendingPathComponent(sig.id + ".json"))
        }
        signatures.insert(sig, at: 0)
    }

    func delete(_ sig: Signature) {
        try? FileManager.default.removeItem(at: dir.appendingPathComponent(sig.id + ".json"))
        signatures.removeAll { $0.id == sig.id }
    }

    private func round4(_ v: CGFloat) -> Double { (Double(v) * 10000).rounded() / 10000 }

    private static func parse(id: String, _ data: Data) -> Signature? {
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let strokes = o["strokes"] as? [[Double]],
              let aspect = o["aspect"] as? Double,
              let color = o["color"] as? Int,
              let width = o["width"] as? Double else { return nil }
        let pts = strokes.map { s in stride(from: 0, to: s.count - 1, by: 2).map { Pt(x: s[$0], y: s[$0 + 1]) } }
        return Signature(id: id, strokes: pts, aspect: min(max(aspect, 0.2), 20), color: UInt32(truncatingIfNeeded: color), width: width)
    }
}

/// Aperçu d'une signature, centré dans son cadre.
struct SignaturePreview: View {
    let sig: Signature
    var body: some View {
        Canvas { ctx, size in
            let w = min(size.width, size.height * sig.aspect)
            let h = w / sig.aspect
            ctx.translateBy(x: (size.width - w) / 2, y: (size.height - h) / 2)
            ctx.withCGContext { cg in AnnotRenderer.drawSignature(sig, width: w, in: cg) }
        }
        .accessibilityLabel("Signature")
    }
}

/// Pad plein écran pour dessiner une nouvelle signature.
struct SignaturePad: View {
    var onCancel: () -> Void
    var onSave: (Signature) -> Void

    @State private var strokes: [[CGPoint]] = []
    @State private var current: [CGPoint] = []
    @State private var color = SIGN_COLORS[0]
    private let strokeWidth: CGFloat = 3

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("Signez dans le cadre avec le doigt. Elle sera enregistrée pour vos prochains documents.")
                    .font(.subheadline).foregroundStyle(Brand.secondaryText)
                pad
                HStack(spacing: 0) {
                    ForEach(SIGN_COLORS, id: \.self) { c in ColorDot(argb: c, selected: color == c) { color = c } }
                    Spacer()
                    Button("Annuler le trait") { _ = strokes.popLast() }.disabled(strokes.isEmpty)
                    Button("Effacer") { strokes.removeAll() }.disabled(strokes.isEmpty).padding(.leading, 12)
                }
                .font(.subheadline)
                Spacer()
            }
            .padding()
            .navigationTitle("Nouvelle signature")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enregistrer") { if let s = normalized() { onSave(s) } }
                        .fontWeight(.semibold)
                        .disabled(strokes.isEmpty)
                }
            }
            .tint(Brand.primary)
        }
        .interactiveDismissDisabled()
    }

    private var pad: some View {
        let guide = Color(hex: 0xB8AFA8)
        return ZStack {
            Canvas { ctx, size in
                let baseY = size.height * 0.72
                var line = Path()
                line.move(to: CGPoint(x: size.width * 0.08, y: baseY))
                line.addLine(to: CGPoint(x: size.width * 0.92, y: baseY))
                ctx.stroke(line, with: .color(guide), style: StrokeStyle(lineWidth: 1, dash: [6, 5]))
                var x = Path()
                let x0 = size.width * 0.08, y0 = baseY - 18
                x.move(to: CGPoint(x: x0, y: y0)); x.addLine(to: CGPoint(x: x0 + 8, y: y0 + 8))
                x.move(to: CGPoint(x: x0 + 8, y: y0)); x.addLine(to: CGPoint(x: x0, y: y0 + 8))
                ctx.stroke(x, with: .color(guide), lineWidth: 1.5)
                for s in strokes + [current] where !s.isEmpty {
                    ctx.stroke(Path(smoothPath(s.map { Pt(x: $0.x, y: $0.y) })), with: .color(Color(argb: color)),
                               style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round, lineJoin: .round))
                }
            }
            if strokes.isEmpty && current.isEmpty {
                Text("Signez ici").font(.title3.weight(.semibold)).foregroundStyle(guide).allowsHitTesting(false)
            }
        }
        .aspectRatio(1.9, contentMode: .fit)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Brand.outline))
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    if let last = current.last, hypot(last.x - v.location.x, last.y - v.location.y) < 1.5 { return }
                    current.append(v.location)
                }
                .onEnded { _ in
                    if !current.isEmpty { strokes.append(current) }
                    current = []
                }
        )
    }

    /// Ramène les tracés à leur boîte englobante, en unités de largeur.
    private func normalized() -> Signature? {
        let all = strokes.flatMap { $0 }
        guard !all.isEmpty else { return nil }
        let pad = strokeWidth
        let minX = all.map(\.x).min()! - pad, minY = all.map(\.y).min()! - pad
        let bw = max(all.map(\.x).max()! + pad - minX, 1)
        let bh = max(all.map(\.y).max()! + pad - minY, 1)
        return Signature(
            id: UUID().uuidString,
            strokes: strokes.map { s in s.map { Pt(x: ($0.x - minX) / bw, y: ($0.y - minY) / bw) } },
            aspect: min(max(bw / bh, 0.2), 20),
            color: color,
            width: strokeWidth / bw
        )
    }
}

/// Écran « Mes signatures ».
struct SignaturesView: View {
    @ObservedObject var store: SignatureStore
    var onBack: () -> Void
    @State private var pad = false
    @State private var deleting: Signature?

    var body: some View {
        NavigationStack {
            Group {
                if store.signatures.isEmpty {
                    VStack(spacing: 14) {
                        Image(systemName: "signature")
                            .font(.system(size: 40, weight: .semibold))
                            .foregroundStyle(Brand.ink)
                            .frame(width: 88, height: 88)
                            .background(Circle().fill(Brand.sun))
                        Text("Aucune signature").font(.title2.weight(.bold))
                        Text("Créez votre signature une fois, puis posez-la sur n'importe quel PDF en un geste.")
                            .font(.subheadline).foregroundStyle(Brand.secondaryText).multilineTextAlignment(.center)
                    }
                    .padding(32)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            ForEach(store.signatures) { sig in
                                SignaturePreview(sig: sig)
                                    .padding(.horizontal, 56).padding(.vertical, 18)
                                    .frame(height: 120)
                                    .frame(maxWidth: .infinity)
                                    .background(RoundedRectangle(cornerRadius: 22).fill(.white).shadow(color: .black.opacity(0.06), radius: 3, y: 1))
                                    .overlay(alignment: .topTrailing) {
                                        Button { deleting = sig } label: {
                                            Image(systemName: "trash").foregroundStyle(Color(hex: 0x8A7F79)).frame(width: 44, height: 44)
                                        }
                                        .accessibilityLabel("Supprimer")
                                    }
                            }
                        }
                        .padding(20)
                    }
                }
            }
            .background(Brand.background)
            .navigationTitle("Mes signatures")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: onBack) { Image(systemName: "chevron.left") }.accessibilityLabel("Retour")
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button { pad = true } label: {
                    Label("Nouvelle signature", systemImage: "plus")
                        .font(.headline)
                        .padding(.horizontal, 22).padding(.vertical, 14)
                        .background(Capsule().fill(Brand.coral))
                        .foregroundStyle(Brand.ink)
                        .shadow(radius: 6, y: 2)
                }
                .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: $pad) {
            SignaturePad(onCancel: { pad = false }) { sig in store.add(sig); pad = false }
        }
        .alert("Supprimer cette signature ?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("Supprimer", role: .destructive) { if let d = deleting { store.delete(d) }; deleting = nil }
            Button("Annuler", role: .cancel) { deleting = nil }
        } message: {
            Text("Les PDF déjà signés ne sont pas modifiés.")
        }
    }
}

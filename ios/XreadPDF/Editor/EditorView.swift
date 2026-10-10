import PDFKit
import SwiftUI

struct EditorView: View {
    @ObservedObject var session: EditorSession
    let signatures: [Signature]
    let inLibrary: Bool
    var onClose: () -> Void
    var onSave: (_ asCopy: Bool) -> Void
    var onNewSignature: (Signature) -> Void

    @State private var confirmExit = false
    @State private var confirmSave = false
    @State private var pagesSheet = false

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack(alignment: .bottom) {
                Brand.surfaceHigh.ignoresSafeArea()
                if !session.pages.isEmpty {
                    EditorPageView(session: session)
                }
                PageNav(session: session) { pagesSheet = true }
                    .padding(.bottom, 12)
            }
            VStack(spacing: 0) {
                ToolOptions(session: session, signatures: signatures)
                Divider()
                ToolBar(session: session, signatures: signatures)
            }
            .background(Brand.background.shadow(.drop(radius: 6)))
        }
        .background(Brand.background)
        .overlay { if session.saving { savingOverlay } }
        .sheet(item: $session.textDialog) { d in
            TextEntrySheet(dialog: d, onCancel: { session.textDialog = nil }, onConfirm: session.confirmText)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $session.padOpen) {
            SignaturePad(onCancel: { session.padOpen = false }) { sig in
                onNewSignature(sig)
                session.signature = sig
                session.padOpen = false
                session.tool = .signature
            }
        }
        .sheet(isPresented: $pagesSheet) {
            PagesSheet(session: session) { pagesSheet = false }
                .presentationDetents([.medium, .large])
        }
        .confirmationDialog("Enregistrer les modifications", isPresented: $confirmSave, titleVisibility: .visible) {
            Button("Remplacer l'original") { onSave(false) }
            Button("Créer une copie") { onSave(true) }
            Button("Annuler", role: .cancel) {}
        } message: {
            Text("Remplacer le document d'origine, ou garder l'original et créer une copie modifiée ?")
        }
        .alert("Quitter sans enregistrer ?", isPresented: $confirmExit) {
            Button("Quitter", role: .destructive, action: onClose)
            Button("Continuer", role: .cancel) {}
        } message: {
            Text("Les modifications apportées à ce PDF seront perdues.")
        }
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button { if session.dirty { confirmExit = true } else { onClose() } } label: {
                Image(systemName: "xmark").font(.title3.weight(.semibold)).frame(width: 44, height: 44)
            }
            .accessibilityLabel("Fermer")
            VStack(alignment: .leading, spacing: 0) {
                Text("Modifier").font(.headline)
                Text(session.url.deletingPathExtension().lastPathComponent)
                    .font(.caption).foregroundStyle(Brand.secondaryText).lineLimit(1)
            }
            Spacer(minLength: 4)
            Button(action: session.undo) { Image(systemName: "arrow.uturn.backward").frame(width: 40, height: 44) }
                .disabled(!session.canUndo).accessibilityLabel("Annuler").accessibilityIdentifier("undo")
            Button(action: session.redo) { Image(systemName: "arrow.uturn.forward").frame(width: 40, height: 44) }
                .disabled(!session.canRedo).accessibilityLabel("Rétablir").accessibilityIdentifier("redo")
            Button {
                if inLibrary { confirmSave = true } else { onSave(true) }
            } label: {
                Text("Enregistrer").font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(Capsule().fill(session.dirty ? Brand.primary : Color.gray.opacity(0.3)))
                    .foregroundStyle(.white)
            }
            .disabled(!session.dirty || session.saving)
            .accessibilityIdentifier("save")
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .tint(Brand.primary)
    }

    private var savingOverlay: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            HStack(spacing: 14) {
                ProgressView()
                Text("Enregistrement du PDF…").font(.subheadline.weight(.semibold))
            }
            .padding(24)
            .background(RoundedRectangle(cornerRadius: 22).fill(Brand.card))
        }
    }
}

// MARK: - Navigation de pages

private struct PageNav: View {
    @ObservedObject var session: EditorSession
    var onPages: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            Button { session.goTo(session.current - 1) } label: {
                Image(systemName: "chevron.left").frame(width: 40, height: 40)
            }
            .disabled(session.current == 0).accessibilityLabel("Page précédente")
            Text("Page \(session.current + 1) / \(session.pages.count)")
                .font(.subheadline.weight(.semibold)).monospacedDigit()
            Button { session.goTo(session.current + 1) } label: {
                Image(systemName: "chevron.right").frame(width: 40, height: 40)
            }
            .disabled(session.current >= session.pages.count - 1).accessibilityLabel("Page suivante")
            Rectangle().fill(Brand.cream.opacity(0.25)).frame(width: 1, height: 22)
            Button(action: onPages) {
                Image(systemName: "square.grid.2x2.fill").foregroundStyle(Brand.sun).frame(width: 44, height: 40)
            }
            .accessibilityLabel("Organiser les pages")
        }
        .foregroundStyle(Brand.cream)
        .padding(.horizontal, 6)
        .background(Capsule().fill(Brand.night).shadow(radius: 6, y: 2))
        .tint(Brand.cream)
    }
}

private struct PagesSheet: View {
    @ObservedObject var session: EditorSession
    var onDone: () -> Void
    @State private var info: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                Text("Pivoter, déplacer ou supprimer. Touchez une page pour l'ouvrir.")
                    .font(.subheadline).foregroundStyle(Brand.secondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                if let info { Text(info).font(.footnote).foregroundStyle(.red).padding(.horizontal) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 12)], spacing: 12) {
                    ForEach(Array(session.pages.enumerated()), id: \.element.src) { i, p in
                        VStack(spacing: 6) {
                            Button { session.goTo(i); onDone() } label: {
                                PageThumb(document: session.document, page: p)
                                    .frame(height: 140)
                                    .overlay(alignment: .bottomLeading) {
                                        Text("\(i + 1)").font(.caption.weight(.bold))
                                            .padding(.horizontal, 8).padding(.vertical, 2)
                                            .background(Capsule().fill(Brand.night)).foregroundStyle(Brand.cream)
                                            .padding(6)
                                    }
                                    .overlay(alignment: .topTrailing) {
                                        if !p.annots.isEmpty { Circle().fill(Brand.coral).frame(width: 10, height: 10).padding(6) }
                                    }
                            }
                            .buttonStyle(.plain)
                            HStack(spacing: 0) {
                                iconButton("chevron.left", "Avancer", enabled: i > 0) { session.movePage(i, by: -1) }
                                iconButton("rotate.right", "Pivoter") { session.rotatePage(i) }
                                iconButton("trash", "Supprimer") {
                                    if !session.deletePage(i) { info = "Un PDF doit garder au moins une page." }
                                }
                                iconButton("chevron.right", "Reculer", enabled: i < session.pages.count - 1) { session.movePage(i, by: 1) }
                            }
                        }
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 18).fill(i == session.current ? Brand.coral.opacity(0.18) : Brand.surfaceHigh))
                    }
                }
                .padding()
            }
            .navigationTitle("Organiser les pages")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("OK", action: onDone) } }
        }
    }

    private func iconButton(_ icon: String, _ label: String, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).frame(maxWidth: .infinity, minHeight: 32) }
            .disabled(!enabled)
            .accessibilityLabel(label)
    }
}

private struct PageThumb: View {
    let document: PDFDocument
    let page: EditPage
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
                    .rotationEffect(.degrees(Double(page.rotation)))
                    .border(Brand.outline, width: 1)
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity)
        .task(id: page.src) {
            guard let p = document.page(at: page.src) else { return }
            let w: CGFloat = 200
            let h = w * page.height / max(page.width, 1)
            image = p.thumbnail(of: CGSize(width: w, height: h), for: .cropBox)
        }
    }
}

// MARK: - Outils

private struct ToolBar: View {
    @ObservedObject var session: EditorSession
    let signatures: [Signature]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Tool.allCases) { tool in
                let active = session.tool == tool
                Button {
                    session.tool = tool
                    if tool == .signature && signatures.isEmpty { session.padOpen = true }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: tool.icon)
                            .font(.system(size: 18, weight: .semibold))
                            .frame(width: 52, height: 32)
                            .background(Capsule().fill(active ? Brand.coral : Color.clear))
                            .foregroundStyle(active ? Brand.ink : Brand.secondaryText)
                        Text(tool.label).font(.caption2.weight(active ? .semibold : .regular))
                            .foregroundStyle(active ? Color.primary : Brand.secondaryText)
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(tool.label)
                .accessibilityIdentifier("tool-\(tool.rawValue)")
                .accessibilityAddTraits(active ? [.isSelected, .isButton] : .isButton)
            }
        }
        .padding(.horizontal, 4)
    }
}

private struct ToolOptions: View {
    @ObservedObject var session: EditorSession
    let signatures: [Signature]

    var body: some View {
        Group {
            switch session.tool {
            case .select:
                if let sel = session.annot(session.selected), sel.isPlaced {
                    HStack(spacing: 8) {
                        if let t = sel.asText {
                            chip("Modifier le texte", "pencil") { session.openEditText(t) }
                        }
                        chip("Supprimer", "trash", destructive: true) { session.deleteSelected() }
                        if sel.asText == nil {
                            Text("Poignée ● pour agrandir").font(.caption).foregroundStyle(Brand.secondaryText).lineLimit(2)
                        }
                        Spacer(minLength: 0)
                    }
                } else {
                    hint("Touchez un texte ou une signature pour le déplacer. Pincez pour zoomer.")
                }
            case .pen:
                HStack(spacing: 0) {
                    ForEach(PEN_COLORS, id: \.self) { c in ColorDot(argb: c, selected: session.penColor == c, size: 24) { session.penColor = c } }
                    Spacer()
                    ForEach(PEN_WIDTHS, id: \.self) { w in
                        Button { session.penWidth = w } label: {
                            Circle().fill(Color(argb: session.penColor)).frame(width: w * 3.2, height: w * 3.2)
                                .frame(width: 40, height: 40)
                                .background(Circle().fill(session.penWidth == w ? Brand.chip : .clear))
                        }
                        .buttonStyle(.plain).accessibilityLabel("Épaisseur")
                    }
                }
            case .highlighter:
                HStack(spacing: 0) {
                    ForEach(HIGHLIGHT_COLORS, id: \.self) { c in ColorDot(argb: c, selected: session.highlightColor == c, size: 24) { session.highlightColor = c } }
                    hint("Glissez sur le texte à surligner").padding(.leading, 8)
                    Spacer()
                }
            case .text:
                HStack(spacing: 0) {
                    ForEach(PEN_COLORS, id: \.self) { c in ColorDot(argb: c, selected: session.textColor == c, size: 24) { session.textColor = c } }
                    Spacer()
                    ForEach(Array(zip(["S", "M", "L"], TEXT_SIZES)), id: \.0) { label, size in
                        Button { session.textSize = size } label: {
                            Text(label).font(.subheadline.weight(.semibold)).frame(width: 40, height: 40)
                                .background(Circle().fill(session.textSize == size ? Brand.chip : .clear))
                        }
                        .buttonStyle(.plain)
                    }
                }
            case .signature:
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button { session.padOpen = true } label: {
                            Label("Nouvelle", systemImage: "plus").font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 12).padding(.vertical, 10)
                                .background(RoundedRectangle(cornerRadius: 14).fill(Brand.sun))
                                .foregroundStyle(Brand.ink)
                        }
                        .buttonStyle(.plain)
                        ForEach(signatures) { sig in
                            let active = session.signature?.id == sig.id
                            Button { session.signature = sig } label: {
                                SignaturePreview(sig: sig)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .frame(width: 92, height: 44)
                                    .background(RoundedRectangle(cornerRadius: 14).fill(.white))
                                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(active ? Brand.coral : Brand.outline, lineWidth: active ? 2.5 : 1))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Choisir cette signature")
                        }
                        if !signatures.isEmpty { hint("Touchez la page à l'endroit où signer") }
                    }
                }
            case .eraser:
                hint("Glissez sur un trait, un texte ou une signature pour l'effacer.")
            }
        }
        .frame(height: 60)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func hint(_ s: String) -> some View {
        Text(s).font(.footnote).foregroundStyle(Brand.secondaryText).lineLimit(2)
    }

    private func chip(_ label: String, _ icon: String, destructive: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(label, systemImage: icon).font(.subheadline)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Capsule().strokeBorder(Brand.outline))
                .foregroundStyle(destructive ? Color.red : Color.primary)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Saisie de texte

private struct TextEntrySheet: View {
    let dialog: TextDialog
    var onCancel: () -> Void
    var onConfirm: (String) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                TextField("Texte", text: $text, axis: .vertical)
                    .lineLimit(2...6)
                    .focused($focused)
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 14).strokeBorder(Brand.outline))
                HStack(spacing: 8) {
                    quick("Date du jour") { insert(Date().formatted(date: .numeric, time: .omitted)) }
                    quick("Lu et approuvé") { insert("Lu et approuvé") }
                }
                Spacer()
            }
            .padding()
            .navigationTitle(dialog.editId == nil ? "Ajouter du texte" : "Modifier le texte")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler", action: onCancel) }
                ToolbarItem(placement: .confirmationAction) { Button("OK") { onConfirm(text) }.fontWeight(.semibold) }
            }
            .onAppear { text = dialog.initial; focused = true }
        }
    }

    private func insert(_ s: String) {
        let sep = text.isEmpty || text.hasSuffix(" ") || text.hasSuffix("\n") ? "" : " "
        text += sep + s
    }

    private func quick(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.subheadline)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Capsule().strokeBorder(Brand.outline))
        }
        .buttonStyle(.plain)
    }
}

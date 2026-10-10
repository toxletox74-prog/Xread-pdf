import SwiftUI
import UniformTypeIdentifiers
import VisionKit

struct LibraryView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var library: Library
    @EnvironmentObject var signatures: SignatureStore

    @State private var query = ""
    @State private var importing = false
    @State private var scanning = false
    @State private var renaming: PdfFile?
    @State private var newName = ""
    @State private var deleting: PdfFile?

    private var shown: [PdfFile] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return q.isEmpty ? library.files : library.files.filter { $0.name.localizedCaseInsensitiveContains(q) }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header
                if library.files.isEmpty {
                    emptyState
                } else {
                    VStack(spacing: 14) {
                        searchField
                        HStack(spacing: 8) {
                            Text("Mes documents").font(.headline)
                            Text("\(shown.count)")
                                .font(.caption.weight(.semibold))
                                .padding(.horizontal, 9).padding(.vertical, 2)
                                .background(Capsule().fill(Brand.chip))
                            Spacer()
                        }
                        if shown.isEmpty {
                            Text("Aucun document ne correspond à « \(query) ».")
                                .font(.subheadline).foregroundStyle(Brand.secondaryText)
                                .padding(32)
                        }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 14)], spacing: 14) {
                            ForEach(shown) { file in
                                DocCard(
                                    file: file,
                                    onOpen: { model.open(file.url) },
                                    onEdit: { model.edit(file.url, tool: .select, returnTo: nil) },
                                    onSign: { model.edit(file.url, tool: .signature, returnTo: nil) },
                                    onRename: { newName = file.name; renaming = file },
                                    onDelete: { deleting = file }
                                )
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
            .padding(.bottom, 24)
        }
        .background(Brand.background.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .scrollDismissesKeyboard(.immediately)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.pdf]) { result in
            if case .success(let url) = result { model.openExternal(url) }
        }
        .fullScreenCover(isPresented: $scanning) {
            DocumentScanner { images in
                scanning = false
                model.scanned(images)
            } onCancel: {
                scanning = false
            }
            .ignoresSafeArea()
        }
        .alert("Renommer", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Nom du document", text: $newName)
            Button("OK") {
                if let f = renaming, !newName.trimmingCharacters(in: .whitespaces).isEmpty,
                   !library.rename(f, to: newName.trimmingCharacters(in: .whitespaces)) {
                    model.message("Un fichier porte déjà ce nom")
                }
                renaming = nil
            }
            Button("Annuler", role: .cancel) { renaming = nil }
        }
        .alert("Supprimer ce PDF ?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("Supprimer", role: .destructive) {
                if let f = deleting { library.delete(f); model.message("PDF supprimé") }
                deleting = nil
            }
            Button("Annuler", role: .cancel) { deleting = nil }
        } message: {
            Text("« \(deleting?.name ?? "") » sera définitivement supprimé.")
        }
        .onAppear { library.refresh() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(spacing: 14) {
                BrandMark(size: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Xread PDF").font(.title2.weight(.black)).foregroundStyle(Brand.cream)
                    Text("Scanner · Modifier · Signer · Partager")
                        .font(.subheadline).foregroundStyle(Brand.cream.opacity(0.72))
                }
            }
            HStack(spacing: 10) {
                ActionTile(label: "Scanner", hint: "Appareil photo", icon: "doc.viewfinder", color: Brand.coral) {
                    if VNDocumentCameraViewController.isSupported { scanning = true }
                    else { model.message("Scanner indisponible sur cet appareil") }
                }
                ActionTile(label: "Ouvrir", hint: "Un PDF", icon: "folder", color: Brand.sky) { importing = true }
                ActionTile(
                    label: "Signatures",
                    hint: signatures.signatures.isEmpty ? "À créer" : "\(signatures.signatures.count) enregistrée\(signatures.signatures.count > 1 ? "s" : "")",
                    icon: "signature", color: Brand.sun
                ) { model.screen = .signatures }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, safeTop + 18)
        .padding(.bottom, 22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            BottomRounded(radius: 32).fill(Brand.night)
        )
    }

    private var safeTop: CGFloat {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.safeAreaInsets.top ?? 47
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Brand.secondaryText)
            TextField("Rechercher un document", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(Brand.secondaryText) }
                    .accessibilityLabel("Effacer la recherche")
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(Capsule().fill(Brand.card))
        .overlay(Capsule().strokeBorder(Brand.outline))
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            BrandMark(size: 96)
            Text("Rien ici… pour l'instant").font(.title2.weight(.bold)).padding(.top, 10)
            Text("Scannez un document ou ouvrez un PDF pour le lire, l'annoter et le signer.")
                .font(.subheadline).foregroundStyle(Brand.secondaryText).multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32).padding(.vertical, 40)
    }
}

private struct ActionTile: View {
    let label: String
    let hint: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(Color.white.opacity(0.35)))
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(.subheadline.weight(.bold)).lineLimit(1).minimumScaleFactor(0.8)
                    Text(hint).font(.caption2).opacity(0.75).lineLimit(1)
                }
            }
            .foregroundStyle(Brand.ink)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(color))
        }
        .buttonStyle(.plain)
    }
}

private struct DocCard: View {
    let file: PdfFile
    var onOpen: () -> Void
    var onEdit: () -> Void
    var onSign: () -> Void
    var onRename: () -> Void
    var onDelete: () -> Void
    @State private var thumb: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button(action: onOpen) {
                ZStack {
                    Color.white
                    if let thumb {
                        Image(uiImage: thumb).resizable().scaledToFill()
                            .frame(maxHeight: .infinity, alignment: .top)
                    } else {
                        Image(systemName: "doc.richtext").font(.largeTitle).foregroundStyle(Brand.outline)
                    }
                }
                .aspectRatio(0.78, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Brand.outline))
            }
            .buttonStyle(.plain)
            HStack(alignment: .top, spacing: 4) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(file.name).font(.subheadline.weight(.semibold)).lineLimit(2)
                    Text("\(file.modified.formatted(date: .abbreviated, time: .omitted)) · \(ByteCountFormatter.string(fromByteCount: file.size, countStyle: .file))")
                        .font(.caption2).foregroundStyle(Brand.secondaryText)
                }
                Spacer(minLength: 0)
                Menu {
                    Button { onEdit() } label: { Label("Modifier", systemImage: "pencil") }
                    Button { onSign() } label: { Label("Signer", systemImage: "signature") }
                    ShareLink(item: file.url) { Label("Partager", systemImage: "square.and.arrow.up") }
                    Button { onRename() } label: { Label("Renommer", systemImage: "character.cursor.ibeam") }
                    Button(role: .destructive) { onDelete() } label: { Label("Supprimer", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis").font(.body.weight(.semibold))
                        .frame(width: 36, height: 36).contentShape(Rectangle())
                }
                .accessibilityLabel("Actions")
            }
            .padding(.horizontal, 4)
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Brand.card).shadow(color: .black.opacity(0.06), radius: 3, y: 1))
        .task(id: file.id) { thumb = await Thumbnails.load(file, width: 360) }
    }
}

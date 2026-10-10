import PDFKit
import UIKit

/// Arguments de lancement pour les tests automatiques sur simulateur (sans effet en usage normal) :
///   -uitest-seed            ajoute un PDF d'exemple à la bibliothèque
///   -uitest-screen X        ouvre directement : viewer | editor | sign | signatures
///   -uitest-export          applique des annotations d'exemple et enregistre une copie
@MainActor
enum DebugSeed {
    static func apply(_ model: AppModel) {
        let args = ProcessInfo.processInfo.arguments
        guard args.contains(where: { $0.hasPrefix("-uitest") }) else { return }
        let url = model.library.dir.appendingPathComponent("Exemple.pdf")
        if args.contains("-uitest-seed"), !FileManager.default.fileExists(atPath: url.path) {
            let r = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
            let data = r.pdfData { ctx in
                for i in 1...2 {
                    ctx.beginPage()
                    ("Document d'exemple — page \(i)" as NSString).draw(at: CGPoint(x: 60, y: 70), withAttributes: [.font: UIFont.boldSystemFont(ofSize: 26)])
                    UIColor(white: 0.88, alpha: 1).setFill()
                    UIRectFill(CGRect(x: 60, y: 140, width: 475, height: 300))
                }
            }
            try? data.write(to: url)
            model.library.refresh()
        }
        if model.signatures.signatures.isEmpty, args.contains("-uitest-seed") {
            let pts = (0...40).map { i -> Pt in
                let t = CGFloat(i) / 40
                return Pt(x: 0.05 + 0.9 * t, y: 0.18 + 0.1 * sin(t * 12))
            }
            model.signatures.add(Signature(id: "demo", strokes: [pts], aspect: 2.6, color: SIGN_COLORS[1], width: 0.012))
        }
        if let i = args.firstIndex(of: "-uitest-screen"), i + 1 < args.count {
            switch args[i + 1] {
            case "viewer": model.open(url)
            case "editor": model.edit(url, tool: .pen, returnTo: url)
            case "sign": model.edit(url, tool: .signature, returnTo: url)
            case "signatures": model.screen = .signatures
            default: break
            }
        }
        if args.contains("-uitest-export") {
            model.edit(url, tool: .select, returnTo: url)
            guard case .editor(let s, _) = model.screen else { return }
            let ink = Annot.ink(InkAnnot(id: s.newId(), points: [Pt(x: 80, y: 500), Pt(x: 300, y: 520), Pt(x: 500, y: 480)], color: PEN_COLORS[1], width: 4, highlighter: false))
            let text = Annot.text(TextAnnot(id: s.newId(), text: "Lu et approuvé", x: 80, y: 560, size: 18, color: PEN_COLORS[0], rotation: 0))
            var list: [Annot] = [ink, text]
            if let sig = model.signatures.signatures.first {
                list.append(.sign(SignAnnot(id: s.newId(), sig: sig, x: 300, y: 620, width: 200, rotation: 0)))
            }
            s.commit(s.mapAnnots { $0.append(contentsOf: list) })
            s.rotatePage(1)
            model.saveEdits(asCopy: true)
        }
    }
}

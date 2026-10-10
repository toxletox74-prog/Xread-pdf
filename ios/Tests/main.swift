// Test hors app (macOS) : exporte des PDF de test avec annotations, vérifié ensuite en Python.
import Foundation
import PDFKit

let dir = URL(fileURLWithPath: CommandLine.arguments[1])
for r0 in [0, 90, 180, 270] {
    guard let doc = PDFDocument(url: dir.appendingPathComponent("in\(r0).pdf")), let page = doc.page(at: 0) else {
        print("ERREUR lecture in\(r0).pdf"); exit(1)
    }
    let s = PdfExporter.intrinsicSize(page)
    print("r0=\(r0) intrinsic=\(s)")
    for e in [0, 90] {
        let ink = Annot.ink(InkAnnot(id: 1, points: [Pt(x: 100, y: 60), Pt(x: 180, y: 60)], color: 0xFF1F4FD8, width: 10, highlighter: false))
        let text = Annot.text(TextAnnot(id: 2, text: "Bonjour é", x: 100, y: 150, size: 20, color: 0xFF188038, rotation: e))
        let ep = EditPage(src: 0, width: s.width, height: s.height, rotation: e, annots: [ink, text])
        try! PdfExporter.export(document: doc, pages: [ep], to: dir.appendingPathComponent("out\(r0)_\(e).pdf"))
    }
}
print("OK")

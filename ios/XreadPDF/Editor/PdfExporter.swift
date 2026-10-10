import CoreGraphics
import Foundation
import PDFKit

enum ExportError: Error { case cannotCreate }

/// Écrit le PDF modifié : chaque page d'origine est redessinée en vectoriel (texte conservé),
/// tournée de la rotation choisie, puis les annotations sont dessinées par-dessus.
enum PdfExporter {

    /// Taille intrinsèque d'une page (CropBox tournée de /Rotate), comme affichée.
    static func intrinsicSize(_ page: PDFPage) -> CGSize {
        let b = page.bounds(for: .cropBox)
        return normDeg(page.rotation) % 180 == 0 ? b.size : CGSize(width: b.height, height: b.width)
    }

    static func export(document: PDFDocument, pages: [EditPage], to url: URL) throws {
        var info: [CFString: Any] = [kCGPDFContextCreator: "Xread PDF"]
        if let title = document.documentAttributes?[PDFDocumentAttribute.titleAttribute] as? String {
            info[kCGPDFContextTitle] = title
        }
        guard let ctx = CGContext(url as CFURL, mediaBox: nil, info as CFDictionary) else {
            throw ExportError.cannotCreate
        }
        for ep in pages {
            guard let page = document.page(at: ep.src) else { continue }
            let size = intrinsicSize(page)
            let dw = size.width, dh = size.height
            let e = normDeg(ep.rotation)
            var box = e % 180 == 0
                ? CGRect(x: 0, y: 0, width: dw, height: dh)
                : CGRect(x: 0, y: 0, width: dh, height: dw)

            ctx.beginPage(mediaBox: &box)
            ctx.saveGState()
            ctx.concatenate(rotation(e, dw: dw, dh: dh))

            // Page d'origine dans (0, 0, dw, dh), repère PDF (y vers le haut)
            ctx.saveGState()
            page.draw(with: .cropBox, to: ctx)
            ctx.restoreGState()

            // Annotations : repère y vers le bas, mises à l'échelle si besoin
            ctx.translateBy(x: 0, y: dh)
            ctx.scaleBy(x: 1, y: -1)
            if ep.width > 0, ep.height > 0 {
                ctx.scaleBy(x: dw / ep.width, y: dh / ep.height)
            }
            AnnotRenderer.draw(ep.annots, in: ctx)

            ctx.restoreGState()
            ctx.endPage()
        }
        ctx.closePDF()
    }

    /// Repère intrinsèque (y vers le haut) → page de sortie tournée de [e]° (sens horaire).
    static func rotation(_ e: Int, dw: CGFloat, dh: CGFloat) -> CGAffineTransform {
        switch e {
        case 90: return CGAffineTransform(a: 0, b: -1, c: 1, d: 0, tx: 0, ty: dw)
        case 180: return CGAffineTransform(a: -1, b: 0, c: 0, d: -1, tx: dw, ty: dh)
        case 270: return CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: dh, ty: 0)
        default: return .identity
        }
    }
}

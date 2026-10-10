import CoreGraphics
import CoreText
import Foundation

// Modèle d'édition, identique à la version Android.
// Coordonnées en POINTS PDF dans l'espace "intrinsèque" de la page (CropBox + /Rotate
// d'origine), origine en haut à gauche, y vers le bas. La rotation ajoutée par l'utilisateur
// (EditPage.rotation) est appliquée par-dessus, à l'affichage comme à l'export.

struct Pt: Equatable, Hashable {
    var x: CGFloat
    var y: CGFloat

    static func + (a: Pt, b: Pt) -> Pt { Pt(x: a.x + b.x, y: a.y + b.y) }
    static func - (a: Pt, b: Pt) -> Pt { Pt(x: a.x - b.x, y: a.y - b.y) }
    static func * (a: Pt, k: CGFloat) -> Pt { Pt(x: a.x * k, y: a.y * k) }

    /// Rotation horaire (repère y vers le bas) d'un multiple de 90°.
    func rotated(_ deg: Int) -> Pt {
        switch normDeg(deg) {
        case 90: return Pt(x: -y, y: x)
        case 180: return Pt(x: -x, y: -y)
        case 270: return Pt(x: y, y: -x)
        default: return self
        }
    }

    var cg: CGPoint { CGPoint(x: x, y: y) }
}

func normDeg(_ d: Int) -> Int { ((d % 360) + 360) % 360 }
func dist(_ a: Pt, _ b: Pt) -> CGFloat { hypot(a.x - b.x, a.y - b.y) }

/// Signature vectorielle : points en unités de largeur (x ∈ [0,1], y ∈ [0, 1/aspect]).
struct Signature: Identifiable, Equatable {
    var id: String
    var strokes: [[Pt]]
    var aspect: CGFloat
    var color: UInt32
    var width: CGFloat
}

struct InkAnnot: Equatable {
    var id: Int
    var points: [Pt]
    var color: UInt32
    var width: CGFloat
    var highlighter: Bool
}

struct TextAnnot: Equatable {
    var id: Int
    var text: String
    var x: CGFloat
    var y: CGFloat
    var size: CGFloat
    var color: UInt32
    var rotation: Int
}

struct SignAnnot: Equatable {
    var id: Int
    var sig: Signature
    var x: CGFloat
    var y: CGFloat
    var width: CGFloat
    var rotation: Int
    var height: CGFloat { width / sig.aspect }
}

enum Annot: Equatable, Identifiable {
    case ink(InkAnnot)
    case text(TextAnnot)
    case sign(SignAnnot)

    var id: Int {
        switch self {
        case .ink(let a): return a.id
        case .text(let a): return a.id
        case .sign(let a): return a.id
        }
    }

    var asText: TextAnnot? { if case .text(let t) = self { return t } else { return nil } }

    /// Élément posé (texte, signature) : ancre haut-gauche et rotation.
    var isPlaced: Bool { if case .ink = self { return false } else { return true } }

    var anchor: Pt {
        switch self {
        case .ink: return Pt(x: 0, y: 0)
        case .text(let a): return Pt(x: a.x, y: a.y)
        case .sign(let a): return Pt(x: a.x, y: a.y)
        }
    }

    var rotation: Int {
        switch self {
        case .ink: return 0
        case .text(let a): return a.rotation
        case .sign(let a): return a.rotation
        }
    }

    func moved(to p: Pt) -> Annot {
        switch self {
        case .ink: return self
        case .text(var a): a.x = p.x; a.y = p.y; return .text(a)
        case .sign(var a): a.x = p.x; a.y = p.y; return .sign(a)
        }
    }

    /// Taille de la boîte en points (repère local de l'élément).
    var boxSize: CGSize {
        switch self {
        case .ink: return .zero
        case .text(let a): return TextLayout.size(a.text, size: a.size)
        case .sign(let a): return CGSize(width: a.width, height: a.height)
        }
    }

    /// Point local → page.
    func corner(_ local: Pt) -> Pt { anchor + local.rotated(-rotation) }
    /// Point de la page → repère local.
    func toLocal(_ p: Pt) -> Pt { (p - anchor).rotated(rotation) }

    func hit(_ p: Pt, tol: CGFloat) -> Bool {
        switch self {
        case .ink(let a): return a.hit(p, tol: tol)
        default:
            let l = toLocal(p)
            let s = boxSize
            return l.x >= -tol && l.x <= s.width + tol && l.y >= -tol && l.y <= s.height + tol
        }
    }
}

extension InkAnnot {
    func hit(_ p: Pt, tol: CGFloat) -> Bool {
        let r = width / 2 + tol
        if points.count == 1 { return dist(p, points[0]) <= r }
        for i in 0..<max(points.count - 1, 0) where distToSegment(p, points[i], points[i + 1]) <= r { return true }
        return false
    }
}

private func distToSegment(_ p: Pt, _ a: Pt, _ b: Pt) -> CGFloat {
    let dx = b.x - a.x, dy = b.y - a.y
    let len2 = dx * dx + dy * dy
    if len2 == 0 { return dist(p, a) }
    let t = min(max(((p.x - a.x) * dx + (p.y - a.y) * dy) / len2, 0), 1)
    return dist(p, Pt(x: a.x + t * dx, y: a.y + t * dy))
}

struct EditPage: Equatable {
    /// Index de la page dans le PDF source.
    var src: Int
    /// Taille intrinsèque en points.
    var width: CGFloat
    var height: CGFloat
    /// Rotation ajoutée par l'utilisateur (0, 90, 180, 270).
    var rotation: Int = 0
    var annots: [Annot] = []
}

/// Tracé lissé (quadratiques passant par les milieux), partagé aperçu / export.
func smoothPath(_ points: [Pt]) -> CGPath {
    let path = CGMutablePath()
    guard let first = points.first else { return path }
    path.move(to: first.cg)
    switch points.count {
    case 1: path.addLine(to: first.cg)
    case 2: path.addLine(to: points[1].cg)
    default:
        for i in 1..<(points.count - 1) {
            let a = points[i], b = points[i + 1]
            path.addQuadCurve(to: CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2), control: a.cg)
        }
        path.addLine(to: points[points.count - 1].cg)
    }
    return path
}

func cgColor(_ argb: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((argb >> 16) & 0xFF) / 255,
        green: CGFloat((argb >> 8) & 0xFF) / 255,
        blue: CGFloat(argb & 0xFF) / 255,
        alpha: alpha
    )
}

/// Mise en page du texte avec CoreText (Helvetica) : la même pour l'aperçu et l'export.
enum TextLayout {
    static func font(_ size: CGFloat) -> CTFont { CTFontCreateWithName("Helvetica" as CFString, size, nil) }

    static func metrics(_ size: CGFloat) -> (ascent: CGFloat, line: CGFloat) {
        let f = font(size)
        let asc = CTFontGetAscent(f), desc = CTFontGetDescent(f)
        return (asc, (asc + desc) * 1.15)
    }

    static func lines(_ text: String, size: CGFloat, color: UInt32) -> [CTLine] {
        let attrs: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font(size),
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): cgColor(color),
        ]
        return text.components(separatedBy: "\n").map {
            CTLineCreateWithAttributedString(NSAttributedString(string: $0, attributes: attrs))
        }
    }

    static func size(_ text: String, size: CGFloat) -> CGSize {
        let ls = lines(text, size: size, color: 0xFF000000)
        let w = ls.map { CGFloat(CTLineGetTypographicBounds($0, nil, nil, nil)) }.max() ?? 0
        return CGSize(width: max(w, size * 0.3), height: CGFloat(ls.count) * metrics(size).line)
    }
}

/// Dessin des annotations dans un contexte Core Graphics en repère y vers le BAS, en points.
enum AnnotRenderer {
    static func draw(_ annots: [Annot], in ctx: CGContext) {
        for a in annots { draw(a, in: ctx) }
    }

    static func draw(_ a: Annot, in ctx: CGContext) {
        ctx.saveGState()
        defer { ctx.restoreGState() }
        switch a {
        case .ink(let ink):
            if ink.highlighter {
                ctx.setBlendMode(.multiply)
                ctx.setAlpha(0.4)
            }
            stroke([ink.points], color: ink.color, width: ink.width, in: ctx)
        case .text(let t):
            ctx.translateBy(x: t.x, y: t.y)
            ctx.rotate(by: -CGFloat(t.rotation) * .pi / 180)
            let m = TextLayout.metrics(t.size)
            ctx.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
            for (i, line) in TextLayout.lines(t.text, size: t.size, color: t.color).enumerated() {
                ctx.textPosition = CGPoint(x: 0, y: m.ascent + CGFloat(i) * m.line)
                CTLineDraw(line, ctx)
            }
        case .sign(let s):
            ctx.translateBy(x: s.x, y: s.y)
            ctx.rotate(by: -CGFloat(s.rotation) * .pi / 180)
            drawSignature(s.sig, width: s.width, in: ctx)
        }
    }

    static func drawSignature(_ sig: Signature, width w: CGFloat, in ctx: CGContext) {
        stroke(sig.strokes.map { $0.map { $0 * w } }, color: sig.color, width: sig.width * w, in: ctx)
    }

    static func stroke(_ strokes: [[Pt]], color: UInt32, width: CGFloat, in ctx: CGContext) {
        ctx.setStrokeColor(cgColor(color))
        ctx.setLineWidth(width)
        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)
        for s in strokes where !s.isEmpty {
            ctx.addPath(smoothPath(s))
        }
        ctx.strokePath()
    }
}

let PEN_COLORS: [UInt32] = [0xFF1A1A1F, 0xFF1F4FD8, 0xFFD93025, 0xFF188038]
let HIGHLIGHT_COLORS: [UInt32] = [0xFFFFE14D, 0xFF8CF08C, 0xFFFF9BD2, 0xFF8FD3FF]
let SIGN_COLORS: [UInt32] = [0xFF1A1A1F, 0xFF1F3FBF]
let PEN_WIDTHS: [CGFloat] = [1.2, 2.4, 4.5]
let HIGHLIGHT_WIDTH: CGFloat = 12
let TEXT_SIZES: [CGFloat] = [10, 14, 20]

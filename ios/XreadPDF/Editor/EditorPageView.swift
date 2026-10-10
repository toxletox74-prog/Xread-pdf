import PDFKit
import SwiftUI
import UIKit

/// Page éditable : UIScrollView (zoom au pincement, déplacement à 2 doigts) contenant le rendu
/// de la page et une couche d'annotations qui reçoit les touchers à 1 doigt.
struct EditorPageView: UIViewRepresentable {
    @ObservedObject var session: EditorSession

    func makeUIView(context: Context) -> PageScrollView { PageScrollView(session: session) }

    func updateUIView(_ view: PageScrollView, context: Context) { view.refresh() }
}

final class PageScrollView: UIScrollView, UIScrollViewDelegate {
    let session: EditorSession
    private let container = UIView()
    private let pageView = UIView()
    private let imageView = UIImageView()
    private let canvas: AnnotCanvas
    private var layoutKey = ""
    private var renderedKey = ""
    private static let renderQueue = DispatchQueue(label: "xreadpdf.render", qos: .userInitiated)

    init(session: EditorSession) {
        self.session = session
        canvas = AnnotCanvas(session: session)
        super.init(frame: .zero)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 6
        bouncesZoom = true
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        delaysContentTouches = false
        panGestureRecognizer.minimumNumberOfTouches = 2
        backgroundColor = .clear

        container.backgroundColor = .white
        container.layer.shadowColor = UIColor.black.cgColor
        container.layer.shadowOpacity = 0.18
        container.layer.shadowRadius = 6
        container.layer.shadowOffset = CGSize(width: 0, height: 2)
        addSubview(container)
        container.addSubview(pageView)
        imageView.contentMode = .scaleToFill
        pageView.addSubview(imageView)
        pageView.addSubview(canvas)
    }

    required init?(coder: NSCoder) { fatalError() }

    /// Appelé à chaque changement d'état de la séance.
    func refresh() {
        setNeedsLayout()
        canvas.setNeedsDisplay()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0, !session.pages.isEmpty else { return }
        let page = session.page
        let key = "\(session.current)|\(page.src)|\(page.rotation)|\(bounds.size)"
        if key != layoutKey {
            layoutKey = key
            setZoomScale(1, animated: false)
            let margin: CGFloat = 16, bottomRoom: CGFloat = 64
            let availW = max(bounds.width - 2 * margin, 1)
            let availH = max(bounds.height - margin - bottomRoom, 1)
            let turned = page.rotation % 180 != 0
            let ratio = turned ? page.height / page.width : page.width / page.height
            var dw = availW, dh = availW / ratio
            if dh > availH { dh = availH; dw = dh * ratio }
            let iw = turned ? dh : dw, ih = turned ? dw : dh

            container.transform = .identity
            container.frame = CGRect(x: 0, y: 0, width: dw, height: dh)
            container.layer.shadowPath = UIBezierPath(rect: container.bounds).cgPath
            contentSize = container.frame.size
            pageView.transform = .identity
            pageView.bounds = CGRect(x: 0, y: 0, width: iw, height: ih)
            pageView.center = CGPoint(x: dw / 2, y: dh / 2)
            pageView.transform = CGAffineTransform(rotationAngle: CGFloat(page.rotation) * .pi / 180)
            imageView.frame = pageView.bounds
            canvas.frame = pageView.bounds
            imageView.image = nil
            renderedKey = ""
            centerContent()
            renderPage(scale: 1)
        }
        centerContent()
    }

    private func centerContent() {
        let bottomRoom: CGFloat = 64
        let w = container.frame.width, h = container.frame.height
        let insetX = max((bounds.width - w) / 2, 0)
        let insetY = max((bounds.height - bottomRoom - h) / 2, 8)
        contentInset = UIEdgeInsets(top: insetY, left: insetX, bottom: max(insetY, bottomRoom), right: insetX)
    }

    private func renderPage(scale: CGFloat) {
        let page = session.page
        let size = pageView.bounds.size
        let pixels = min(UIScreen.main.scale * scale, 6)
        let key = "\(page.src)|\(size)|\(pixels)"
        guard key != renderedKey, let pdfPage = session.document.page(at: page.src) else { return }
        renderedKey = key
        let target = CGSize(width: size.width * pixels / UIScreen.main.scale, height: size.height * pixels / UIScreen.main.scale)
        Self.renderQueue.async { [weak self] in
            let img = pdfPage.thumbnail(of: target, for: .cropBox)
            DispatchQueue.main.async {
                guard let self, self.renderedKey == key else { return }
                self.imageView.image = img
            }
        }
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { container }

    func scrollViewDidZoom(_ scrollView: UIScrollView) { centerContent() }

    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
        canvas.contentScaleFactor = UIScreen.main.scale * scale
        canvas.setNeedsDisplay()
        renderPage(scale: scale > 1.3 ? min(scale, 3) : 1)
    }
}

/// Couche d'annotations : dessin + gestes à 1 doigt selon l'outil.
final class AnnotCanvas: UIView {
    let session: EditorSession

    private enum Drag {
        case ink(id: Int, color: UInt32, width: CGFloat, highlighter: Bool, points: [Pt])
        case erase
        case move(target: Annot, p0: Pt, onHandle: Bool, box: CGSize, moved: Bool, wasSelected: Bool)
        case tap(p0: Pt)
    }
    private var drag: Drag?

    init(session: EditorSession) {
        self.session = session
        super.init(frame: .zero)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    private var pxPerPt: CGFloat { bounds.width / max(session.page.width, 1) }
    private var zoom: CGFloat { (superview?.superview?.superview as? UIScrollView)?.zoomScale ?? 1 }

    private func pt(_ touch: UITouch) -> Pt {
        let p = touch.location(in: self)
        return Pt(x: p.x / pxPerPt, y: p.y / pxPerPt)
    }

    // MARK: Dessin

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext(), !session.pages.isEmpty else { return }
        let page = session.page
        ctx.saveGState()
        ctx.scaleBy(x: pxPerPt, y: pxPerPt)
        AnnotRenderer.draw(page.annots, in: ctx)
        ctx.restoreGState()

        guard let sel = session.annot(session.selected), sel.isPlaced else { return }
        let box = sel.boxSize
        let corners = [Pt(x: 0, y: 0), Pt(x: box.width, y: 0), Pt(x: box.width, y: box.height), Pt(x: 0, y: box.height)]
            .map { sel.corner($0) * pxPerPt }
        let z = zoom
        let path = UIBezierPath()
        path.move(to: corners[0].cg)
        corners.dropFirst().forEach { path.addLine(to: $0.cg) }
        path.close()
        path.lineWidth = 1.5 / z
        path.setLineDash([8 / z, 6 / z], count: 2, phase: 0)
        UIColor(Brand.primary).setStroke()
        path.stroke()
        let h = corners[2].cg
        UIColor.white.setFill()
        UIBezierPath(arcCenter: h, radius: 11 / z, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
        UIColor(Brand.primary).setFill()
        UIBezierPath(arcCenter: h, radius: 8 / z, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
    }

    // MARK: Gestes

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, !session.pages.isEmpty else { return }
        let p0 = pt(t)
        let tol = 18 / pxPerPt / zoom
        switch session.tool {
        case .pen, .highlighter:
            let hl = session.tool == .highlighter
            let id = session.newId()
            drag = .ink(id: id, color: hl ? session.highlightColor : session.penColor,
                        width: hl ? HIGHLIGHT_WIDTH : session.penWidth, highlighter: hl, points: [p0])
            session.beginGesture()
            updateInk()
        case .eraser:
            drag = .erase
            session.beginGesture()
            erase(at: p0, tol: tol)
        case .text, .signature:
            drag = .tap(p0: p0)
        case .select:
            let previous = session.annot(session.selected)
            var onHandle = false
            if let prev = previous, prev.isPlaced {
                let b = prev.boxSize
                onHandle = dist(prev.corner(Pt(x: b.width, y: b.height)), p0) <= tol * 1.4
            }
            guard let target = onHandle ? previous : session.hitPlaced(p0, tol: tol) else {
                session.selected = nil
                drag = nil
                return
            }
            let wasSelected = session.selected == target.id
            session.selected = target.id
            session.beginGesture()
            drag = .move(target: target, p0: p0, onHandle: onHandle, box: target.boxSize, moved: false, wasSelected: wasSelected)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, let d = drag else { return }
        let p = pt(t)
        switch d {
        case .ink(let id, let color, let width, let hl, var points):
            let minStep = 1 / pxPerPt / zoom
            if let last = points.last, dist(p, last) >= minStep {
                points.append(p)
                drag = .ink(id: id, color: color, width: width, highlighter: hl, points: points)
                updateInk()
            }
        case .erase:
            erase(at: p, tol: 18 / pxPerPt / zoom)
        case .move(let target, let p0, let onHandle, let box, let moved, let wasSelected):
            let slop = 8 / pxPerPt / zoom
            if !moved && dist(p, p0) < slop { return }
            let updated = onHandle ? resized(target, box: box, to: p) : target.moved(to: target.anchor + (p - p0))
            session.live(session.replacing(updated))
            drag = .move(target: target, p0: p0, onHandle: onHandle, box: box, moved: true, wasSelected: wasSelected)
        case .tap:
            break
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let d = drag else { return }
        drag = nil
        switch d {
        case .ink, .erase:
            session.endGesture()
        case .move(let target, _, let onHandle, _, let moved, let wasSelected):
            if moved {
                session.endGesture()
            } else {
                session.cancelGesture()
                if wasSelected, !onHandle, case .text(let ta) = target { session.openEditText(ta) }
            }
        case .tap(let p0):
            if session.tool == .text {
                if case .text(let ta)? = session.hitPlaced(p0, tol: 18 / pxPerPt / zoom) {
                    session.openEditText(ta)
                } else {
                    session.openNewText(p0)
                }
            } else if let sig = session.signature {
                session.placeSignature(at: p0, sig)
            } else {
                session.padOpen = true
            }
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        // Un 2e doigt (zoom) annule le geste en cours
        if case .tap = drag {} else if drag != nil { session.cancelGesture() }
        drag = nil
    }

    private func updateInk() {
        guard case .ink(let id, let color, let width, let hl, let points)? = drag else { return }
        let ink = Annot.ink(InkAnnot(id: id, points: points, color: color, width: width, highlighter: hl))
        session.live(session.mapAnnots { list in
            list.removeAll { $0.id == id }
            list.append(ink)
        })
    }

    private func erase(at p: Pt, tol: CGFloat) {
        let hit = session.hitAny(p, tol: tol)
        guard !hit.isEmpty else { return }
        session.live(session.mapAnnots { $0.removeAll { hit.contains($0.id) } })
    }
}

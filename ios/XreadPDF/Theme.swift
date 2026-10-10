import SwiftUI
import UIKit

/// Palette de l'icône « Fusée Partage » (mêmes valeurs que la version Android).
enum Brand {
    static let night = Color(hex: 0x2A1E3D)
    static let nightSoft = Color(hex: 0x3A2C52)
    static let coral = Color(hex: 0xFF5A4E)
    static let sun = Color(hex: 0xFFC93C)
    static let sky = Color(hex: 0x4C8DFF)
    static let cream = Color(hex: 0xFFF6EE)
    static let ink = Color(hex: 0x1A1A1F)

    /// Couleurs adaptées au mode clair / sombre.
    static let background = Color(light: 0xFFF8F2, dark: 0x15101D)
    static let card = Color(light: 0xFFFFFF, dark: 0x221B2B)
    static let surfaceHigh = Color(light: 0xF5E5DC, dark: 0x2C2436)
    static let primary = Color(light: 0xC7362C, dark: 0xFF8A7F)
    static let secondaryText = Color(light: 0x52443F, dark: 0xCEC2D4)
    static let outline = Color(light: 0xD8C2BB, dark: 0x4B4153)
    static let chip = Color(light: 0xE9DDFF, dark: 0x3B2D57)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { trait in
            UIColor(Color(hex: trait.userInterfaceStyle == .dark ? dark : light))
        })
    }

    /// Couleur ARGB stockée dans le modèle.
    init(argb: UInt32) { self.init(hex: argb & 0xFFFFFF) }
}

/// Le logo (la fusée) dans un carré arrondi violet.
struct BrandMark: View {
    var size: CGFloat
    var body: some View {
        Image("BrandMark")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .background(Brand.nightSoft)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.3, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Bouton-pastille de couleur.
struct ColorDot: View {
    var argb: UInt32
    var selected: Bool
    var size: CGFloat = 28
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Circle()
                .fill(Color(argb: argb))
                .frame(width: size, height: size)
                .overlay(Circle().strokeBorder(selected ? Color.primary : Color.black.opacity(0.12), lineWidth: selected ? 3 : 1))
                .frame(width: size + 14, height: size + 14)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Couleur")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Rectangle aux coins inférieurs arrondis (en-tête de l'accueil).
struct BottomRounded: Shape {
    var radius: CGFloat
    func path(in r: CGRect) -> Path {
        var p = Path()
        let rad = min(radius, r.height / 2, r.width / 2)
        p.move(to: CGPoint(x: r.minX, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - rad))
        p.addArc(center: CGPoint(x: r.maxX - rad, y: r.maxY - rad), radius: rad, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: r.minX + rad, y: r.maxY))
        p.addArc(center: CGPoint(x: r.minX + rad, y: r.maxY - rad), radius: rad, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        p.closeSubpath()
        return p
    }
}

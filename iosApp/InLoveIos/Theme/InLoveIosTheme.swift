import SwiftUI

/// Design tokens matching DESIGN.md (Dreamy Romantic Rose & Warm Pearl Velvet, WCAG AAA).
public enum InLoveColors {
    public static let softBlush = Color(hex: 0xFFF5F8)
    public static let pearl = Color(hex: 0xFFFFFF)
    public static let surfaceContainerLow = Color(hex: 0xFFF0F5)
    public static let surfaceContainer = Color(hex: 0xFFE6F1)
    public static let surfaceContainerHigh = Color(hex: 0xFFFFDCED)

    public static let rose = Color(hex: 0xE91E63)
    public static let primaryContainer = Color(hex: 0xFF2D75)
    public static let secondaryRose = Color(hex: 0xFF4081)
    public static let hotPink = Color(hex: 0xFF1493)
    public static let roseGradientEnd = Color(hex: 0xFF659A)

    /// WCAG AAA (~15:1 on white)
    public static let deepPlum = Color(hex: 0x1F0416)
    /// WCAG AAA (~9.2:1 on white)
    public static let subtlePlum = Color(hex: 0x4A1934)
    public static let cardBorder = Color(hex: 0xFFC6DB)
    public static let outline = Color(hex: 0x9E4774)

    public static let vipGold = Color(hex: 0xFFB300)
    public static let vipGoldContainer = Color(hex: 0xFFF8E1)
    public static let vipGoldBorder = Color(hex: 0xFFD54F)
    public static let vipGoldText = Color(hex: 0xB78103)

    public static let emeraldCheck = Color(hex: 0x16803C)
    public static let warningAmber = Color(hex: 0x9A4D00)
    public static let urgentCrimson = Color(hex: 0xB71C1C)

    public static var roseGradient: LinearGradient {
        LinearGradient(
            colors: [primaryContainer, rose, roseGradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

public enum InLoveMetrics {
    public static let minTapTarget: CGFloat = 48
    public static let cardCornerRadius: CGFloat = 20
    public static let pillCornerRadius: CGFloat = 50
    public static let buttonCornerRadius: CGFloat = 16
    public static let targetGap: CGFloat = 8
    public static let screenMargin: CGFloat = 16
    public static let sectionGap: CGFloat = 16
    public static let cardPadding: CGFloat = 16
    public static let innerKeyline: CGFloat = screenMargin + cardPadding
}

/// Romantic typography pairing Apple New York Serif for display/headings/reflections
/// with SF Pro Dynamic Type + monospaced digits for dates, counters, and metrics.
public enum InLoveTypography {
    public static let heroDisplay = Font.system(size: 52, weight: .bold, design: .serif)
    public static let paywallDisplay = Font.system(.largeTitle, design: .serif).weight(.semibold)
    public static let serifTitle = Font.system(.title2, design: .serif).weight(.semibold)
    public static let serifHeadline = Font.system(.headline, design: .serif).weight(.semibold)
    public static let serifReflection = Font.system(.body, design: .serif).italic()
    public static let metricNumber = Font.system(.headline, design: .default).weight(.semibold).monospacedDigit()
    public static let metadataCaption = Font.footnote.monospacedDigit()
}

/// Locale-aware display formatter for ISO dates (`yyyy-MM-dd` -> `dd/MM/yyyy` in VI, `MMM d, yyyy` in EN).
public enum InLoveDateFormatting {
    private static let enMonths = [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    ]

    public static func displayDate(iso: String, isEnglish: Bool) -> String {
        let parts = iso.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]),
              let month = Int(parts[1]),
              let day = Int(parts[2]),
              (1...12).contains(month),
              (1...31).contains(day) else {
            return iso
        }
        if isEnglish {
            return "\(enMonths[month - 1]) \(day), \(year)"
        }
        return String(format: "%02d/%02d/%04d", day, month, year)
    }
}

public struct InLoveCardModifier: ViewModifier {
    var background: Color = InLoveColors.pearl
    var borderColor: Color = InLoveColors.cardBorder
    var borderWidth: CGFloat = 1.0
    var cornerRadius: CGFloat = InLoveMetrics.cardCornerRadius

    public func body(content: Content) -> some View {
        content
            .padding(InLoveMetrics.cardPadding)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: borderWidth)
            )
            .shadow(color: InLoveColors.deepPlum.opacity(0.04), radius: 8, x: 0, y: 2)
    }
}

public struct InLoveTapTargetModifier: ViewModifier {
    public func body(content: Content) -> some View {
        content
            .frame(minWidth: InLoveMetrics.minTapTarget, minHeight: InLoveMetrics.minTapTarget)
            .contentShape(Rectangle())
    }
}

public extension View {
    func inLoveCard(
        background: Color = InLoveColors.pearl,
        borderColor: Color = InLoveColors.cardBorder,
        borderWidth: CGFloat = 1.0,
        cornerRadius: CGFloat = InLoveMetrics.cardCornerRadius
    ) -> some View {
        modifier(
            InLoveCardModifier(
                background: background,
                borderColor: borderColor,
                borderWidth: borderWidth,
                cornerRadius: cornerRadius
            )
        )
    }

    func inLoveTapTarget() -> some View {
        modifier(InLoveTapTargetModifier())
    }
}

public struct InLovePrimaryButtonStyle: ButtonStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(InLoveColors.pearl)
            .frame(maxWidth: .infinity, minHeight: InLoveMetrics.minTapTarget)
            .padding(.horizontal, 16)
            .background(
                RoundedRectangle(cornerRadius: InLoveMetrics.buttonCornerRadius, style: .continuous)
                    .fill(InLoveColors.rose)
            )
            .scaleEffect(configuration.isPressed ? 0.985 : 1.0)
            .opacity(configuration.isPressed ? 0.9 : 1.0)
            .animation(.easeOut(duration: 0.16), value: configuration.isPressed)
    }
}

public struct InLoveSecondaryButtonStyle: ButtonStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.medium))
            .foregroundStyle(InLoveColors.deepPlum)
            .frame(minHeight: InLoveMetrics.minTapTarget)
            .padding(.horizontal, 16)
            .background(
                RoundedRectangle(cornerRadius: InLoveMetrics.buttonCornerRadius, style: .continuous)
                    .fill(InLoveColors.surfaceContainerLow)
            )
            .overlay(
                RoundedRectangle(cornerRadius: InLoveMetrics.buttonCornerRadius, style: .continuous)
                    .stroke(InLoveColors.cardBorder, lineWidth: 1.0)
            )
            .scaleEffect(configuration.isPressed ? 0.985 : 1.0)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

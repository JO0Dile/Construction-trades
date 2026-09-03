import SwiftUI

/// The same site-safety palette as the Android theme: high-visibility amber on
/// slate, chosen to stay legible in direct sun and through a scratched screen
/// protector, and distinct from the reds and greens used for stock and
/// checklist states.
enum Brand {
    static let amber = Color(red: 0.949, green: 0.718, blue: 0.020)
    static let slate = Color(red: 0.122, green: 0.161, blue: 0.200)

    static func accent(for scheme: ColorScheme) -> Color {
        scheme == .dark ? amber : slate
    }
}

extension View {
    /// Larger text is a first-class setting here, not an accessibility
    /// afterthought: this app is read at arm's length, in gloves, on a
    /// scaffold. Applied at the root so every screen scales together.
    func tradesTypography(largeText: Bool) -> some View {
        dynamicTypeSize(largeText ? .accessibility1 : .large)
    }
}

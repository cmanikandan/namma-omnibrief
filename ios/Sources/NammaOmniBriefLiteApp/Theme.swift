import SwiftUI

/// Bengaluru editorial light theme matching the Android app's warm paper & terracotta palette.
public enum OmniTheme {
    public static let paperBackground = Color(red: 0.980, green: 0.969, blue: 0.949) // #FAF7F2
    public static let cardSurface = Color.white
    public static let subtleSurface = Color(red: 0.949, green: 0.933, blue: 0.902) // #F2EEE6
    public static let borderWarm = Color(red: 0.886, green: 0.859, blue: 0.812) // #E2DBD0
    public static let primaryTerracotta = Color(red: 0.722, green: 0.259, blue: 0.129) // #B84221
    public static let mysoreGold = Color(red: 0.776, green: 0.545, blue: 0.173) // #C68B2C
    public static let deepInk = Color(red: 0.114, green: 0.102, blue: 0.086) // #1D1A16
    public static let mutedSlate = Color(red: 0.396, green: 0.369, blue: 0.329) // #655E54
    public static let forestGreen = Color(red: 0.106, green: 0.431, blue: 0.220) // #1B6E38
    public static let errorCrimson = Color(red: 0.729, green: 0.145, blue: 0.145)
}

public enum LiteDestination: String, CaseIterable, Identifiable {
    case today = "Today"
    case articleToX = "X Drafter"
    case archive = "Archive"
    case settings = "Settings"

    public var id: String { rawValue }

    public var systemIcon: String {
        switch self {
        case .today: return "newspaper.fill"
        case .articleToX: return "doc.text.image.fill"
        case .archive: return "tray.full.fill"
        case .settings: return "slider.horizontal.3"
        }
    }
}

public extension View {
    func omniScaledFont(size: CGFloat, weight: Font.Weight = .regular, scale: Double) -> some View {
        self.font(.system(size: round(size * CGFloat(scale)), weight: weight))
    }
}

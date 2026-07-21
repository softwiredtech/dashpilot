import SwiftUI

/// Shared dark palette ported from the Android app's `Color.kt`
/// (`AccentColor` + `DarkColors`).
extension Color {
    /// #5CBD68 — accent green (Android `AccentColor`).
    static let dashAccent = Color(red: 0x5C / 255.0, green: 0xBD / 255.0, blue: 0x68 / 255.0)
    /// #0D0D0D — screen background (Android `DarkColors.Background`).
    static let dashBackground = Color(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0)
    /// #1A1A1A — card/button surface (Android `DarkColors.Surface`).
    static let dashSurface = Color(red: 0x1A / 255.0, green: 0x1A / 255.0, blue: 0x1A / 255.0)
    /// #2A2A2A — selected surface (Android `DarkColors.SurfaceSelected`).
    static let dashSurfaceSelected = Color(red: 0x2A / 255.0, green: 0x2A / 255.0, blue: 0x2A / 255.0)
    /// #333333 — hairline borders (Android `DarkColors.Border`).
    static let dashBorder = Color(red: 0x33 / 255.0, green: 0x33 / 255.0, blue: 0x33 / 255.0)
    /// #555555 — disabled elements (Android `DarkColors.Disabled`).
    static let dashDisabled = Color(red: 0x55 / 255.0, green: 0x55 / 255.0, blue: 0x55 / 255.0)
    /// #666666 — subtle text (Android `DarkColors.TextSubtle`).
    static let dashTextSubtle = Color(red: 0x66 / 255.0, green: 0x66 / 255.0, blue: 0x66 / 255.0)
    /// #888888 — muted text (Android `DarkColors.TextMuted`).
    static let dashTextMuted = Color(red: 0x88 / 255.0, green: 0x88 / 255.0, blue: 0x88 / 255.0)
    /// #AAAAAA — disabled content (Android `DarkColors.ContentDisabled`).
    static let dashContentDisabled = Color(red: 0xAA / 255.0, green: 0xAA / 255.0, blue: 0xAA / 255.0)
    /// #FF5252 — error red (Android `DarkColors.Error`).
    static let dashError = Color(red: 0xFF / 255.0, green: 0x52 / 255.0, blue: 0x52 / 255.0)
}

/// Spacing and shape constants shared by the home/menu screens
/// (mirrors the dp values used across the Android UI).
enum DashMetrics {
    /// Horizontal screen edge padding.
    static let screenPadding: CGFloat = 24
    /// Standard corner radius for cards and buttons.
    static let corner: CGFloat = 16
    /// Smaller corner radius for compact elements.
    static let smallCorner: CGFloat = 10
    /// Standard control button height.
    static let buttonHeight: CGFloat = 56
    /// Gap between grid items.
    static let gridGap: CGFloat = 16
}

import Foundation

/// Manages user settings, API keys, model selection, and text scale for Namma Omnibrief Lite.
///
/// Follows the strict Secrets Policy in `AGENTS.md`:
/// - Zero credentials are hardcoded in source.
/// - Keys resolve from on-device `UserDefaults` (or process environment variables when running in Xcode).
public final class AppPreferencesStore: @unchecked Sendable {

    public static let defaultModel = "gemini-3.8-flash"
    public static let defaultFontScale: Double = 1.15
    public static let maxArticleImages = 10
    public static let xStandardCharLimit = 280
    public static let xPremiumCharLimit = 25000
    public static let autoDetectSource = "Auto-Detect"

    public static let availableModels: [(id: String, label: String)] = [
        ("gemini-3.8-flash", "Gemini Flash 3.8 (High Context)"),
        ("gemini-3.7-flash", "Gemini Flash 3.7"),
        ("gemini-3.6-flash", "Gemini Flash 3.6"),
        ("gemini-3.5-flash", "Gemini Flash 3.5 (Standard)"),
        ("gemini-2.5-flash", "Gemini Flash 2.5 (Fallback)")
    ]

    public static let fontScaleOptions: [(scale: Double, label: String)] = [
        (0.90, "Compact"),
        (1.00, "Standard"),
        (1.15, "Comfortable"),
        (1.30, "Large"),
        (1.50, "Extra Large")
    ]

    public static let knownPublications: [String] = [
        "Auto-Detect",
        "The Economic Times",
        "The Times of India",
        "The Hindu",
        "Deccan Herald",
        "Mint",
        "Financial Times",
        "The Wall Street Journal",
        "The New York Times",
        "Bloomberg",
        "Reuters"
    ]

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private func envFallback(_ key: String, placeholder: String) -> String {
        let val = ProcessInfo.processInfo.environment[key]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return (!val.isEmpty && val != placeholder) ? val : ""
    }

    public var geminiApiKey: String {
        get {
            let saved = defaults.string(forKey: "gemini_api_key")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !saved.isEmpty { return saved }
            return envFallback("GEMINI_API_KEY", placeholder: "MY_GEMINI_API_KEY")
        }
        set {
            defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "gemini_api_key")
        }
    }

    public var geminiModel: String {
        get { defaults.string(forKey: "gemini_model") ?? Self.defaultModel }
        set { defaults.set(newValue, forKey: "gemini_model") }
    }

    public var xClientId: String {
        get { defaults.string(forKey: "x_client_id")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_client_id") }
    }

    public var xClientSecret: String {
        get { defaults.string(forKey: "x_client_secret")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_client_secret") }
    }

    public var xAccessToken: String {
        get { defaults.string(forKey: "x_access_token")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
        set {
            defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_access_token")
            xTokenExpiresAtMillis = 0
        }
    }

    public var xRefreshToken: String {
        get { defaults.string(forKey: "x_refresh_token")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_refresh_token") }
    }

    public var xTokenExpiresAtMillis: Int64 {
        get { Int64(defaults.integer(forKey: "x_token_expires_at")) }
        set { defaults.set(Int(newValue), forKey: "x_token_expires_at") }
    }

    /// Saves rotated OAuth 2.0 tokens in a deterministic order so setting `xAccessToken` does not
    /// wipe out the newly computed expiration timestamp.
    public func saveRefreshedTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Int64,
        nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        defaults.set(accessToken.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_access_token")
        if !refreshToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            defaults.set(refreshToken.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "x_refresh_token")
        }
        let expiry = expiresInSeconds > 0 ? nowMillis + (expiresInSeconds * 1000) : 0
        defaults.set(Int(expiry), forKey: "x_token_expires_at")
    }

    public var hasXCredentials: Bool {
        !xAccessToken.isEmpty || (!xClientId.isEmpty && !xRefreshToken.isEmpty)
    }

    public var isXBlue: Bool {
        get {
            if defaults.object(forKey: "is_x_blue") == nil { return true }
            return defaults.bool(forKey: "is_x_blue")
        }
        set { defaults.set(newValue, forKey: "is_x_blue") }
    }

    public var attachImageToPost: Bool {
        get {
            if defaults.object(forKey: "attach_image_to_post") == nil { return true }
            return defaults.bool(forKey: "attach_image_to_post")
        }
        set { defaults.set(newValue, forKey: "attach_image_to_post") }
    }

    public var defaultSource: String {
        get { defaults.string(forKey: "default_source") ?? Self.autoDetectSource }
        set { defaults.set(newValue, forKey: "default_source") }
    }

    public var fontScale: Double {
        get {
            guard defaults.object(forKey: "font_scale") != nil else { return Self.defaultFontScale }
            let raw = defaults.double(forKey: "font_scale")
            return min(max(raw, 0.85), 1.60)
        }
        set {
            defaults.set(min(max(newValue, 0.85), 1.60), forKey: "font_scale")
        }
    }

    public var headlineSort: HeadlineSort {
        get { HeadlineSort.fromId(defaults.string(forKey: "headline_sort")) }
        set { defaults.set(newValue.id, forKey: "headline_sort") }
    }

    /// Parses `.env` formatted key=value lines pasted into Settings -> Bulk Import.
    /// Returns the number of recognized keys imported.
    @discardableResult
    public func importEnvBlock(_ block: String) -> Int {
        var imported = 0
        for rawLine in block.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty || line.hasPrefix("#") { continue }
            guard let eqIndex = line.firstIndex(of: "=") else { continue }
            let key = String(line[..<eqIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
            var value = String(line[line.index(after: eqIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
            if (value.hasPrefix("\"") && value.hasSuffix("\"")) || (value.hasPrefix("'") && value.hasSuffix("'")) {
                value = String(value.dropFirst().dropLast())
            }
            guard !value.isEmpty else { continue }

            switch key.uppercased() {
            case "GEMINI_API_KEY":
                geminiApiKey = value; imported += 1
            case "X_CLIENT_ID":
                xClientId = value; imported += 1
            case "X_CLIENT_SECRET":
                xClientSecret = value; imported += 1
            case "X_ACCESS_TOKEN":
                xAccessToken = value; imported += 1
            case "X_REFRESH_TOKEN":
                xRefreshToken = value; imported += 1
            default:
                break
            }
        }
        return imported
    }
}

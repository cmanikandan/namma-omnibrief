import Foundation

public enum XPostOutcome: Equatable, Sendable {
    case success(tweetId: String, text: String)
    case failure(message: String)
}

public struct TokenRefreshOutcome: Equatable, Sendable {
    public let success: Bool
    public let accessToken: String
    public let refreshToken: String
    public let expiresIn: Int64
    public let error: String?

    public init(
        success: Bool,
        accessToken: String = "",
        refreshToken: String = "",
        expiresIn: Int64 = 0,
        error: String? = nil
    ) {
        self.success = success
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresIn = expiresIn
        self.error = error
    }
}

/// Handles OAuth 2.0 User Context token refresh, media upload (`/2/media/upload`),
/// publishing (`/2/tweets`), and the keyless fallback X web intent URL (`https://x.com/intent/tweet`).
public final class XClient: Sendable {

    /// Renew 60 seconds before expiry so an in-flight upload never races token expiration.
    public static let expiryBufferMillis: Int64 = 60_000

    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    /// Pure check for whether the access token should be refreshed before attempting a request.
    ///
    /// - When `accessToken` is blank, a refresh is required.
    /// - When `expiresAtMillis` is 0 (a hand-pasted token with unknown expiry), returns `false` so
    ///   the token is tried first and refreshed on 401.
    /// - Otherwise returns `true` when `nowMillis + 60_000 >= expiresAtMillis`.
    public static func needsRefresh(
        accessToken: String,
        expiresAtMillis: Int64,
        nowMillis: Int64
    ) -> Bool {
        if accessToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return true
        }
        if expiresAtMillis <= 0 {
            return false
        }
        return (nowMillis + expiryBufferMillis) >= expiresAtMillis
    }

    /// Builds the direct X composer web intent URL so the user can post without OAuth credentials.
    public static func directComposerURL(for text: String) -> URL? {
        var components = URLComponents(string: "https://x.com/intent/tweet")
        components?.queryItems = [URLQueryItem(name: "text", value: text)]
        return components?.url
    }

    public func refreshOAuth2Token(
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ) async -> TokenRefreshOutcome {
        let cleanClient = clientId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanRefresh = refreshToken.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanClient.isEmpty || cleanRefresh.isEmpty {
            return TokenRefreshOutcome(success: false, error: "Client ID and Refresh Token are required.")
        }

        guard let url = URL(string: "https://api.twitter.com/2/oauth2/token") else {
            return TokenRefreshOutcome(success: false, error: "Invalid token URL.")
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 30
        let basicRaw = "\(cleanClient):\(clientSecret.trimmingCharacters(in: .whitespacesAndNewlines))"
        let basicBase64 = Data(basicRaw.utf8).base64EncodedString()
        req.setValue("Basic \(basicBase64)", forHTTPHeaderField: "Authorization")
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        var comps = URLComponents()
        comps.queryItems = [
            URLQueryItem(name: "grant_type", value: "refresh_token"),
            URLQueryItem(name: "refresh_token", value: cleanRefresh),
            URLQueryItem(name: "client_id", value: cleanClient)
        ]
        req.httpBody = comps.percentEncodedQuery?.data(using: .utf8)

        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                return TokenRefreshOutcome(success: false, error: "No HTTP response")
            }
            if (200..<300).contains(http.statusCode),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let newAccess = (json["access_token"] as? String) ?? ""
                let newRefresh = (json["refresh_token"] as? String) ?? cleanRefresh
                let expiresIn = Int64((json["expires_in"] as? Int) ?? 7200)
                return TokenRefreshOutcome(
                    success: !newAccess.isEmpty,
                    accessToken: newAccess,
                    refreshToken: newRefresh,
                    expiresIn: expiresIn
                )
            } else {
                let raw = String(data: data, encoding: .utf8) ?? ""
                return TokenRefreshOutcome(success: false, error: "HTTP \(http.statusCode): \(raw)")
            }
        } catch {
            return TokenRefreshOutcome(success: false, error: error.localizedDescription)
        }
    }

    public func uploadImage(accessToken: String, jpegData: Data) async -> Result<String, Error> {
        guard let url = URL(string: "https://api.x.com/2/media/upload") else {
            return .failure(URLError(.badURL))
        }
        let boundary = "Boundary-\(UUID().uuidString)"
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 45
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"media_category\"\r\n\r\n".data(using: .utf8)!)
        body.append("tweet_image\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"media\"; filename=\"image.jpg\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(jpegData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                return .failure(URLError(.badServerResponse))
            }
            if (200..<300).contains(http.statusCode),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let nestedId = (json["data"] as? [String: Any])?["id"] as? String
                let flatId = json["media_id_string"] as? String
                let mediaId = (nestedId?.isEmpty == false ? nestedId : flatId) ?? ""
                if !mediaId.isEmpty {
                    return .success(mediaId)
                }
            }
            if http.statusCode == 403 {
                let msg = "X rejected the image upload (HTTP 403 Forbidden). Your OAuth 2.0 token is missing the 'media.write' scope. Re-authorise using tools/x_oauth_setup.py or disable 'Attach source image to X posts' in Settings."
                return .failure(NSError(domain: "XClient", code: 403, userInfo: [NSLocalizedDescriptionKey: msg]))
            }
            let raw = String(data: data, encoding: .utf8) ?? ""
            return .failure(NSError(domain: "XClient", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "Upload failed (HTTP \(http.statusCode)): \(raw)"]))
        } catch {
            return .failure(error)
        }
    }

    public func postTweet(
        accessToken: String,
        text: String,
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        accessTokenExpiresAtMillis: Int64,
        imageJpegData: Data?,
        onTokenRefreshed: (@Sendable (String, String, Int64) -> Void)? = nil
    ) async -> XPostOutcome {
        var currentToken = accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        let canRefresh = !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !refreshToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)

        if canRefresh && Self.needsRefresh(accessToken: currentToken, expiresAtMillis: accessTokenExpiresAtMillis, nowMillis: nowMillis) {
            let refreshed = await refreshOAuth2Token(clientId: clientId, clientSecret: clientSecret, refreshToken: refreshToken)
            if refreshed.success {
                currentToken = refreshed.accessToken
                onTokenRefreshed?(refreshed.accessToken, refreshed.refreshToken, refreshed.expiresIn)
            } else if currentToken.isEmpty {
                return .failure(message: "Could not obtain X access token: \(refreshed.error ?? "Unknown error")")
            }
        }

        guard !currentToken.isEmpty else {
            return .failure(message: "No X OAuth 2.0 Access Token configured. Add tokens in Settings or tap 'Open in X App' to post manually.")
        }

        var mediaIds: [String] = []
        if let jpegData = imageJpegData {
            switch await uploadImage(accessToken: currentToken, jpegData: jpegData) {
            case .success(let id):
                mediaIds.append(id)
            case .failure(let err):
                return .failure(message: err.localizedDescription)
            }
        }

        let firstAttempt = await executePost(accessToken: currentToken, text: text, mediaIds: mediaIds)
        if case .failure(let msg) = firstAttempt,
           msg.localizedCaseInsensitiveContains("Unauthorized"),
           canRefresh {
            let refreshed = await refreshOAuth2Token(clientId: clientId, clientSecret: clientSecret, refreshToken: refreshToken)
            if refreshed.success {
                currentToken = refreshed.accessToken
                onTokenRefreshed?(refreshed.accessToken, refreshed.refreshToken, refreshed.expiresIn)
                return await executePost(accessToken: currentToken, text: text, mediaIds: mediaIds)
            } else {
                return .failure(message: "X session expired and token refresh failed. Re-run tools/x_oauth_setup.py and paste the new tokens into Settings.")
            }
        }

        return firstAttempt
    }

    private func executePost(accessToken: String, text: String, mediaIds: [String]) async -> XPostOutcome {
        guard let url = URL(string: "https://api.twitter.com/2/tweets") else {
            return .failure(message: "Invalid X API URL.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 30
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")

        var payload: [String: Any] = ["text": text]
        if !mediaIds.isEmpty {
            payload["media"] = ["media_ids": mediaIds]
        }
        req.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                return .failure(message: "No HTTP response from X.")
            }
            if (200..<300).contains(http.statusCode),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let dataObj = json["data"] as? [String: Any],
               let tweetId = dataObj["id"] as? String {
                return .success(tweetId: tweetId, text: text)
            } else if http.statusCode == 401 {
                return .failure(message: "HTTP 401 Unauthorized")
            } else {
                let raw = String(data: data, encoding: .utf8) ?? ""
                return .failure(message: "X API rejected post (HTTP \(http.statusCode)): \(raw)")
            }
        } catch {
            return .failure(message: error.localizedDescription)
        }
    }
}

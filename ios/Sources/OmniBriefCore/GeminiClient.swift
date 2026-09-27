import Foundation

/// Calls the Gemini v1beta REST API (`generateContent`) using the 8 hard grounding rules from `AGENTS.md`.
public final class GeminiClient: Sendable {

    /// Ordered fallback chain used when the selected model is rate-limited or unavailable.
    public static let fallbackModels = [
        "gemini-3.8-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    ]

    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    /// Builds the exact 8-rule grounded prompt for Article -> X analysis.
    public static func buildArticlePrompt(
        textInput: String,
        hasImages: Bool,
        specifiedSource: String?,
        isXBlue: Bool
    ) -> String {
        var prompt = """
        You are an executive intelligence analyst who converts a SINGLE news article into an authoritative, professional post for X (formerly Twitter).

        NON-NEGOTIABLE ANALYSIS RULES:
        1. SINGLE MAIN TOPIC ONLY: A photographed newspaper or screenshot often captures neighbouring columns, adjacent headlines, advertisements, teasers, page furniture, or partially visible unrelated stories. Identify the ONE dominant article (largest headline, largest area, the story the photo is clearly centred on) and analyse ONLY that story. Completely ignore every peripheral or inadvertently captured item.
        2. STRICTLY GROUNDED: Use ONLY the content that is actually present in the supplied image/text. Do NOT use outside knowledge, do NOT infer events beyond the text, and do NOT add background the source does not state.
        3. NO EDITORIALISING: Report what the article says. No opinions, no praise, no criticism, no predictions, no 'this signals...', no calls to action, no rhetorical questions. Neutral, factual, third-person reporting voice.
        4. If a figure, name, date or quote is illegible or absent, omit it rather than guessing. Never fabricate numbers. Do NOT promote an incidental mention into a claim: a product, company or person that appears only inside someone's job title, a photo caption, a quoted aside, an example or a comparison is NOT thereby part of the main subject's actions, plans or priorities. Attribute something to the subject only where the article explicitly does so.
        5. Tone: Professional, factual, objective, high signal-to-noise. NO emojis whatsoever.
        6. Source Identification: Determine the source publication (e.g. New York Times, Wall Street Journal, Financial Times, Times of India, Economic Times, The Hindu, Deccan Herald, Bloomberg, Reuters, etc.). 
        """

        if let specifiedSource = specifiedSource?.trimmingCharacters(in: .whitespacesAndNewlines),
           !specifiedSource.isEmpty,
           specifiedSource != AppPreferencesStore.autoDetectSource {
            prompt += "The user has specified the source as: \"\(specifiedSource)\". Use this source.\n"
        } else {
            prompt += "Read any visible masthead, dateline, byline, page furniture or URL in the image/text to identify the exact publisher. If it cannot be determined from the content, return \"Unknown\" and set sourceDetectedAutomatically to false.\n"
        }

        if isXBlue {
            prompt += "7. Format: The author has an X Premium/Blue account, so write a crisp executive analysis (roughly 500-1200 characters) covering the core thesis, the verified figures/facts stated in the article, the stated implications, and exact source attribution (e.g. 'Source: Financial Times').\n"
        } else {
            prompt += "7. Format: Standard X post. The ENTIRE postDraft, including attribution and every hashtag, MUST be at most 260 characters. Aim for 200-250 characters to leave a safety margin; a post over 280 characters is rejected by X outright. Count carefully and shorten the wording rather than dropping the source attribution.\n"
        }

        prompt += """
        8. Include 2 to 4 precise, professional tags derived from the article subject (e.g. #Economy, #Markets, #TechPolicy).

        Return your response as a strict JSON object (no markdown fences) with these keys:
        {
          "detectedSource": "Name of publication or 'Unknown'",
          "sourceDetectedAutomatically": true,
          "mainTopic": "One line naming the single dominant story you analysed",
          "headline": "Concise headline of that dominant story",
          "postDraft": "The complete ready-to-publish X post content with attribution and hashtags",
          "hashtags": ["#Tag1", "#Tag2"],
          "fullSummary": "Factual bullet-point summary of the dominant story only"
        }
        """

        let trimmedText = textInput.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedText.isEmpty {
            prompt += "\nARTICLE TEXT/CONTENT:\n\(trimmedText)\n"
        }
        if hasImages {
            prompt += "\nThe attached image is a photograph/scan of the article. Apply rule 1 rigorously.\n"
        }
        return prompt
    }

    public func analyzeArticle(
        apiKey: String,
        modelName: String,
        textInput: String,
        imageJpegBuffers: [Data],
        specifiedSource: String?,
        isXBlue: Bool
    ) async throws -> ArticleAnalysisResult {
        let cleanKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanKey.isEmpty else {
            throw NSError(
                domain: "GeminiClient",
                code: 401,
                userInfo: [NSLocalizedDescriptionKey: "Gemini API key is not configured. Please set your key in Settings."]
            )
        }

        let prompt = Self.buildArticlePrompt(
            textInput: textInput,
            hasImages: !imageJpegBuffers.isEmpty,
            specifiedSource: specifiedSource,
            isXBlue: isXBlue
        )

        var parts: [[String: Any]] = [["text": prompt]]
        for jpegData in imageJpegBuffers {
            parts.append([
                "inlineData": [
                    "mimeType": "image/jpeg",
                    "data": jpegData.base64EncodedString()
                ]
            ])
        }

        let payload: [String: Any] = [
            "contents": [
                ["parts": parts]
            ]
        ]
        let bodyData = try JSONSerialization.data(withJSONObject: payload)

        let rawResponse = try await callWithFallbacks(
            apiKey: cleanKey,
            preferredModel: modelName,
            requestBody: bodyData
        )
        return try Self.parseArticleResult(from: rawResponse)
    }

    private func callWithFallbacks(
        apiKey: String,
        preferredModel: String,
        requestBody: Data
    ) async throws -> Data {
        var chain = [preferredModel]
        for fallback in Self.fallbackModels where !chain.contains(fallback) {
            chain.append(fallback)
        }

        var lastError: Error?
        for model in chain {
            do {
                return try await callSingleModel(apiKey: apiKey, model: model, requestBody: requestBody)
            } catch {
                lastError = error
            }
        }
        throw lastError ?? NSError(domain: "GeminiClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "No Gemini model available"])
    }

    private func callSingleModel(apiKey: String, model: String, requestBody: Data) async throws -> Data {
        let cleanModel = model.hasPrefix("models/") ? String(model.dropFirst(7)) : model
        let urlStr = "https://generativelanguage.googleapis.com/v1beta/models/\(cleanModel):generateContent?key=\(apiKey)"
        guard let url = URL(string: urlStr) else {
            throw URLError(.badURL)
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 90
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = requestBody

        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        if !(200..<300).contains(http.statusCode) {
            if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let errObj = root["error"] as? [String: Any],
               let msg = errObj["message"] as? String {
                throw NSError(domain: "GeminiClient", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: msg])
            }
            let rawText = String(data: data, encoding: .utf8) ?? ""
            throw NSError(domain: "GeminiClient", code: http.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(http.statusCode): \(rawText)"])
        }

        return data
    }

    public static func parseArticleResult(from data: Data) throws -> ArticleAnalysisResult {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]],
              let first = candidates.first,
              let content = first["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]],
              let text = parts.first?["text"] as? String else {
            throw NSError(domain: "GeminiClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "Gemini returned an empty response."])
        }

        if let jsonSub = extractJsonSubstring(from: text),
           let jsonData = jsonSub.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
            let detectedSource = (obj["detectedSource"] as? String) ?? "Unknown"
            let sourceDetected = (obj["sourceDetectedAutomatically"] as? Bool)
                ?? (detectedSource != "Unknown" && !detectedSource.isEmpty)
            let headline = (obj["headline"] as? String) ?? "Article Summary"
            let postDraft = (obj["postDraft"] as? String) ?? text
            let hashtags = (obj["hashtags"] as? [String]) ?? []
            let fullSummary = (obj["fullSummary"] as? String) ?? postDraft
            let mainTopic = (obj["mainTopic"] as? String) ?? ""

            return ArticleAnalysisResult(
                detectedSource: detectedSource,
                sourceDetectedAutomatically: sourceDetected,
                headline: headline,
                postDraft: postDraft,
                hashtags: hashtags,
                fullSummary: fullSummary,
                mainTopic: mainTopic
            )
        }

        return ArticleAnalysisResult(
            detectedSource: "Unknown",
            sourceDetectedAutomatically: false,
            headline: "Article Summary",
            postDraft: text.trimmingCharacters(in: .whitespacesAndNewlines),
            hashtags: [],
            fullSummary: text.trimmingCharacters(in: .whitespacesAndNewlines),
            mainTopic: ""
        )
    }

    public static func buildConferencePrompt(
        conferenceTitle: String,
        sessionTopic: String,
        speakerName: String,
        photoCount: Int,
        hasAudio: Bool
    ) -> String {
        var prompt = """
        You are an executive conference intelligence scribe using high-context multimodal reasoning to synthesize a conference session into a comprehensive, boardroom-ready report.

        SESSION METADATA:
        Conference: \(conferenceTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Conference Session" : conferenceTitle)
        Topic: \(sessionTopic.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Executive Presentation" : sessionTopic)
        Speaker: \(speakerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Keynote Speaker" : speakerName)

        INPUTS PROVIDED:
        - Presentation slide photos: \(photoCount) photos captured.
        """
        if hasAudio {
            prompt += "\n- Live session audio recording attached.\n"
        }
        prompt += """

        TASK:
        Thoroughly analyze all presentation slides (diagrams, bullet points, charts, statistics) and synthesize them with the spoken session arguments into a detailed briefing.
        Return your output as a strict JSON object (no markdown fences) with keys:
        {
          "sessionTitle": "Clean title for the session",
          "speaker": "Speaker name",
          "executiveSummary": "2-3 paragraph executive summary of the session",
          "keyTakeaways": ["takeaway 1", "takeaway 2", "takeaway 3", "takeaway 4"],
          "slideInsights": ["Insight from slide/diagram 1", "Key data points from presentation"],
          "actionItems": ["Strategic recommendation 1", "Action item 2"],
          "fullReportMarkdown": "A fully formatted, beautiful Markdown report with headers (##), bold text, bullet points, ready to export or email."
        }
        """
        return prompt
    }

    public func generateConferenceReport(
        apiKey: String,
        modelName: String,
        conferenceTitle: String,
        sessionTopic: String,
        speakerName: String,
        photoJpegBuffers: [Data],
        audioData: Data?,
        audioMimeType: String = "audio/mp4"
    ) async throws -> ConferenceReportResult {
        let cleanKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanKey.isEmpty else {
            throw NSError(
                domain: "GeminiClient",
                code: 401,
                userInfo: [NSLocalizedDescriptionKey: "Gemini API key is not configured. Please set your key in Settings."]
            )
        }

        let prompt = Self.buildConferencePrompt(
            conferenceTitle: conferenceTitle,
            sessionTopic: sessionTopic,
            speakerName: speakerName,
            photoCount: photoJpegBuffers.count,
            hasAudio: audioData != nil && !audioData!.isEmpty
        )

        var parts: [[String: Any]] = [
            ["text": prompt]
        ]

        for jpeg in photoJpegBuffers {
            parts.append([
                "inlineData": [
                    "mimeType": "image/jpeg",
                    "data": jpeg.base64EncodedString()
                ]
            ])
        }

        if let audio = audioData, !audio.isEmpty {
            parts.append([
                "inlineData": [
                    "mimeType": audioMimeType,
                    "data": audio.base64EncodedString()
                ]
            ])
        }

        let requestPayload: [String: Any] = [
            "contents": [
                ["parts": parts]
            ]
        ]
        let requestBody = try JSONSerialization.data(withJSONObject: requestPayload)

        let responseData = try await callWithFallbacks(
            apiKey: cleanKey,
            preferredModel: modelName,
            requestBody: requestBody
        )

        return try Self.parseConferenceResult(
            from: responseData,
            defaultTopic: sessionTopic,
            defaultSpeaker: speakerName
        )
    }

    public static func parseConferenceResult(
        from data: Data,
        defaultTopic: String,
        defaultSpeaker: String
    ) throws -> ConferenceReportResult {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]],
              let first = candidates.first,
              let content = first["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]],
              let text = parts.first?["text"] as? String else {
            throw NSError(domain: "GeminiClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "Gemini returned an empty conference response."])
        }

        let fallbackTopic = defaultTopic.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Conference Intelligence Report" : defaultTopic
        let fallbackSpeaker = defaultSpeaker.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Keynote Speaker" : defaultSpeaker

        if let jsonSub = extractJsonSubstring(from: text),
           let jsonData = jsonSub.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
            let title = (obj["sessionTitle"] as? String) ?? fallbackTopic
            let speaker = (obj["speaker"] as? String) ?? fallbackSpeaker
            let execSummary = (obj["executiveSummary"] as? String) ?? ""
            let keyTakeaways = (obj["keyTakeaways"] as? [String]) ?? []
            let slideInsights = (obj["slideInsights"] as? [String]) ?? []
            let actionItems = (obj["actionItems"] as? [String]) ?? []
            let fullReport = (obj["fullReportMarkdown"] as? String) ?? text

            return ConferenceReportResult(
                sessionTitle: title,
                speaker: speaker,
                executiveSummary: execSummary,
                keyTakeaways: keyTakeaways,
                slideInsights: slideInsights,
                actionItems: actionItems,
                fullReportMarkdown: fullReport
            )
        }

        return ConferenceReportResult(
            sessionTitle: fallbackTopic,
            speaker: fallbackSpeaker,
            executiveSummary: text,
            keyTakeaways: ["Key takeaways synthesized from slides and presentation audio."],
            slideInsights: [],
            actionItems: [],
            fullReportMarkdown: text
        )
    }

    public static func extractJsonSubstring(from text: String) -> String? {
        guard let firstBrace = text.firstIndex(of: "{"),
              let lastBrace = text.lastIndex(of: "}"),
              firstBrace <= lastBrace else {
            return nil
        }
        return String(text[firstBrace...lastBrace])
    }
}

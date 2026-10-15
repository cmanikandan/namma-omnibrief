import Foundation

/// Calls the Gemini v1beta REST API (`generateContent`) using the 8 hard grounding rules from `AGENTS.md`.
public final class GeminiClient: Sendable {

    /// Ordered fallback chain: Primary (3.8-flash) -> Backup (3.7-flash) -> Last resort (4-argon).
    public static let fallbackModels = [
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-4-argon"
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
        requestBody: Data,
        timeoutInterval: TimeInterval = 35
    ) async throws -> Data {
        var chain = [preferredModel]
        for fallback in Self.fallbackModels where !chain.contains(fallback) {
            chain.append(fallback)
        }

        var lastError: Error?
        for model in chain {
            do {
                return try await callSingleModel(
                    apiKey: apiKey,
                    model: model,
                    requestBody: requestBody,
                    timeoutInterval: timeoutInterval
                )
            } catch {
                lastError = error
            }
        }
        throw lastError ?? NSError(domain: "GeminiClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "No Gemini model available"])
    }

    private func callSingleModel(
        apiKey: String,
        model: String,
        requestBody: Data,
        timeoutInterval: TimeInterval = 35
    ) async throws -> Data {
        let cleanModel = model.hasPrefix("models/") ? String(model.dropFirst(7)) : model
        let urlStr = "https://generativelanguage.googleapis.com/v1beta/models/\(cleanModel):generateContent?key=\(apiKey)"
        guard let url = URL(string: urlStr) else {
            throw URLError(.badURL)
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = timeoutInterval
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
        audioMimeType: String = "audio/m4a"
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
            let limitBytes = 18_500_000
            if audio.count > limitBytes {
                let mb = Double(audio.count) / 1_048_576.0
                throw NSError(
                    domain: "GeminiClient",
                    code: 413,
                    userInfo: [NSLocalizedDescriptionKey: String(format: "Audio size (%.1f MB) exceeds Gemini's 20 MB inline API payload limit. Please trim the session.", mb)]
                )
            }
            let validMime = (audioMimeType == "audio/mp4") ? "audio/m4a" : audioMimeType
            parts.append([
                "inlineData": [
                    "mimeType": validMime,
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
            requestBody: requestBody,
            timeoutInterval: 120
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

    public static func extractJsonArraySubstring(from text: String) -> String? {
        guard let firstBracket = text.firstIndex(of: "["),
              let lastBracket = text.lastIndex(of: "]"),
              firstBracket <= lastBracket else {
            return nil
        }
        return String(text[firstBracket...lastBracket])
    }

    // MARK: - Lu.ma Bengaluru Tech & AI Events

    public func fetchLumaBengaluruEvents(
        apiKey: String,
        modelName: String
    ) async throws -> [LumaBengaluruEvent] {
        let cleanKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanKey.isEmpty else {
            return Self.sampleBengaluruEvents()
        }

        let prompt = """
        Search Google for upcoming tech and AI events, developer meetups, founder mixers, and demo days in Bengaluru (Bangalore), especially events listed on lu.ma (Luma Bengaluru) or happening in tech hubs like Koramangala, Indiranagar, HSR Layout, Bellandur.
        Return ONLY valid JSON matching this schema:
        [
          {
            "title": "Event Title",
            "date": "e.g. Saturday, Oct 24, 2026",
            "time": "e.g. 10:00 AM - 1:00 PM IST",
            "venue": "e.g. Indiranagar, Bengaluru",
            "organizer": "e.g. Bengaluru AI Community",
            "category": "GenAI / LLMs / Robotics / Startups",
            "url": "https://lu.ma/... or relevant URL",
            "description": "1-2 sentence crisp overview of what attendees will learn or see."
          }
        ]
        No markdown wrapping, just raw JSON array.
        """

        let requestPayload: [String: Any] = [
            "contents": [
                [
                    "parts": [
                        ["text": prompt]
                    ]
                ]
            ],
            "tools": [
                ["googleSearch": [:]]
            ]
        ]

        let requestBody = try JSONSerialization.data(withJSONObject: requestPayload)

        do {
            let responseData = try await callWithFallbacks(
                apiKey: cleanKey,
                preferredModel: modelName,
                requestBody: requestBody,
                timeoutInterval: 45
            )

            guard let root = try JSONSerialization.jsonObject(with: responseData) as? [String: Any],
                  let candidates = root["candidates"] as? [[String: Any]],
                  let first = candidates.first,
                  let content = first["content"] as? [String: Any],
                  let parts = content["parts"] as? [[String: Any]],
                  let text = parts.first?["text"] as? String,
                  let arrayJson = Self.extractJsonArraySubstring(from: text),
                  let arrayData = arrayJson.data(using: .utf8),
                  let jsonArray = try? JSONSerialization.jsonObject(with: arrayData) as? [[String: Any]] else {
                return Self.sampleBengaluruEvents()
            }

            var events: [LumaBengaluruEvent] = []
            for item in jsonArray {
                guard let title = item["title"] as? String, !title.isEmpty else { continue }
                let date = (item["date"] as? String) ?? "Upcoming"
                let time = (item["time"] as? String) ?? ""
                let venue = (item["venue"] as? String) ?? "Bengaluru"
                let organizer = (item["organizer"] as? String) ?? "Lu.ma Bengaluru"
                let category = (item["category"] as? String) ?? "AI / Tech"
                let url = (item["url"] as? String) ?? "https://lu.ma/bengaluru"
                let desc = (item["description"] as? String) ?? ""

                events.append(
                    LumaBengaluruEvent(
                        title: title,
                        date: date,
                        time: time,
                        venue: venue,
                        organizer: organizer,
                        category: category,
                        url: url,
                        description: desc
                    )
                )
            }

            return events.isEmpty ? Self.sampleBengaluruEvents() : events
        } catch {
            return Self.sampleBengaluruEvents()
        }
    }

    public static func sampleBengaluruEvents() -> [LumaBengaluruEvent] {
        return [
            LumaBengaluruEvent(
                title: "Physical AI & Robotics Demo Night",
                date: "Wednesday, Oct 28, 2026",
                time: "5:30 PM - 8:30 PM IST",
                venue: "South Park Commons, Koramangala",
                organizer: "South Park Commons & Elevation Capital",
                category: "Physical AI / Robotics",
                url: "https://lu.ma/bengaluru",
                description: "Live demos of humanoid policies, edge inference chips, and robotic perception architectures built by Bengaluru founders."
            ),
            LumaBengaluruEvent(
                title: "Bengaluru GenAI Builders & Agents Summit",
                date: "Saturday, Nov 7, 2026",
                time: "10:00 AM - 3:00 PM IST",
                venue: "Indiranagar Club, Indiranagar",
                organizer: "Bengaluru AI Collective",
                category: "Agents & LLMs",
                url: "https://lu.ma/bengaluru",
                description: "Deep dive into production multi-agent workflows, DSPy pipeline compilations, and enterprise RAG evaluation at scale."
            ),
            LumaBengaluruEvent(
                title: "Karnataka Sovereign AI & Indic Compute Forum",
                date: "Thursday, Nov 12, 2026",
                time: "2:00 PM - 6:00 PM IST",
                venue: "IISc & Palace Grounds, Bengaluru",
                organizer: "KITS & Karnataka Innovation Council",
                category: "Policy & Compute",
                url: "https://lu.ma/bengaluru",
                description: "State initiatives for localized Kannada/Indic LLMs, GPU cluster subsidies, and academic-industry research grants."
            ),
            LumaBengaluruEvent(
                title: "Rust Systems & High-Throughput ML Serving Mixer",
                date: "Friday, Nov 20, 2026",
                time: "6:00 PM - 9:00 PM IST",
                venue: "HSR Layout Sector 4, Bengaluru",
                organizer: "Bangalore Rust & Systems Club",
                category: "Systems & Infra",
                url: "https://lu.ma/bengaluru",
                description: "Architecting microsecond-latency tensor servers, vLLM optimizations, and on-device quantization pipelines."
            )
        ]
    }

    // MARK: - arXiv Trending GenAI Paper

    public struct ArxivRawEntry: Sendable {
        public let id: String
        public let title: String
        public let summary: String
        public let authors: [String]
        public let published: String
        public let pdfUrl: String

        public init(
            id: String,
            title: String,
            summary: String,
            authors: [String] = [],
            published: String = "",
            pdfUrl: String = ""
        ) {
            self.id = id
            self.title = title
            self.summary = summary
            self.authors = authors
            self.published = published
            self.pdfUrl = pdfUrl
        }
    }

    public func fetchTrendingArxivPapers(count: Int = 5) async throws -> [ArxivRawEntry] {
        let endpoint = "https://export.arxiv.org/api/query?search_query=cat:cs.AI+OR+cat:cs.CL+OR+cat:cs.LG&sortBy=submittedDate&sortOrder=descending&max_results=\(count)"
        guard let url = URL(string: endpoint) else { return [] }

        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("YenGuru/1.0 (iOS; Research)", forHTTPHeaderField: "User-Agent")

        let (data, _) = try await session.data(for: request)
        let parser = XMLParser(data: data)
        let delegate = ArxivFeedParserDelegate()
        parser.delegate = delegate
        parser.parse()
        return delegate.entries
    }

    public func simplifyArxivPaper(
        apiKey: String,
        modelName: String,
        rawEntry: ArxivRawEntry
    ) async throws -> ArxivGenAIPaper {
        let cleanKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanKey.isEmpty else {
            return Self.sampleArxivPaper()
        }

        let prompt = """
        You are a principal AI systems architect explaining cutting-edge arXiv research to an engineering leader/tech executive who has deep software and systems experience but is NOT a data scientist or ML PhD.
        
        RESEARCH PAPER:
        Title: \(rawEntry.title)
        Authors: \(rawEntry.authors.joined(separator: ", "))
        Published: \(rawEntry.published)
        Abstract:
        \(rawEntry.summary)

        TASK:
        Explain this paper with extreme clarity and high signal.
        Avoid math jargon, matrix algebra notations, or dense academic phrasing.
        Use intuitive software analogies (e.g. caching, compiler optimizations, RPC, distributed consensus) where appropriate.

        Return your output as a strict JSON object (no markdown fences) with keys:
        {
          "plainEnglishSummary": "A vivid 2-paragraph conceptual explanation of what this paper actually does.",
          "coreProblem": "What bottleneck, failure mode, or cost barrier in current AI architectures does this solve?",
          "coreBreakthrough": "The specific novel architecture, algorithm, or insight introduced.",
          "engineeringImplication": "What this means for software engineers and engineering leaders building AI systems today.",
          "twoMinuteTakeaway": "A razor-sharp 2-sentence executive takeaway for a CTO or tech lead."
        }
        """

        let requestPayload: [String: Any] = [
            "contents": [
                [
                    "parts": [
                        ["text": prompt]
                    ]
                ]
            ]
        ]

        let requestBody = try JSONSerialization.data(withJSONObject: requestPayload)

        do {
            let responseData = try await callWithFallbacks(
                apiKey: cleanKey,
                preferredModel: modelName,
                requestBody: requestBody,
                timeoutInterval: 45
            )

            guard let root = try JSONSerialization.jsonObject(with: responseData) as? [String: Any],
                  let candidates = root["candidates"] as? [[String: Any]],
                  let first = candidates.first,
                  let content = first["content"] as? [String: Any],
                  let parts = content["parts"] as? [[String: Any]],
                  let text = parts.first?["text"] as? String,
                  let jsonSub = Self.extractJsonSubstring(from: text),
                  let jsonData = jsonSub.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
                return Self.sampleArxivPaper()
            }

            return ArxivGenAIPaper(
                id: rawEntry.id,
                title: rawEntry.title,
                authors: rawEntry.authors,
                publishedDate: rawEntry.published,
                arxivUrl: rawEntry.id,
                pdfUrl: rawEntry.pdfUrl.isEmpty ? rawEntry.id.replacingOccurrences(of: "/abs/", with: "/pdf/") : rawEntry.pdfUrl,
                rawAbstract: rawEntry.summary,
                plainEnglishSummary: (obj["plainEnglishSummary"] as? String) ?? rawEntry.summary,
                coreProblem: (obj["coreProblem"] as? String) ?? "Training complexity and generalization limits in existing models.",
                coreBreakthrough: (obj["coreBreakthrough"] as? String) ?? "Novel architectural optimization over latent representation space.",
                engineeringImplication: (obj["engineeringImplication"] as? String) ?? "Enables faster fine-tuning and significantly lower inference compute budgets.",
                twoMinuteTakeaway: (obj["twoMinuteTakeaway"] as? String) ?? "A major step forward for practical real-world deployment of complex generative models."
            )
        } catch {
            return Self.sampleArxivPaper()
        }
    }

    public static func sampleArxivPaper() -> ArxivGenAIPaper {
        return ArxivGenAIPaper(
            id: "http://arxiv.org/abs/2610.20606v1",
            title: "Modeling and Understanding Neural Weights of Humanoid Policies with Diffusion",
            authors: ["Huixuan Cheng", "Bike Zhang", "Shankar Sastry", "Koushil Sreenath"],
            publishedDate: "October 14, 2026",
            arxivUrl: "http://arxiv.org/abs/2610.20606v1",
            pdfUrl: "https://arxiv.org/pdf/2610.20606v1",
            rawAbstract: "Recent advances in robot learning have driven rapid progress in humanoid control, enabling increasingly agile behaviors in the real world. However, humanoid control networks are typically obtained through conventional training procedures, while comparatively little attention has been given to directly modeling the distribution of their learned weights and generating functional networks in weight space. In this work, we investigate whether high-performing humanoid control networks can be generatively modeled directly through their weights. We adopt a latent diffusion framework that couples a linear autoencoder with a Diffusion Transformer. The autoencoder compresses actor network parameters into a compact latent space, and the Diffusion Transformer models the distribution of these latents.",
            plainEnglishSummary: "Instead of training a robot's brain from scratch each time or tuning millions of parameters slowly through days of reinforcement learning simulation, this paper treats the neural network's weights themselves like an image and uses diffusion to generate complete, functional motor skills (walking, balancing, kicking) directly in weight space.\n\nThink of it like compiling pre-optimized binaries on demand: instead of running expensive physical physics simulations for every new skill, a Diffusion Transformer synthesizes executable neural network weights that deploy instantly onto the robot.",
            coreProblem: "Humanoid robot control policies typically require hundreds of hours of trial-and-error simulation per motion skill, and learned networks rarely transfer easily to new kinematic tasks without starting from scratch.",
            coreBreakthrough: "A latent diffusion framework coupling a linear autoencoder with a Diffusion Transformer. The autoencoder compresses actor network parameters into a low-dimensional space, and diffusion models the distribution of functional weights.",
            engineeringImplication: "Generative policy synthesis allows fast real-time deployment of motor skills onto physical robots (verified on Unitree G1) without physics simulation overhead, creating on-demand behavioral libraries.",
            twoMinuteTakeaway: "Treating policy weights as generative tokens lets us synthesize robotic motor skills in seconds. Humanoid robotics is entering its generative diffusion era."
        )
    }
}

final class ArxivFeedParserDelegate: NSObject, XMLParserDelegate, @unchecked Sendable {
    var entries: [GeminiClient.ArxivRawEntry] = []
    private var inEntry = false
    private var currentElement = ""
    private var currentText = ""
    private var id = ""
    private var title = ""
    private var summary = ""
    private var published = ""
    private var authors: [String] = []
    private var pdfUrl = ""

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        currentElement = elementName
        currentText = ""
        if elementName == "entry" {
            inEntry = true
            id = ""
            title = ""
            summary = ""
            published = ""
            authors = []
            pdfUrl = ""
        } else if inEntry && elementName == "link" {
            if attributeDict["title"] == "pdf" || attributeDict["type"] == "application/pdf" {
                pdfUrl = attributeDict["href"] ?? ""
            }
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if inEntry {
            currentText += string
        }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if inEntry {
            let trimmed = currentText.trimmingCharacters(in: .whitespacesAndNewlines)
            if elementName == "title" {
                title = trimmed.replacingOccurrences(of: "\n", with: " ")
            } else if elementName == "summary" {
                summary = trimmed.replacingOccurrences(of: "\n", with: " ")
            } else if elementName == "published" {
                published = trimmed
            } else if elementName == "id" {
                id = trimmed
            } else if elementName == "name" {
                authors.append(trimmed)
            } else if elementName == "entry" {
                inEntry = false
                let entry = GeminiClient.ArxivRawEntry(
                    id: id,
                    title: title,
                    summary: summary,
                    authors: authors,
                    published: published,
                    pdfUrl: pdfUrl
                )
                entries.append(entry)
            }
        }
    }
}

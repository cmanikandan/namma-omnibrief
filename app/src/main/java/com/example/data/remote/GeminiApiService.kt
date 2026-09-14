package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ArticleAnalysisResult(
    val detectedSource: String,
    val sourceDetectedAutomatically: Boolean,
    val headline: String,
    val postDraft: String,
    val hashtags: List<String>,
    val fullSummary: String,
    /** The single dominant story the model locked on to (peripheral items ignored). */
    val mainTopic: String = ""
)

data class ConferenceReportResult(
    val sessionTitle: String,
    val speaker: String,
    val executiveSummary: String,
    val keyTakeaways: List<String>,
    val slideInsights: List<String>,
    val actionItems: List<String>,
    val fullReportMarkdown: String
)

class GeminiApiService {

    companion object {
        /** Ordered fallback chain used when the selected model is unavailable. */
        val FALLBACK_MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-2.5-flash")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeArticle(
        apiKey: String,
        modelName: String,
        textInput: String,
        imageUris: List<Uri>,
        specifiedSource: String?,
        isXBlue: Boolean,
        context: Context
    ): Result<ArticleAnalysisResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please set your key in Settings or the Secrets panel."))
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("You are an executive intelligence analyst who converts a SINGLE news article into an authoritative, professional post for X (formerly Twitter).\n\n")

        promptBuilder.append("NON-NEGOTIABLE ANALYSIS RULES:\n")
        promptBuilder.append("1. SINGLE MAIN TOPIC ONLY: A photographed newspaper or screenshot often captures neighbouring columns, adjacent headlines, advertisements, teasers, page furniture, or partially visible unrelated stories. Identify the ONE dominant article (largest headline, largest area, the story the photo is clearly centred on) and analyse ONLY that story. Completely ignore every peripheral or inadvertently captured item.\n")
        promptBuilder.append("2. STRICTLY GROUNDED: Use ONLY the content that is actually present in the supplied image/text. Do NOT use outside knowledge, do NOT infer events beyond the text, and do NOT add background the source does not state.\n")
        promptBuilder.append("3. NO EDITORIALISING: Report what the article says. No opinions, no praise, no criticism, no predictions, no 'this signals...', no calls to action, no rhetorical questions. Neutral, factual, third-person reporting voice.\n")
        promptBuilder.append("4. If a figure, name, date or quote is illegible or absent, omit it rather than guessing. Never fabricate numbers. Do NOT promote an incidental mention into a claim: a product, company or person that appears only inside someone's job title, a photo caption, a quoted aside, an example or a comparison is NOT thereby part of the main subject's actions, plans or priorities. Attribute something to the subject only where the article explicitly does so.\n")
        promptBuilder.append("5. Tone: Professional, factual, objective, high signal-to-noise. NO emojis whatsoever.\n")

        promptBuilder.append("6. Source Identification: Determine the source publication (e.g. New York Times, Wall Street Journal, Financial Times, Times of India, Economic Times, The Hindu, Deccan Herald, Bloomberg, Reuters, etc.). ")
        if (!specifiedSource.isNullOrBlank() && specifiedSource != "Auto-Detect") {
            promptBuilder.append("The user has specified the source as: \"$specifiedSource\". Use this source.\n")
        } else {
            promptBuilder.append("Read any visible masthead, dateline, byline, page furniture or URL in the image/text to identify the exact publisher. If it cannot be determined from the content, return \"Unknown\" and set sourceDetectedAutomatically to false.\n")
        }

        if (isXBlue) {
            promptBuilder.append("7. Format: The author has an X Premium/Blue account, so write a crisp executive analysis (roughly 500-1200 characters) covering the core thesis, the verified figures/facts stated in the article, the stated implications, and exact source attribution (e.g. 'Source: Financial Times').\n")
        } else {
            promptBuilder.append("7. Format: Standard X post. The ENTIRE postDraft, including attribution and every hashtag, MUST be at most 260 characters. Aim for 200-250 characters to leave a safety margin; a post over 280 characters is rejected by X outright. Count carefully and shorten the wording rather than dropping the source attribution.\n")
        }
        promptBuilder.append("8. Include 2 to 4 precise, professional tags derived from the article subject (e.g. #Economy, #Markets, #TechPolicy).\n\n")

        promptBuilder.append("Return your response as a strict JSON object (no markdown fences) with these keys:\n")
        promptBuilder.append("{\n")
        promptBuilder.append("  \"detectedSource\": \"Name of publication or 'Unknown'\",\n")
        promptBuilder.append("  \"sourceDetectedAutomatically\": true/false,\n")
        promptBuilder.append("  \"mainTopic\": \"One line naming the single dominant story you analysed\",\n")
        promptBuilder.append("  \"headline\": \"Concise headline of that dominant story\",\n")
        promptBuilder.append("  \"postDraft\": \"The complete ready-to-publish X post content with attribution and hashtags\",\n")
        promptBuilder.append("  \"hashtags\": [\"#Tag1\", \"#Tag2\"],\n")
        promptBuilder.append("  \"fullSummary\": \"Factual bullet-point summary of the dominant story only\"\n")
        promptBuilder.append("}\n")

        if (textInput.isNotBlank()) {
            promptBuilder.append("\nARTICLE TEXT/CONTENT:\n$textInput\n")
        }
        if (imageUris.isNotEmpty()) {
            promptBuilder.append("\nThe attached image is a photograph/scan of the article. Apply rule 1 rigorously.\n")
        }

        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", promptBuilder.toString()))

        // Process images
        for (uri in imageUris) {
            try {
                val base64Data = readUriAsBase64Jpeg(context, uri)
                if (base64Data != null) {
                    val inlineDataObj = JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Data)
                    partsArray.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } catch (e: Exception) {
                Log.e("GeminiApi", "Error reading image URI: $uri", e)
            }
        }

        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        contentsArray.put(JSONObject().put("parts", partsArray))
        requestJson.put("contents", contentsArray)

        callWithFallbacks(apiKey, modelName, requestJson).mapCatching { jsonResponse ->
            parseArticleResult(jsonResponse)
        }
    }

    suspend fun generateConferenceReport(
        apiKey: String,
        modelName: String,
        conferenceTitle: String,
        sessionTopic: String,
        speakerName: String,
        photoUris: List<Uri>,
        audioFile: File?,
        context: Context
    ): Result<ConferenceReportResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        val promptBuilder = StringBuilder()
        promptBuilder.append("You are an executive conference intelligence scribe using high-context multimodal reasoning to synthesize a conference session into a comprehensive, boardroom-ready report.\n\n")
        promptBuilder.append("SESSION METADATA:\n")
        promptBuilder.append("Conference: ${conferenceTitle.ifBlank { "Conference Session" }}\n")
        promptBuilder.append("Topic: ${sessionTopic.ifBlank { "Executive Presentation" }}\n")
        promptBuilder.append("Speaker: ${speakerName.ifBlank { "Keynote Speaker" }}\n\n")
        promptBuilder.append("INPUTS PROVIDED:\n")
        promptBuilder.append("- Presentation slide photos: ${photoUris.size} photos captured.\n")
        if (audioFile != null && audioFile.exists()) {
            promptBuilder.append("- Live session audio recording attached (${audioFile.length() / 1024} KB).\n")
        }
        promptBuilder.append("\nTASK:\n")
        promptBuilder.append("Thoroughly analyze all presentation slides (diagrams, bullet points, charts, statistics) and synthesize them with the spoken session arguments into a detailed briefing.\n")
        promptBuilder.append("Return your output as a strict JSON object with keys:\n")
        promptBuilder.append("{\n")
        promptBuilder.append("  \"sessionTitle\": \"Clean title for the session\",\n")
        promptBuilder.append("  \"speaker\": \"Speaker name\",\n")
        promptBuilder.append("  \"executiveSummary\": \"2-3 paragraph executive summary of the session\",\n")
        promptBuilder.append("  \"keyTakeaways\": [\"takeaway 1\", \"takeaway 2\", \"takeaway 3\", \"takeaway 4\"],\n")
        promptBuilder.append("  \"slideInsights\": [\"Insight from slide/diagram 1\", \"Key data points from presentation\"],\n")
        promptBuilder.append("  \"actionItems\": [\"Strategic recommendation 1\", \"Action item 2\"],\n")
        promptBuilder.append("  \"fullReportMarkdown\": \"A fully formatted, beautiful Markdown report with headers (##), bold text, bullet points, ready to export or email.\"\n")
        promptBuilder.append("}\n")

        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", promptBuilder.toString()))

        // Add photos (supports > 10 photos)
        for (uri in photoUris) {
            try {
                val base64Data = readUriAsBase64Jpeg(context, uri)
                if (base64Data != null) {
                    val inlineDataObj = JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Data)
                    partsArray.put(JSONObject().put("inlineData", inlineDataObj))
                }
            } catch (e: Exception) {
                Log.e("GeminiApi", "Error reading conference photo: $uri", e)
            }
        }

        // Add audio if recorded
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            try {
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                val mimeType = when {
                    audioFile.name.endsWith(".m4a", true) -> "audio/mp4"
                    audioFile.name.endsWith(".aac", true) -> "audio/aac"
                    audioFile.name.endsWith(".wav", true) -> "audio/wav"
                    else -> "audio/mp4"
                }
                val inlineDataObj = JSONObject()
                    .put("mimeType", mimeType)
                    .put("data", base64Audio)
                partsArray.put(JSONObject().put("inlineData", inlineDataObj))
            } catch (e: Exception) {
                Log.e("GeminiApi", "Error reading audio file", e)
            }
        }

        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        contentsArray.put(JSONObject().put("parts", partsArray))
        requestJson.put("contents", contentsArray)

        callWithFallbacks(apiKey, modelName, requestJson).mapCatching { jsonResponse ->
            parseConferenceResult(jsonResponse, sessionTopic, speakerName)
        }
    }

    /**
     * Calls the requested model, then walks a fallback chain of progressively older Flash models.
     * Keeps the app usable if a model id is retired or temporarily unavailable.
     */
    private fun callWithFallbacks(
        apiKey: String,
        modelName: String,
        requestJson: JSONObject
    ): Result<String> {
        val chain = (listOf(modelName) + FALLBACK_MODELS).distinct()
        var lastFailure: Result<String>? = null
        for (model in chain) {
            val response = callGeminiRaw(apiKey, model, requestJson)
            if (response.isSuccess) return response
            lastFailure = response
            Log.w("GeminiApi", "Model $model failed, trying next fallback", response.exceptionOrNull())
        }
        return lastFailure ?: Result.failure(Exception("No Gemini model available"))
    }

    private fun callGeminiRaw(apiKey: String, model: String, requestJson: JSONObject): Result<String> {
        return try {
            val cleanModel = if (model.startsWith("models/")) model.removePrefix("models/") else model
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errorJson = JSONObject(responseBody).optJSONObject("error")
                        errorJson?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $responseBody"
                    }
                    Result.failure(Exception(errorMsg))
                } else {
                    Result.success(responseBody)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseArticleResult(rawResponse: String): ArticleAnalysisResult {
        val root = JSONObject(rawResponse)
        val text = extractFirstCandidateText(root)

        // Try parsing JSON block from text
        val jsonStr = extractJsonSubstring(text)
        return if (jsonStr != null) {
            val json = JSONObject(jsonStr)
            val detectedSource = json.optString("detectedSource", "Unknown")
            val sourceDetected = json.optBoolean("sourceDetectedAutomatically", detectedSource != "Unknown" && detectedSource.isNotBlank())
            val headline = json.optString("headline", "Article Summary")
            val postDraft = json.optString("postDraft", text)
            val hashtagsJson = json.optJSONArray("hashtags")
            val tags = mutableListOf<String>()
            if (hashtagsJson != null) {
                for (i in 0 until hashtagsJson.length()) {
                    tags.add(hashtagsJson.getString(i))
                }
            }
            val fullSummary = json.optString("fullSummary", postDraft)
            val mainTopic = json.optString("mainTopic", "")

            ArticleAnalysisResult(
                detectedSource = detectedSource,
                sourceDetectedAutomatically = sourceDetected,
                headline = headline,
                postDraft = postDraft,
                hashtags = tags,
                fullSummary = fullSummary,
                mainTopic = mainTopic
            )
        } else {
            ArticleAnalysisResult(
                detectedSource = "Unknown",
                sourceDetectedAutomatically = false,
                headline = "Analysis",
                postDraft = text,
                hashtags = listOf("#Analysis", "#News"),
                fullSummary = text
            )
        }
    }

    private fun parseConferenceResult(rawResponse: String, defaultTopic: String, defaultSpeaker: String): ConferenceReportResult {
        val root = JSONObject(rawResponse)
        val text = extractFirstCandidateText(root)
        val jsonStr = extractJsonSubstring(text)

        return if (jsonStr != null) {
            val json = JSONObject(jsonStr)
            val title = json.optString("sessionTitle", defaultTopic.ifBlank { "Conference Intelligence Report" })
            val speaker = json.optString("speaker", defaultSpeaker.ifBlank { "Keynote Speaker" })
            val execSummary = json.optString("executiveSummary", "")

            val takeawaysJson = json.optJSONArray("keyTakeaways")
            val takeaways = mutableListOf<String>()
            if (takeawaysJson != null) {
                for (i in 0 until takeawaysJson.length()) {
                    takeaways.add(takeawaysJson.getString(i))
                }
            }

            val slideJson = json.optJSONArray("slideInsights")
            val slideInsights = mutableListOf<String>()
            if (slideJson != null) {
                for (i in 0 until slideJson.length()) {
                    slideInsights.add(slideJson.getString(i))
                }
            }

            val actionJson = json.optJSONArray("actionItems")
            val actionItems = mutableListOf<String>()
            if (actionJson != null) {
                for (i in 0 until actionJson.length()) {
                    actionItems.add(actionJson.getString(i))
                }
            }

            val fullReport = json.optString("fullReportMarkdown", text)

            ConferenceReportResult(
                sessionTitle = title,
                speaker = speaker,
                executiveSummary = execSummary,
                keyTakeaways = takeaways,
                slideInsights = slideInsights,
                actionItems = actionItems,
                fullReportMarkdown = fullReport
            )
        } else {
            ConferenceReportResult(
                sessionTitle = defaultTopic.ifBlank { "Conference Session Report" },
                speaker = defaultSpeaker.ifBlank { "Presenter" },
                executiveSummary = text,
                keyTakeaways = listOf("Key takeaways synthesized from slides and presentation audio."),
                slideInsights = emptyList(),
                actionItems = emptyList(),
                fullReportMarkdown = text
            )
        }
    }

    private fun extractFirstCandidateText(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                sb.append(part.getString("text"))
            }
        }
        return sb.toString().trim()
    }

    private fun extractJsonSubstring(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }

    /**
     * Reads [uri] as a base64 JPEG for an `inlineData` part.
     *
     * Shares [ImageEncoder] with the X upload path, so the model sees exactly the image that will
     * be posted. The orientation fix matters twice over here: a newspaper photographed in portrait
     * decodes sideways, and asking the model to read rotated body text makes both the masthead
     * detection and the summary worse.
     */
    private fun readUriAsBase64Jpeg(context: Context, uri: Uri): String? {
        val bytes = ImageEncoder.readUriAsJpegBytes(
            context = context,
            uri = uri,
            logTag = "GeminiApi"
        ) ?: return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

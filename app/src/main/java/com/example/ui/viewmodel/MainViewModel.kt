package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.RecordingState
import com.example.data.local.BriefItem
import com.example.data.local.OmniBriefDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.remote.ArticleAnalysisResult
import com.example.data.remote.ConferenceReportResult
import com.example.data.remote.GeminiApiService
import com.example.data.remote.XApiService
import com.example.data.remote.XPostResult
import com.example.data.repository.BriefRepository
import com.example.ui.components.AppDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Lifecycle of a single queued X post. */
enum class DraftPostStatus { PENDING, POSTING, POSTED, FAILED }

/**
 * One reviewable X post. A batch of up to [AppPreferences.MAX_ARTICLE_IMAGES] images produces one
 * item per image, so every photo gets its own independently editable and approvable draft.
 */
data class XPostDraftItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val imageUri: Uri? = null,
    val text: String,
    val source: String = "",
    val headline: String = "",
    val mainTopic: String = "",
    val fullSummary: String = "",
    val status: DraftPostStatus = DraftPostStatus.PENDING,
    val tweetId: String? = null,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    val prefs = AppPreferences(context)
    private val database = OmniBriefDatabase.getDatabase(context)
    val repository = BriefRepository(database.briefItemDao())

    private val geminiService = GeminiApiService()
    private val xApiService = XApiService()

    val audioRecorder = AudioRecorderManager(context)
    val audioPlayer = AudioPlayerManager()

    // Navigation
    private val _currentDestination = MutableStateFlow(AppDestination.ARTICLE_TO_X)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    fun navigateTo(dest: AppDestination) {
        _currentDestination.value = dest
    }

    // --- History Flow (Max 10 with automatic rollover) ---
    val storedItems: StateFlow<List<BriefItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Function 1: Article to X Post State ---
    val articleTextInput = MutableStateFlow("")
    val articleImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val articleSource = MutableStateFlow(prefs.defaultSource)
    val showSourceDialog = MutableStateFlow(false)

    val isAnalyzingArticle = MutableStateFlow(false)
    val articleAnalysisResult = MutableStateFlow<ArticleAnalysisResult?>(null)
    val activePostDraft = MutableStateFlow("")
    val isPostingToX = MutableStateFlow(false)
    val postToXStatus = MutableStateFlow<String?>(null)
    val articleError = MutableStateFlow<String?>(null)

    /** One draft per attached image (plus one for pasted text), reviewed before posting. */
    val postDrafts = MutableStateFlow<List<XPostDraftItem>>(emptyList())

    /** Human readable progress such as "Analysing image 3 of 7". */
    val batchProgress = MutableStateFlow<String?>(null)

    val maxArticleImages = AppPreferences.MAX_ARTICLE_IMAGES

    // --- Function 2: Conference Reporter State ---
    val confTitle = MutableStateFlow("")
    val confTopic = MutableStateFlow("")
    val confSpeaker = MutableStateFlow("")
    val confPhotoUris = MutableStateFlow<List<Uri>>(emptyList())
    val recordedAudioFile = MutableStateFlow<File?>(null)

    val isGeneratingReport = MutableStateFlow(false)
    val conferenceReportResult = MutableStateFlow<ConferenceReportResult?>(null)
    val conferenceError = MutableStateFlow<String?>(null)
    val exportStatus = MutableStateFlow<String?>(null)

    // --- Article to X Actions ---

    /** Adds an image, enforcing the 10-image batch cap. Returns false when the cap is hit. */
    fun addArticleImageUri(uri: Uri): Boolean {
        if (articleImageUris.value.size >= maxArticleImages) {
            articleError.value = "Maximum $maxArticleImages images per batch. Remove one to add another."
            return false
        }
        articleImageUris.value = articleImageUris.value + uri
        articleError.value = null
        return true
    }

    /** Adds a multi-select result, silently trimming anything beyond the 10-image cap. */
    fun addArticleImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = maxArticleImages - articleImageUris.value.size
        if (remaining <= 0) {
            articleError.value = "Maximum $maxArticleImages images per batch. Remove one to add another."
            return
        }
        val accepted = uris.take(remaining)
        articleImageUris.value = articleImageUris.value + accepted
        articleError.value = if (uris.size > remaining) {
            "Only the first $maxArticleImages images were added (batch cap)."
        } else {
            null
        }
    }

    fun removeArticleImageUri(index: Int) {
        articleImageUris.value = articleImageUris.value.filterIndexed { i, _ -> i != index }
    }

    fun clearArticleImages() {
        articleImageUris.value = emptyList()
    }

    fun setArticleSource(source: String) {
        articleSource.value = source
        showSourceDialog.value = false
    }

    // --- Draft queue editing ---

    fun updateDraftText(id: String, newText: String) {
        postDrafts.value = postDrafts.value.map {
            if (it.id == id) it.copy(text = newText, status = DraftPostStatus.PENDING, error = null) else it
        }
        if (postDrafts.value.firstOrNull()?.id == id) activePostDraft.value = newText
    }

    fun removeDraft(id: String) {
        postDrafts.value = postDrafts.value.filterNot { it.id == id }
        activePostDraft.value = postDrafts.value.firstOrNull()?.text ?: ""
    }

    fun clearDrafts() {
        postDrafts.value = emptyList()
        activePostDraft.value = ""
        postToXStatus.value = null
    }

    /**
     * Analyses every attached image independently (one post per image, max 10) plus the pasted
     * text, so each photo produces its own reviewable draft instead of one merged post.
     */
    fun analyzeArticle() {
        if (articleTextInput.value.isBlank() && articleImageUris.value.isEmpty()) {
            articleError.value = "Please enter article text, copy-paste excerpt, or attach an image/doc."
            return
        }

        viewModelScope.launch {
            isAnalyzingArticle.value = true
            articleError.value = null
            postToXStatus.value = null
            postDrafts.value = emptyList()

            val images = articleImageUris.value.take(maxArticleImages)
            val collected = mutableListOf<XPostDraftItem>()
            val failures = mutableListOf<String>()

            // 1. One independent analysis per image: no cross-contamination between articles.
            images.forEachIndexed { index, uri ->
                batchProgress.value = "Analysing image ${index + 1} of ${images.size}..."
                val result = geminiService.analyzeArticle(
                    apiKey = prefs.geminiApiKey,
                    modelName = prefs.geminiModel,
                    textInput = "",
                    imageUris = listOf(uri),
                    specifiedSource = articleSource.value,
                    isXBlue = prefs.isXBlue,
                    context = context
                )
                result.onSuccess { data ->
                    collected += XPostDraftItem(
                        label = "Image ${index + 1}",
                        imageUri = uri,
                        text = data.postDraft,
                        source = data.detectedSource.ifBlank { articleSource.value },
                        headline = data.headline,
                        mainTopic = data.mainTopic,
                        fullSummary = data.fullSummary
                    )
                    if (index == 0) articleAnalysisResult.value = data
                }.onFailure { error ->
                    failures += "Image ${index + 1}: ${error.localizedMessage ?: error.message}"
                }
            }

            // 2. Pasted / typed text becomes its own draft.
            if (articleTextInput.value.isNotBlank()) {
                batchProgress.value = "Analysing pasted text..."
                val result = geminiService.analyzeArticle(
                    apiKey = prefs.geminiApiKey,
                    modelName = prefs.geminiModel,
                    textInput = articleTextInput.value,
                    imageUris = emptyList(),
                    specifiedSource = articleSource.value,
                    isXBlue = prefs.isXBlue,
                    context = context
                )
                result.onSuccess { data ->
                    collected += XPostDraftItem(
                        label = "Pasted text",
                        text = data.postDraft,
                        source = data.detectedSource.ifBlank { articleSource.value },
                        headline = data.headline,
                        mainTopic = data.mainTopic,
                        fullSummary = data.fullSummary
                    )
                    if (articleAnalysisResult.value == null) articleAnalysisResult.value = data
                }.onFailure { error ->
                    failures += "Text: ${error.localizedMessage ?: error.message}"
                }
            }

            batchProgress.value = null
            isAnalyzingArticle.value = false
            postDrafts.value = collected
            activePostDraft.value = collected.firstOrNull()?.text ?: activePostDraft.value

            if (collected.isEmpty()) {
                articleError.value = failures.joinToString("\n").ifBlank { "Failed to analyze article" }
                return@launch
            }
            if (failures.isNotEmpty()) {
                articleError.value = "${collected.size} draft(s) ready. Some inputs failed:\n" +
                        failures.joinToString("\n")
            }

            // Adopt a confidently detected source, otherwise ask the user which publication it is.
            val detected = collected.firstOrNull { it.source.isNotBlank() && it.source != "Unknown" }?.source
            if (detected != null) {
                articleSource.value = detected
            } else if (articleSource.value.isBlank() || articleSource.value == "Auto-Detect") {
                showSourceDialog.value = true
            }

            // One history entry per analysis run keeps the 10-item rollover meaningful.
            val first = collected.first()
            saveDraftToRoom(
                title = if (collected.size > 1) "${first.headline} (+${collected.size - 1} more)" else first.headline,
                content = collected.joinToString("\n\n---\n\n") { "[${it.label}] ${it.text}" },
                source = articleSource.value,
                type = "X_POST",
                imageCount = images.size
            )
        }
    }

    /**
     * Approves the batch and publishes every draft to X sequentially (one by one), pausing briefly
     * between posts so the API is not rate limited. Per-draft status is surfaced in the UI.
     */
    fun approveAndPostToX() {
        val queue = postDrafts.value
        if (queue.isEmpty()) {
            postSingleDraft(activePostDraft.value)
            return
        }

        val token = if (prefs.xAuthMethod == "OAUTH2_USER") prefs.xAccessToken else prefs.xBearerToken
        if (token.isBlank() && prefs.xRefreshToken.isBlank()) {
            postToXStatus.value = "No X credentials set in Settings. Opening the X composer with draft 1..."
            XApiService.launchDirectXComposer(context, queue.first().text)
            return
        }

        viewModelScope.launch {
            isPostingToX.value = true
            postToXStatus.value = null
            var posted = 0
            var failed = 0

            val pending = postDrafts.value.filter { it.status != DraftPostStatus.POSTED && it.text.isNotBlank() }
            pending.forEachIndexed { index, draft ->
                updateDraftStatus(draft.id) { it.copy(status = DraftPostStatus.POSTING, error = null) }
                batchProgress.value = "Posting ${index + 1} of ${pending.size}..."

                val result = xApiService.postTweet(
                    accessToken = if (prefs.xAuthMethod == "OAUTH2_USER") prefs.xAccessToken else prefs.xBearerToken,
                    text = draft.text,
                    clientId = prefs.xClientId,
                    clientSecret = prefs.xClientSecret,
                    refreshToken = prefs.xRefreshToken,
                    onTokenRefreshed = { newAccess, newRefresh ->
                        prefs.xAccessToken = newAccess
                        prefs.xRefreshToken = newRefresh
                    }
                )

                when (result) {
                    is XPostResult.Success -> {
                        posted++
                        updateDraftStatus(draft.id) {
                            it.copy(status = DraftPostStatus.POSTED, tweetId = result.tweetId, error = null)
                        }
                    }
                    is XPostResult.Error -> {
                        failed++
                        updateDraftStatus(draft.id) {
                            it.copy(status = DraftPostStatus.FAILED, error = result.message)
                        }
                    }
                }

                // Small spacing between posts avoids X rate limiting on rapid sequential writes.
                if (index < pending.lastIndex) delay(2000)
            }

            batchProgress.value = null
            isPostingToX.value = false
            postToXStatus.value = when {
                failed == 0 -> "Posted $posted of ${pending.size} to X successfully."
                posted == 0 -> postDrafts.value.firstOrNull { it.error != null }?.error
                    ?: "Failed to post to X."
                else -> "Posted $posted of ${pending.size}. $failed failed - review below and retry."
            }
            Toast.makeText(context, postToXStatus.value ?: "", Toast.LENGTH_LONG).show()
        }
    }

    /** Posts a single ad-hoc draft (used when the queue is empty and the user typed a post). */
    private fun postSingleDraft(draft: String) {
        if (draft.isBlank()) return

        val token = if (prefs.xAuthMethod == "OAUTH2_USER") prefs.xAccessToken else prefs.xBearerToken
        if (token.isBlank() && prefs.xRefreshToken.isBlank()) {
            postToXStatus.value = "No X credentials set in Settings. Opening X App..."
            XApiService.launchDirectXComposer(context, draft)
            return
        }

        viewModelScope.launch {
            isPostingToX.value = true
            postToXStatus.value = null

            val result = xApiService.postTweet(
                accessToken = token,
                text = draft,
                clientId = prefs.xClientId,
                clientSecret = prefs.xClientSecret,
                refreshToken = prefs.xRefreshToken,
                onTokenRefreshed = { newAccess, newRefresh ->
                    prefs.xAccessToken = newAccess
                    prefs.xRefreshToken = newRefresh
                }
            )
            isPostingToX.value = false

            when (result) {
                is XPostResult.Success -> {
                    postToXStatus.value = "Successfully posted to X! (ID: ${result.tweetId})"
                    Toast.makeText(context, "Posted to X successfully!", Toast.LENGTH_SHORT).show()
                }
                is XPostResult.Error -> {
                    postToXStatus.value = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateDraftStatus(id: String, transform: (XPostDraftItem) -> XPostDraftItem) {
        postDrafts.value = postDrafts.value.map { if (it.id == id) transform(it) else it }
    }

    fun testAndRefreshXConnection(onResult: (Boolean, String) -> Unit) {
        if (prefs.xClientId.isBlank() || prefs.xRefreshToken.isBlank()) {
            onResult(false, "Enter Client ID, Client Secret and Refresh Token first.")
            return
        }
        viewModelScope.launch {
            val res = xApiService.refreshOAuth2Token(prefs.xClientId, prefs.xClientSecret, prefs.xRefreshToken)
            if (res.success) {
                prefs.xAccessToken = res.accessToken
                prefs.xRefreshToken = res.refreshToken
                onResult(true, "OAuth 2.0 connected. Access token valid for ${res.expiresIn / 60} mins; tokens refreshed and saved.")
            } else {
                onResult(false, res.error ?: "Failed to refresh token")
            }
        }
    }

    fun openInXApp() {
        val draft = postDrafts.value.firstOrNull { it.status != DraftPostStatus.POSTED }?.text
            ?: activePostDraft.value
        if (draft.isNotBlank()) {
            XApiService.launchDirectXComposer(context, draft)
        }
    }

    /** Opens the official X composer pre-filled with one specific draft from the queue. */
    fun openDraftInXApp(text: String) {
        if (text.isNotBlank()) XApiService.launchDirectXComposer(context, text)
    }

    fun loadSampleArticle() {
        articleTextInput.value = """How a Wall Street star fell to earth

A real-life thriller traces the rise of private capital through the story of financier Leon Black — and the Epstein scandal that sunk him. By Patrick Jenkins (Financial Times)

"Leon Black was in his second year at Harvard Business School when his famous father threw himself out of his office window." So begins an early chapter of William D Cohan's latest Wall Street non-fiction thriller, Money to Burn, the epic tale of a man who overcame personal tragedy, worked with junk bond maestro Michael Milken, founded private capital giant Apollo Global Management, chaired New York's MoMA gallery, and then suffered a humiliating fall from grace through his association with Jeffrey Epstein.

It is a pacy read that uses Black's life to tell the story of Wall Street's evolution over the past half-century from bank-dominated cosiness to private capital-dominated aggression — much of it imbued with vast personal and corporate greed.

Until the 2008 global financial crisis, the Wall Street figures that commanded popular interest were the big players at Goldman Sachs, Morgan Stanley or JPMorgan. But the relative decline of stock and bond markets, and the explosive rise of private equity and private credit, has thrust the leaders of a new generation of financial giants into the limelight. No one more so than Black.

Money to Burn: Leon Black, Apollo and the Remaking of Wall Street by William D Cohan (Allen Lane/Portfolio, 688 pages) details how Black, Marc Rowan, and Josh Harris built Apollo into a multi-hundred-billion empire, and how Black's 158 million dollar payments to Epstein ultimately precipitated his downfall.""".trimIndent()
        articleSource.value = "Financial Times"
        articleError.value = null
        activePostDraft.value = """Financial Times: The rise and unravelling of Apollo co-founder Leon Black.

From William D Cohan's "Money to Burn":
- Traces Wall Street's shift from bank-led finance to private capital dominance
- Charts Apollo Global Management's growth under Black, Rowan and Harris
- Details the $158m in payments to Jeffrey Epstein that preceded Black's exit

Source: Financial Times"""
        postDrafts.value = listOf(
            XPostDraftItem(
                label = "Sample: Financial Times",
                text = activePostDraft.value,
                source = "Financial Times",
                headline = "The rise and fall of Leon Black",
                mainTopic = "Leon Black, Apollo and the Epstein payments"
            )
        )
        Toast.makeText(context, "Loaded Financial Times sample & draft", Toast.LENGTH_SHORT).show()
    }

    fun loadBengaluruTechSampleArticle() {
        articleTextInput.value = """Bengaluru Tech Summit 2026: Silicon Valley of India Unveils Sovereign AI Stack & Next-Gen Semiconductor Fabrication

By Tech Bureau, Bengaluru (Deccan Herald / LiveMint)

BENGALURU — Karnataka's capital solidified its position as the undisputed AI and deep-tech capital of India at the Bengaluru Tech Summit (BTS 2026) today at Bangalore Palace. State leaders and tech visionaries jointly unveiled the 'Namma AI Grid', an indigenous sovereign high-performance computing cluster providing 10,000 GPUs to local startups and researchers in Electronic City, Whitefield, and Koramangala.

Key developments announced:
1. Sovereign AI Models: Multilingual models fine-tuned in Kannada and 14 Indic languages for digital governance and rural fintech.
2. Namma Metro AI Transit: Real-time dynamic scheduling driven by computer vision across the Purple, Green, and newly operational Pink lines, reducing commuter wait times by 35%.
3. Deep-Tech Investment: Over ${'$'}4.2B in venture commitments focused on aerospace, robotics, and clean-energy mobility corridors linking Kempegowda International Airport to the city center.

"Bengaluru is no longer just the back-office of global tech; it is the innovation engine authoring foundational AI models for the world," stated the keynote address before an audience of 5,000 global delegates.""".trimIndent()
        articleSource.value = "Deccan Herald / Bengaluru Tech Summit"
        articleError.value = null
        activePostDraft.value = """Bengaluru Tech Summit 2026 opened at Bangalore Palace with Karnataka's sovereign AI roadmap.

Reported announcements:
- ${'$'}4.2B in venture commitments across AI, robotics and clean-energy mobility
- 'Namma AI Grid': a 10,000-GPU cluster for startups in Electronic City, Whitefield and Koramangala
- Computer-vision scheduling across Namma Metro lines, cited as cutting wait times by 35%
- Indic language models covering Kannada and 14 other languages

Source: Deccan Herald

#NammaBengaluru #BTS2026 #DeepTech #AIInfrastructure"""
        postDrafts.value = listOf(
            XPostDraftItem(
                label = "Sample: Namma BLR",
                text = activePostDraft.value,
                source = "Deccan Herald",
                headline = "Bengaluru Tech Summit 2026 sovereign AI stack",
                mainTopic = "BTS 2026 announcements"
            )
        )
        Toast.makeText(context, "Loaded Namma Bengaluru Tech Summit sample & draft", Toast.LENGTH_SHORT).show()
    }

    fun pasteFromClipboard(clipboardText: String) {
        if (clipboardText.isNotBlank()) {
            articleTextInput.value = clipboardText
            articleError.value = null
            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(text: String, label: String = "Text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // --- Conference Reporter Actions ---
    fun addConferencePhotoUri(uri: Uri) {
        confPhotoUris.value = confPhotoUris.value + uri
    }

    fun addConferencePhotoUris(uris: List<Uri>) {
        confPhotoUris.value = confPhotoUris.value + uris
    }

    fun removeConferencePhoto(index: Int) {
        confPhotoUris.value = confPhotoUris.value.filterIndexed { i, _ -> i != index }
    }

    fun startAudioRecording() {
        val file = audioRecorder.startRecording()
        recordedAudioFile.value = file
    }

    fun pauseAudioRecording() {
        audioRecorder.pauseRecording()
    }

    fun resumeAudioRecording() {
        audioRecorder.resumeRecording()
    }

    fun stopAudioRecording() {
        val file = audioRecorder.stopRecording()
        recordedAudioFile.value = file
    }

    fun toggleAudioPlayback() {
        val file = recordedAudioFile.value ?: audioRecorder.getCurrentFile()
        if (file != null && file.exists()) {
            audioPlayer.togglePlayPause(file)
        }
    }

    fun generateConferenceReport() {
        if (confPhotoUris.value.isEmpty() && recordedAudioFile.value == null && confTopic.value.isBlank()) {
            conferenceError.value = "Please add slide photos, record audio, or provide session topic."
            return
        }

        viewModelScope.launch {
            isGeneratingReport.value = true
            conferenceError.value = null
            exportStatus.value = null

            val result = geminiService.generateConferenceReport(
                apiKey = prefs.geminiApiKey,
                modelName = prefs.geminiModel,
                conferenceTitle = confTitle.value,
                sessionTopic = confTopic.value,
                speakerName = confSpeaker.value,
                photoUris = confPhotoUris.value,
                audioFile = recordedAudioFile.value,
                context = context
            )

            isGeneratingReport.value = false

            result.onSuccess { report ->
                conferenceReportResult.value = report

                // Save report to local Room with 10-item rollover
                saveDraftToRoom(
                    title = report.sessionTitle,
                    content = report.fullReportMarkdown,
                    source = "${report.speaker} • ${confTitle.value.ifBlank { "Conference" }}",
                    type = "CONFERENCE_REPORT",
                    imageCount = confPhotoUris.value.size,
                    hasAudio = recordedAudioFile.value != null,
                    audioSecs = audioRecorder.durationSeconds.value
                )
            }.onFailure { err ->
                conferenceError.value = err.localizedMessage ?: err.message ?: "Failed to generate report"
            }
        }
    }

    fun sendEmailViaGmail() {
        val report = conferenceReportResult.value ?: return
        val subject = "[Conference Report] ${report.sessionTitle} - ${report.speaker}"
        val body = report.fullReportMarkdown
        launchEmailIntent(subject, body, "Conference Report")
    }

    fun exportToGoogleDrive() {
        val report = conferenceReportResult.value ?: return
        exportContentToDrive(
            fileNamePrefix = "Conference_Report",
            title = report.sessionTitle,
            content = report.fullReportMarkdown,
            summary = report.executiveSummary
        )
    }

    /** Builds a markdown brief covering every draft in the current batch. */
    private fun buildArticleBriefMarkdown(): Pair<String, String> {
        val analysis = articleAnalysisResult.value
        val drafts = postDrafts.value
        val headline = analysis?.headline ?: drafts.firstOrNull()?.headline ?: "Article Brief"

        val body = buildString {
            appendLine("# $headline")
            appendLine("Source: ${articleSource.value}")
            appendLine()
            if (drafts.isEmpty()) {
                appendLine("## Executive Summary")
                appendLine(analysis?.fullSummary ?: activePostDraft.value)
                appendLine()
                appendLine("## X Post Draft")
                appendLine(activePostDraft.value)
            } else {
                appendLine("${drafts.size} post(s) in this batch.")
                drafts.forEachIndexed { index, draft ->
                    appendLine()
                    appendLine("## ${index + 1}. ${draft.label} - ${draft.headline.ifBlank { "Untitled" }}")
                    if (draft.mainTopic.isNotBlank()) appendLine("_Main topic: ${draft.mainTopic}_")
                    if (draft.source.isNotBlank()) appendLine("_Source: ${draft.source}_")
                    if (draft.fullSummary.isNotBlank()) {
                        appendLine()
                        appendLine("### Summary")
                        appendLine(draft.fullSummary)
                    }
                    appendLine()
                    appendLine("### X Post")
                    appendLine(draft.text)
                }
            }
            appendLine()
            appendLine("---")
            appendLine("Generated by Namma Omnibrief")
        }
        return headline to body
    }

    fun sendArticleViaGmail() {
        val (headline, body) = buildArticleBriefMarkdown()
        launchEmailIntent("[Executive Brief] $headline", body, "Article Brief")
    }

    fun exportArticleToGoogleDrive() {
        val (headline, body) = buildArticleBriefMarkdown()
        exportContentToDrive(
            fileNamePrefix = "Article_Brief",
            title = headline,
            content = body,
            summary = postDrafts.value.firstOrNull()?.text ?: activePostDraft.value
        )
    }

    private fun launchEmailIntent(subject: String, body: String, label: String) {
        try {
            // First attempt: Explicitly target official Gmail app
            val gmailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                setPackage("com.google.android.gm")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(gmailIntent)
            exportStatus.value = "Launched Gmail with $label"
            Toast.makeText(context, "Opening Gmail...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Fallback: General email client chooser
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(emailIntent)
                exportStatus.value = "Opened email client"
            } catch (e2: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send $label via Email"))
                exportStatus.value = "Opened share chooser"
            }
        }
    }

    private fun exportContentToDrive(
        fileNamePrefix: String,
        title: String,
        content: String,
        summary: String
    ) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
            val fileName = "${fileNamePrefix}_${cleanTitle}_$timeStamp.md"
            val file = File(context.cacheDir, fileName)
            file.writeText(content)

            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // First attempt: Target Google Drive app if installed
            var launchedDirect = false
            try {
                val driveDirectIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, summary)
                    setPackage("com.google.android.apps.docs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(driveDirectIntent)
                launchedDirect = true
                exportStatus.value = "Opened Google Drive"
                Toast.makeText(context, "Saving to Google Drive...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                launchedDirect = false
            }

            if (!launchedDirect) {
                val driveIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, summary)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(driveIntent, "Save to Google Drive / Files")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                exportStatus.value = "Choose Google Drive or Files"
            }
        } catch (e: Exception) {
            exportStatus.value = "Error exporting: ${e.message}"
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun loadSampleConferenceSession() {
        confTopic.value = "Gemini 3.5 & Next-Gen Multimodal Reasoning"
        confSpeaker.value = "Sundar Pichai & Demis Hassabis"
        confTitle.value = "Google I/O Global Summit"
        conferenceError.value = null
        conferenceReportResult.value = ConferenceReportResult(
            sessionTitle = "Google I/O: Next-Gen Multimodal Reasoning",
            speaker = "Sundar Pichai & Demis Hassabis",
            executiveSummary = "Comprehensive overview of 2M+ token high-context windows, real-time multimodal latency optimizations, and agentic workflows integrated into enterprise workflows.",
            keyTakeaways = listOf(
                "2M token context enables entire codebases and hours of video analysis in a single prompt.",
                "Sub-second multimodal response loops drastically improve mobile on-device assistants.",
                "Sovereign data isolation models meet stringent healthcare and financial data compliance."
            ),
            slideInsights = listOf(
                "Architecture slide: unified multimodal encoder shared across text, vision and audio.",
                "Benchmark slide: 40% latency reduction versus the previous generation."
            ),
            actionItems = listOf(
                "Audit internal knowledge bases for long-context ingestion.",
                "Prototype multimodal visual document pipelines."
            ),
            fullReportMarkdown = """# Conference Executive Brief: Google I/O Global Summit
**Session**: Gemini 3.5 & Next-Gen Multimodal Reasoning  
**Speaker**: Sundar Pichai & Demis Hassabis  

## Executive Summary
Google I/O spotlighted major breakthroughs in high-context multimodal reasoning, reducing latency by 40% while expanding document analysis capabilities.

## Key Takeaways
- 2M token context enables deep codebase and financial filings analysis.
- New enterprise guardrails for enterprise privacy.

---
*Synthesized by Namma Omnibrief*"""
        )
        Toast.makeText(context, "Loaded Google I/O keynote & synthesized report", Toast.LENGTH_SHORT).show()
    }

    fun loadBengaluruTechConferenceSample() {
        confTitle.value = "Bengaluru Tech Summit 2026"
        confTopic.value = "Autonomous Urban Transit & Sovereign AI Compute"
        confSpeaker.value = "Dr. C. N. Ashwath Narayan & Tech Leaders"
        conferenceError.value = null
        conferenceReportResult.value = ConferenceReportResult(
            sessionTitle = "Bengaluru Tech Summit 2026: Sovereign AI & Autonomous Urban Infrastructure",
            speaker = "Dr. C. N. Ashwath Narayan & Tech Leaders",
            executiveSummary = "At BTS 2026, Karnataka inaugurated the 10,000-GPU 'Namma AI Grid' compute infrastructure and autonomous mobility algorithms deployed across Bengaluru's expanding Metro network. The session outlined how public-private partnerships in Karnataka are enabling localized Indic AI models while driving 100% renewable power data center development along the Devanahalli tech corridor.",
            keyTakeaways = listOf(
                "10,000 GPUs dedicated for Bengaluru tech ecosystem startups and research institutions.",
                "Namma Metro automated passenger load prediction reducing urban gridlock along Outer Ring Road (ORR).",
                "Sovereign Kannada & Indic LLMs achieving benchmark parity on technical and legal reasoning.",
                "Devanahalli Green Tech Corridor providing 100% renewable power to next-gen AI supercomputing centers."
            ),
            slideInsights = listOf(
                "Topology slide: Namma AI Grid clusters mapped across Electronic City, Whitefield and Koramangala.",
                "Transit slide: predicted vs actual Metro load curves for the Purple and Pink lines."
            ),
            actionItems = listOf(
                "Access Namma AI Grid compute credits via Karnataka Innovation portal.",
                "Integrate Bengaluru Open Mobility transit data into enterprise route planning.",
                "Participate in state-backed sandbox for autonomous robotics logistics."
            ),
            fullReportMarkdown = """# Conference Executive Brief: Bengaluru Tech Summit 2026
**Session**: Sovereign AI & Autonomous Urban Infrastructure  
**Keynote Speakers**: Dr. C. N. Ashwath Narayan & Tech Leaders  
**Location**: Bangalore Palace, Bengaluru  

## Executive Overview
Bengaluru has formally inaugurated its Sovereign AI & Deep Tech roadmap at the Bengaluru Tech Summit 2026. The keynote spotlighted the deployment of the 'Namma AI Grid' compute cluster, bringing 10,000 subsidized high-performance GPUs to startups across Whitefield, Electronic City, and Koramangala.

## Strategic Takeaways
1. **Sovereign Infrastructure**: Dedicated high-performance cluster to reduce dependency on foreign cloud providers.
2. **Urban Transit Optimization**: AI algorithms actively balancing Namma Metro frequencies and feeder bus routes to alleviate Silk Board and ORR congestion.
3. **Indic Language Intelligence**: Foundational models optimized for Kannada enterprise search and civic delivery.

## Next Steps
- Apply for Karnataka Deep-Tech compute credits.
- Partner with Bangalore Metro Rail Corporation (BMRCL) for open mobility APIs.

---
*Synthesized by Namma Omnibrief • Bengaluru's Executive AI*"""
        )
        Toast.makeText(context, "Loaded Namma Bengaluru Tech Summit keynote & brief!", Toast.LENGTH_SHORT).show()
    }

    private fun saveDraftToRoom(
        title: String,
        content: String,
        source: String,
        type: String,
        imageCount: Int = 0,
        hasAudio: Boolean = false,
        audioSecs: Int = 0
    ) {
        viewModelScope.launch {
            val item = BriefItem(
                type = type,
                title = title,
                content = content,
                sourceOrSpeaker = source,
                imageCount = imageCount,
                hasAudio = hasAudio,
                audioDurationSeconds = audioSecs,
                status = "Draft"
            )
            repository.saveWithRollover(item, maxLimit = 10)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.reset()
        audioPlayer.stop()
    }
}

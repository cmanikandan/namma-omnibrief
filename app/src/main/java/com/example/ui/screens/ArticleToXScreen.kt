package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.preferences.AppPreferences
import com.example.ui.components.SourceSelectorDialog
import com.example.ui.components.XPostPreviewCard
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.DraftPostStatus
import com.example.ui.viewmodel.MainViewModel
import java.io.File

/**
 * Widest the source chip in the section header may grow.
 *
 * Publications are short ("WSJ", "The Hindu", "Economic Times") and fit well inside this, so the
 * cap is invisible in normal use. It exists for the outliers — the built-in Bengaluru sample sets
 * a 37-character source — where an uncapped chip would take two thirds of the row and leave the
 * heading rendering one word per line.
 *
 * Deliberately in `dp`, not scaled with the font setting: the point is to bound the *layout*, and
 * the chip's own label is already `maxLines = 1` with an ellipsis, so larger text truncates sooner
 * rather than pushing the heading out again.
 */
private val SOURCE_CHIP_MAX_WIDTH = 150.dp


@Composable
fun ArticleToXScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textInput by viewModel.articleTextInput.collectAsStateWithLifecycle()
    val imageUris by viewModel.articleImageUris.collectAsStateWithLifecycle()
    val currentSource by viewModel.articleSource.collectAsStateWithLifecycle()
    val showSourceDialog by viewModel.showSourceDialog.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingArticle.collectAsStateWithLifecycle()
    val activeDraft by viewModel.activePostDraft.collectAsStateWithLifecycle()
    val isPostingToX by viewModel.isPostingToX.collectAsStateWithLifecycle()
    val postToXStatus by viewModel.postToXStatus.collectAsStateWithLifecycle()
    val errorMessage by viewModel.articleError.collectAsStateWithLifecycle()
    val postDrafts by viewModel.postDrafts.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val isXBlue = viewModel.prefs.isXBlue
    val clipboardManager = LocalClipboardManager.current

    var tempCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Start fresh is confirmed because it discards analysed drafts, and each of those cost a
    // Gemini call — an accidental tap is expensive, not just annoying.
    var showClearConfirm by remember { mutableStateOf(false) }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraPhotoUri?.let { viewModel.addArticleImageUri(it) }
        }
    }

    fun launchCameraInternal() {
        try {
            val photoFile = File(context.cacheDir, "article_scan_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            viewModel.articleError.value = "Camera launch error: ${e.localizedMessage ?: e.message}. You can use 'Upload Image' to select photos."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            viewModel.articleError.value = "Camera permission not granted. You can use 'Upload Image' or 'Load FT Sample' to test."
        }
    }

    // Photo Picker Launcher (Android zero-permission photo picker, capped at the batch limit)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = AppPreferences.MAX_ARTICLE_IMAGES)
    ) { uris ->
        viewModel.addArticleImageUris(uris)
    }

    // Source confirmation dialog
    if (showSourceDialog) {
        SourceSelectorDialog(
            initialSource = currentSource,
            onSourceSelected = { viewModel.setArticleSource(it) },
            onDismiss = { viewModel.showSourceDialog.value = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Start fresh?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    "This removes the attached photos, the pasted text and every draft in the " +
                        "queue. Posts already published to X are not affected.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Text(
                    text = "Clear everything",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseError,
                    modifier = Modifier
                        .clickable {
                            viewModel.clearArticleWorkspace()
                            showClearConfirm = false
                        }
                        .padding(12.dp)
                        .testTag("confirm_clear_workspace_button")
                )
            },
            dismissButton = {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier
                        .clickable { showClearConfirm = false }
                        .padding(12.dp)
                )
            },
            containerColor = ObsidianSurface
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Weighted so it absorbs whatever the capped source chip does not use. Both halves of
            // this have bitten: without the weight the 18sp title claimed the whole row and the
            // chip wrapped mid-word; with the weight but an uncapped chip, a long publication name
            // squeezed the title instead. See the chip's cap below.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ARTICLE TO X DRAFTER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "High-Context Multimodal Analysis",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Source Selector Chip
            Box(
                modifier = Modifier
                    // Capped rather than weighted. Row measures unweighted children first and
                    // hands the remainder to the weighted Column, so a short source such as "FT"
                    // leaves the heading nearly the whole row — which a `weight(1f, fill = false)`
                    // would not, because unused weight space is not redistributed.
                    //
                    // The cap is what stops the mirror-image bug: uncapped, the 37-character
                    // "Deccan Herald / Bengaluru Tech Summit" sample measured 692 px and left the
                    // title 167 px, breaking it one word per line. Over-long sources now ellipsize.
                    .widthIn(max = SOURCE_CHIP_MAX_WIDTH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                    .clickable { viewModel.showSourceDialog.value = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("open_source_picker_chip")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Newspaper,
                        contentDescription = "Source",
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentSource.isBlank()) "Auto-Detect" else currentSource,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // "Start fresh" wipes images, pasted text and the whole draft queue in one tap. Shown only
        // when there is something to discard, so a clean page is not cluttered by a no-op control.
        val hasWorkspaceContent = textInput.isNotBlank() ||
            imageUris.isNotEmpty() ||
            postDrafts.isNotEmpty() ||
            activeDraft.isNotBlank()

        if (hasWorkspaceContent) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("clear_article_workspace_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = RoseError,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start fresh",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoseError,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Input Card: Text / Article / Excerpt
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ARTICLE TEXT / EXCERPT / URL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    if (textInput.isNotBlank()) {
                        Text(
                            text = "Clear",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseError,
                            modifier = Modifier.clickable { viewModel.articleTextInput.value = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { viewModel.articleTextInput.value = it },
                    placeholder = {
                        Text(
                            "Paste newspaper article, WSJ/FT excerpt, opinion piece, or document text here...",
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("article_text_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Action Buttons: Paste, Sample FT & Namma BLR Tech
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                viewModel.pasteFromClipboard(clip)
                            } else {
                                viewModel.articleError.value = "Clipboard is empty. Copy text first or tap 'Namma BLR Tech'."
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("paste_clipboard_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(13.dp), tint = CyanAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.loadSampleArticle()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("load_sample_article_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Sample", modifier = Modifier.size(13.dp), tint = VioletAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sample FT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.loadBengaluruTechSampleArticle()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldVerified),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("load_bengaluru_tech_sample_button")
                    ) {
                        Icon(Icons.Default.LocationCity, contentDescription = "Bengaluru Sample", modifier = Modifier.size(13.dp), tint = EmeraldVerified)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Namma BLR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Media Attachment Buttons: Camera and Gallery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.articleError.value = null
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                launchCameraInternal()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("take_article_photo_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo / Scan", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_article_image_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Image", fontSize = 12.sp)
                    }
                }

                // Attached Images Thumbnails
                if (imageUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${imageUris.size} / ${AppPreferences.MAX_ARTICLE_IMAGES} images • one post generated per image",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (imageUris.size >= AppPreferences.MAX_ARTICLE_IMAGES) AmberPending else CyanAccent
                        )
                        Text(
                            text = "Clear all",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseError,
                            modifier = Modifier
                                .clickable { viewModel.clearArticleImages() }
                                .testTag("clear_article_images_button")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(imageUris) { index, uri ->
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Article photo ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .clickable { viewModel.removeArticleImageUri(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = RoseError,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Analyze Button
        Button(
            onClick = { viewModel.analyzeArticle() },
            enabled = !isAnalyzing,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("analyze_article_button")
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = batchProgress ?: "Analyzing with ${viewModel.prefs.geminiModel}...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (imageUris.size > 1) {
                        "Generate ${imageUris.size} X Post Drafts"
                    } else {
                        "Generate X Post Draft"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Scroll Down Guide Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Scroll down to see every post preview, character counts & export tools",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = CyanAccent
            )
        }

        // Error message banner
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoseError.copy(alpha = 0.15f))
                    .border(1.dp, RoseError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = RoseError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = RoseError
                    )
                }
            }
        }

        // ---- Post queue: one reviewable card per image (plus pasted text) ----
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (postDrafts.size > 1) {
                    "POST QUEUE • ${postDrafts.size} POSTS"
                } else {
                    "POST PREVIEW & APPROVAL"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            if (postDrafts.isNotEmpty()) {
                Text(
                    text = "Clear queue",
                    fontSize = 10.sp,
                    color = RoseError,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { viewModel.clearDrafts() }
                        .testTag("clear_draft_queue_button")
                )
            } else if (activeDraft.isBlank()) {
                Text(
                    text = "Tap preview below to edit",
                    fontSize = 10.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (postDrafts.isEmpty()) {
            // Manual / empty state: single free-form card.
            XPostPreviewCard(
                postDraft = activeDraft,
                source = currentSource.ifBlank { "Auto-Detected" },
                isXBlue = isXBlue,
                isPosting = isPostingToX,
                postedStatus = postToXStatus,
                onDraftChange = { viewModel.activePostDraft.value = it },
                onChangeSourceClick = { viewModel.showSourceDialog.value = true },
                onApproveAndPost = { viewModel.approveAndPostToX() },
                onOpenInXApp = { viewModel.openInXApp() },
                onCopyDraft = { viewModel.copyToClipboard(activeDraft, "X Post Draft") },
                onEmailSummary = { viewModel.sendArticleViaGmail() },
                onDriveExport = { viewModel.exportArticleToGoogleDrive() }
            )
        } else {
            // Batch summary + sequential publish control.
            val postedCount = postDrafts.count { it.status == DraftPostStatus.POSTED }
            val pendingCount = postDrafts.count { it.status != DraftPostStatus.POSTED }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Review each post below, edit anything you want, then publish. " +
                                "Posts are published to X one by one, in order.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$postedCount posted • $pendingCount pending" +
                                (batchProgress?.let { " • $it" } ?: ""),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (postedCount > 0) EmeraldVerified else CyanAccent
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.approveAndPostToX() },
                        enabled = !isPostingToX && pendingCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("approve_and_post_all_button")
                    ) {
                        if (isPostingToX) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(batchProgress ?: "Posting...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (pendingCount > 1) {
                                    "Approve & Post All ($pendingCount) one by one"
                                } else {
                                    "Approve & Post"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (postToXStatus != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = postToXStatus ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (postToXStatus?.contains("fail", ignoreCase = true) == true) {
                                RoseError
                            } else {
                                EmeraldVerified
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendArticleViaGmail() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("email_batch_button")
                        ) {
                            Text("Email all", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportArticleToGoogleDrive() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drive_batch_button")
                        ) {
                            Text("Save all to Drive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // One preview card per draft.
            postDrafts.forEachIndexed { index, draft ->
                Spacer(modifier = Modifier.height(14.dp))
                val statusLabel = when (draft.status) {
                    DraftPostStatus.PENDING -> "PENDING"
                    DraftPostStatus.POSTING -> "POSTING"
                    DraftPostStatus.POSTED -> "POSTED"
                    DraftPostStatus.FAILED -> "FAILED"
                }
                val statusColor = when (draft.status) {
                    DraftPostStatus.PENDING -> TextSecondary
                    DraftPostStatus.POSTING -> AmberPending
                    DraftPostStatus.POSTED -> EmeraldVerified
                    DraftPostStatus.FAILED -> RoseError
                }

                XPostPreviewCard(
                    postDraft = draft.text,
                    source = draft.source.ifBlank { currentSource.ifBlank { "Auto-Detected" } },
                    isXBlue = isXBlue,
                    isPosting = draft.status == DraftPostStatus.POSTING,
                    postedStatus = draft.error ?: draft.tweetId?.let { "Posted to X (ID: $it)" },
                    onDraftChange = { viewModel.updateDraftText(draft.id, it) },
                    onChangeSourceClick = { viewModel.showSourceDialog.value = true },
                    onApproveAndPost = { viewModel.approveAndPostToX() },
                    onOpenInXApp = { viewModel.openDraftInXApp(draft.text) },
                    onCopyDraft = { viewModel.copyToClipboard(draft.text, "X Post ${index + 1}") },
                    imageUri = draft.imageUri,
                    batchLabel = "${draft.label} • ${index + 1} of ${postDrafts.size}",
                    mainTopic = draft.mainTopic,
                    draftStatusLabel = statusLabel,
                    draftStatusColor = statusColor,
                    primaryActionLabel = "Post all",
                    onRemoveDraft = { viewModel.removeDraft(draft.id) }
                )
            }
        }

        // Generous bottom clearance spacer so all elements scroll up comfortably above navigation
        Spacer(modifier = Modifier.height(120.dp))
    }
}

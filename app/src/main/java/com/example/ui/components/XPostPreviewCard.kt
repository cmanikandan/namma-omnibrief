package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

/**
 * Width cap for the source pill + status badge column in the card header.
 *
 * Keeps a long custom publication name from eating the row and ellipsizing the draft label into
 * uselessness. Sized to fit the longest entry in the built-in publisher list at the default text
 * scale; beyond that the pill ellipsizes, which is acceptable because the same value is also shown
 * on the chip at the top of the screen.
 */
private val SOURCE_COLUMN_MAX_WIDTH = 190.dp

@Composable
fun XPostPreviewCard(
    postDraft: String,
    source: String,
    isXBlue: Boolean,
    isPosting: Boolean,
    postedStatus: String?,
    onDraftChange: (String) -> Unit,
    onChangeSourceClick: () -> Unit,
    onApproveAndPost: () -> Unit,
    onOpenInXApp: () -> Unit,
    onCopyDraft: () -> Unit,
    onEmailSummary: (() -> Unit)? = null,
    onDriveExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Thumbnail of the image this draft was generated from (null for text-only drafts). */
    imageUri: Uri? = null,
    /** Position label such as "Image 2 of 7". */
    batchLabel: String? = null,
    /** The single dominant story Gemini locked on to. */
    mainTopic: String? = null,
    /** PENDING / POSTING / POSTED / FAILED chip text. */
    draftStatusLabel: String? = null,
    draftStatusColor: Color? = null,
    primaryActionLabel: String = "Approve & Post",
    onRemoveDraft: (() -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    val charCount = postDraft.length
    val maxStandard = 280
    val isOverStandard = charCount > maxStandard

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header / Author Identity
            //
            // The identity block carries the weight and the source column does not. Compose
            // measures unweighted children first, so the pill gets the width it actually needs and
            // the label absorbs whatever is left. Without this the row was plain SpaceBetween with
            // no weight anywhere, and a long batchLabel — "Sample: Financial Times • 1 of 1" is
            // enough — took the entire width and squeezed the pill to two pixels. It then wrapped
            // one character per line, grew to about a full screen tall and pushed the post body off
            // the bottom, which reads as "the draft is empty".
            //
            // `fill = false` matters: a short label must not stretch and shove the pill to the far
            // edge. The label ellipsizes rather than wrapping, because two lines of title would
            // misalign it against the 42.dp avatar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageUri != null) {
                        // Thumbnail of the exact photo this draft came from.
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Source image for this draft",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(CyanAccent, VioletAccent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "X",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = batchLabel ?: "Executive Brief",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified X Blue",
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isXBlue) "@User • X Blue Long-Form" else "@User • Standard Post",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Capped as well, so a long custom source cannot starve the label in the other
                // direction. 190.dp fits "Source: Washington Post" at the default text scale.
                Column(
                    modifier = Modifier.widthIn(max = SOURCE_COLUMN_MAX_WIDTH),
                    horizontalAlignment = Alignment.End
                ) {
                    // Source Pill Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianCard)
                            .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onChangeSourceClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("source_badge_clickable")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Just the publication, not "Source: <publication>". The pencil and the
                            // pill styling already say what this is, and the eight characters of
                            // prefix were coming straight out of the draft label's budget — with
                            // them the label ellipsised to "Sample: …", losing the "2 of 3" that
                            // tells you which draft in a batch you are looking at.
                            Text(
                                text = source,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyanAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit source",
                                tint = CyanAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (draftStatusLabel != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusColor = draftStatusColor ?: TextSecondary
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(0.5.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = draftStatusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            if (!mainTopic.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Main topic analysed: $mainTopic",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Post content (View or Edit)
            if (isEditing) {
                OutlinedTextField(
                    value = postDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_post_draft_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 4
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianCard)
                        .border(0.5.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .clickable { isEditing = true }
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (postDraft.isBlank()) "Draft preview will appear here once generated. Tap here to write a custom post directly, or tap 'Namma BLR Tech Sample' above to load a ready draft..." else postDraft,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontStyle = if (postDraft.isBlank()) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        color = if (postDraft.isBlank()) TextSecondary.copy(alpha = 0.7f) else TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Character Counter & Mode Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$charCount chars",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverStandard && !isXBlue) RoseError else CyanAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isXBlue) "(X Blue: Extended Limit Active)" else "Max 280",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Row {
                    Text(
                        text = if (isEditing) "Done Editing" else "Tap text to edit",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CyanAccent,
                        modifier = Modifier.clickable { isEditing = !isEditing }
                    )
                }
            }

            if (postedStatus != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isError = postedStatus.startsWith("Failed", ignoreCase = true) ||
                        postedStatus.startsWith("Error", ignoreCase = true) ||
                        postedStatus.contains("Notice", ignoreCase = true) ||
                        postedStatus.contains("HTTP", ignoreCase = true)

                val bannerBg = if (isError) RoseError.copy(alpha = 0.15f) else EmeraldVerified.copy(alpha = 0.15f)
                val bannerBorder = if (isError) RoseError.copy(alpha = 0.4f) else EmeraldVerified.copy(alpha = 0.4f)
                val bannerColor = if (isError) RoseError else EmeraldVerified

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = bannerColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = postedStatus,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = bannerColor
                            )
                        }

                        if (isError) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap 'X App' below to publish via the pre-populated official composer.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Approve & Post to X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary: Approve & Post to X (Direct API)
                Button(
                    onClick = onApproveAndPost,
                    enabled = !isPosting && postDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("approve_and_post_button")
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posting...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(primaryActionLabel, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Secondary: Open in X App / Web Intent
                OutlinedButton(
                    onClick = onOpenInXApp,
                    enabled = postDraft.isNotBlank(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_in_x_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in X",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("X App")
                }

                // Tertiary: Copy
                OutlinedButton(
                    onClick = onCopyDraft,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("copy_x_post_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy text",
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Optional: drop this draft from the batch queue
                if (onRemoveDraft != null) {
                    OutlinedButton(
                        onClick = onRemoveDraft,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("remove_draft_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove this draft",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Export to Workspace (Gmail & Drive) if available
            if (onEmailSummary != null || onDriveExport != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onEmailSummary != null) {
                        OutlinedButton(
                            onClick = onEmailSummary,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_article_gmail_button")
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Gmail", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send via Gmail", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (onDriveExport != null) {
                        OutlinedButton(
                            onClick = onDriveExport,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_article_drive_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Drive", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Google Drive", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

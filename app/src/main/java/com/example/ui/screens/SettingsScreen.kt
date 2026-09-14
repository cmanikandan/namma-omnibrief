package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppPreferences
import com.example.ui.theme.ChipBlush
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.PreviewAtFontScale
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletAccent
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = viewModel.prefs
    val currentFontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    /** Reads the clipboard, trims stray whitespace/newlines that break tokens, and reports if empty. */
    fun readClipboard(): String? {
        val text = clipboard.getText()?.text?.trim()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return null
        }
        return text
    }

    var geminiKeyInput by remember { mutableStateOf(prefs.customGeminiKeyInput) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var selectedModel by remember { mutableStateOf(prefs.geminiModel) }

    var xTokenInput by remember { mutableStateOf(prefs.xBearerToken) }
    var showXToken by remember { mutableStateOf(false) }

    var xAuthMethod by remember { mutableStateOf(prefs.xAuthMethod) }
    var xClientIdInput by remember { mutableStateOf(prefs.xClientId) }
    var xClientSecretInput by remember { mutableStateOf(prefs.xClientSecret) }
    var xAccessTokenInput by remember { mutableStateOf(prefs.xAccessToken) }
    var xRefreshTokenInput by remember { mutableStateOf(prefs.xRefreshToken) }
    var showClientSecret by remember { mutableStateOf(false) }
    var showAccessToken by remember { mutableStateOf(false) }
    var showRefreshToken by remember { mutableStateOf(false) }
    var isTestingXConnection by remember { mutableStateOf(false) }
    var xConnectionTestMessage by remember { mutableStateOf<String?>(null) }
    var isXConnectionSuccess by remember { mutableStateOf(false) }

    var isXBlue by remember { mutableStateOf(prefs.isXBlue) }
    var attachImage by remember { mutableStateOf(prefs.attachImageToPost) }

    var defaultSource by remember { mutableStateOf(prefs.defaultSource) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "SYSTEM CONFIGURATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "API Keys & Integrations",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // BULK KEY IMPORT: paste an entire .env-style blob and fan it out into every field.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VioletAccent.copy(alpha = 0.08f))
                .border(1.dp, VioletAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = VioletAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BULK IMPORT ALL KEYS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VioletAccent,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Copy a whole block of keys (from your notes, a password manager, or a .env file) " +
                        "and import them all in one tap. One key per line, in either format:",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianCard)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "GEMINI_API_KEY=AIzaSy...\n" +
                            "X_CLIENT_ID=cXo1...\n" +
                            "X_CLIENT_SECRET=...\n" +
                            "X_ACCESS_TOKEN=...\n" +
                            "X_REFRESH_TOKEN=...\n" +
                            "X_BEARER_TOKEN=AAAA...",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val blob = readClipboard() ?: return@OutlinedButton
                        val parsed = parseKeyBlob(blob)
                        if (parsed.isEmpty()) {
                            Toast.makeText(
                                context,
                                "No KEY=value pairs found in the clipboard",
                                Toast.LENGTH_LONG
                            ).show()
                            return@OutlinedButton
                        }
                        var applied = 0
                        parsed[KEY_ALIAS_GEMINI]?.let { geminiKeyInput = it; applied++ }
                        parsed[KEY_ALIAS_CLIENT_ID]?.let { xClientIdInput = it; applied++ }
                        parsed[KEY_ALIAS_CLIENT_SECRET]?.let { xClientSecretInput = it; applied++ }
                        parsed[KEY_ALIAS_ACCESS_TOKEN]?.let { xAccessTokenInput = it; applied++ }
                        parsed[KEY_ALIAS_REFRESH_TOKEN]?.let { xRefreshTokenInput = it; applied++ }
                        parsed[KEY_ALIAS_BEARER]?.let { xTokenInput = it; applied++ }
                        if (applied == 0) {
                            Toast.makeText(
                                context,
                                "Found ${parsed.size} pair(s) but none matched a known key name",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Imported $applied key(s). Review, then tap Save All Settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_import_keys_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import All Keys From Clipboard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Keys never leave this device. They are stored in this app's private storage " +
                            "and sent only to Google and X over HTTPS.",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: GEMINI AI MODEL CONFIGURATION
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GEMINI AI ENGINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Model Selection Chips
                Text(
                    text = "Active Intelligence Model",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val models = AppPreferences.AVAILABLE_MODELS

                models.forEach { (modelId, label) ->
                    val isSelected = selectedModel == modelId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CyanAccent.copy(alpha = 0.15f) else ObsidianCard)
                            .border(
                                1.dp,
                                if (isSelected) CyanAccent else ObsidianBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedModel = modelId
                                prefs.geminiModel = modelId
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanAccent else TextPrimary
                                )
                                Text(
                                    text = modelId,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gemini API Key Input
                Text(
                    text = "Gemini API Key",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Paste your key with the button on the right. Stored only on this device.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = { geminiKeyInput = it },
                    placeholder = { Text("Paste your Gemini API key (AIzaSy...)") },
                    singleLine = true,
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        KeyFieldActions(
                            hasValue = geminiKeyInput.isNotBlank(),
                            isVisible = showGeminiKey,
                            onToggleVisibility = { showGeminiKey = !showGeminiKey },
                            onPaste = { readClipboard()?.let { geminiKeyInput = it } },
                            onClear = { geminiKeyInput = "" },
                            pasteTestTag = "paste_gemini_api_key"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1b: APPEARANCE / TEXT SIZE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APPEARANCE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Text Size",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Applies everywhere in the app, straight away.",
                    fontSize = 12.sp,
                    color = TextTertiary
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppPreferences.FONT_SCALE_OPTIONS.forEach { (scaleValue, label) ->
                    val selected = kotlin.math.abs(currentFontScale - scaleValue) < 0.01f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) ChipBlush else ObsidianCard)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) CyanAccent else ObsidianBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setFontScale(scaleValue) }
                            .testTag("font_scale_${label.lowercase().replace(' ', '_')}")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = TextPrimary
                            )
                            // Preview each option at its own absolute scale. Without this the
                            // literal sp below would itself be scaled by whatever is currently
                            // selected, so every preview would drift as the selection changed.
                            PreviewAtFontScale(
                                optionScale = scaleValue,
                                currentScale = currentFontScale
                            ) {
                                Text(
                                    text = "The quick brown fox",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: X (TWITTER) AUTH CONFIGURATION
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "X (TWITTER) AUTH & ACCOUNT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // X Blue / Premium Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianCard)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "X Blue / Premium Account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Enables high-context longer analysis (> 280 chars) for articles",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isXBlue,
                        onCheckedChange = {
                            isXBlue = it
                            prefs.isXBlue = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyanAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ObsidianBorder
                        ),
                        modifier = Modifier.testTag("x_blue_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Attach Photo Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianCard)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Attach photo to post",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Uploads the photographed article with the post. Needs the " +
                                "'media.write' scope on your X authorisation — turn this off if " +
                                "uploads return 403.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = attachImage,
                        onCheckedChange = {
                            attachImage = it
                            prefs.attachImageToPost = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyanAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ObsidianBorder
                        ),
                        modifier = Modifier.testTag("attach_image_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auth Method Selector
                Text(
                    text = "Authentication Protocol",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOAuthUser = xAuthMethod == "OAUTH2_USER"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOAuthUser) CyanAccent.copy(alpha = 0.12f) else ObsidianCard)
                            .border(1.dp, if (isOAuthUser) CyanAccent else ObsidianBorder, RoundedCornerShape(8.dp))
                            .clickable { xAuthMethod = "OAUTH2_USER" }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OAuth 2.0 User (Direct)",
                            fontSize = 11.sp,
                            fontWeight = if (isOAuthUser) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOAuthUser) CyanAccent else TextPrimary
                        )
                    }

                    val isBearer = xAuthMethod == "BEARER"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isBearer) CyanAccent.copy(alpha = 0.12f) else ObsidianCard)
                            .border(1.dp, if (isBearer) CyanAccent else ObsidianBorder, RoundedCornerShape(8.dp))
                            .clickable { xAuthMethod = "BEARER" }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bearer Token (Legacy)",
                            fontSize = 11.sp,
                            fontWeight = if (isBearer) FontWeight.Bold else FontWeight.Normal,
                            color = if (isBearer) CyanAccent else TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (xAuthMethod == "OAUTH2_USER") {
                    Text(
                        text = "OAuth 2.0 User Context Keys",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                    Text(
                        text = "Full write permissions (tweet.write scope) to post tweets directly to your account.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Client ID
                    OutlinedTextField(
                        value = xClientIdInput,
                        onValueChange = { xClientIdInput = it },
                        label = { Text("Client ID") },
                        placeholder = { Text("Enter Client ID (e.g. cXo1...)") },
                        singleLine = true,
                        trailingIcon = {
                            KeyFieldActions(
                                hasValue = xClientIdInput.isNotBlank(),
                                isVisible = null,
                                onToggleVisibility = null,
                                onPaste = { readClipboard()?.let { xClientIdInput = it } },
                                onClear = { xClientIdInput = "" },
                                pasteTestTag = "paste_x_client_id"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("x_client_id_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Client Secret
                    OutlinedTextField(
                        value = xClientSecretInput,
                        onValueChange = { xClientSecretInput = it },
                        label = { Text("Client Secret") },
                        placeholder = { Text("Enter Client Secret") },
                        singleLine = true,
                        visualTransformation = if (showClientSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            KeyFieldActions(
                                hasValue = xClientSecretInput.isNotBlank(),
                                isVisible = showClientSecret,
                                onToggleVisibility = { showClientSecret = !showClientSecret },
                                onPaste = { readClipboard()?.let { xClientSecretInput = it } },
                                onClear = { xClientSecretInput = "" },
                                pasteTestTag = "paste_x_client_secret"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("x_client_secret_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // User Access Token
                    OutlinedTextField(
                        value = xAccessTokenInput,
                        onValueChange = { xAccessTokenInput = it },
                        label = { Text("User Access Token") },
                        placeholder = { Text("Enter OAuth 2.0 User Access Token") },
                        singleLine = true,
                        visualTransformation = if (showAccessToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            KeyFieldActions(
                                hasValue = xAccessTokenInput.isNotBlank(),
                                isVisible = showAccessToken,
                                onToggleVisibility = { showAccessToken = !showAccessToken },
                                onPaste = { readClipboard()?.let { xAccessTokenInput = it } },
                                onClear = { xAccessTokenInput = "" },
                                pasteTestTag = "paste_x_access_token"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("x_access_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Refresh Token
                    OutlinedTextField(
                        value = xRefreshTokenInput,
                        onValueChange = { xRefreshTokenInput = it },
                        label = { Text("Refresh Token") },
                        placeholder = { Text("Enter Refresh Token (enables auto-renewal)") },
                        singleLine = true,
                        visualTransformation = if (showRefreshToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            KeyFieldActions(
                                hasValue = xRefreshTokenInput.isNotBlank(),
                                isVisible = showRefreshToken,
                                onToggleVisibility = { showRefreshToken = !showRefreshToken },
                                onPaste = { readClipboard()?.let { xRefreshTokenInput = it } },
                                onClear = { xRefreshTokenInput = "" },
                                pasteTestTag = "paste_x_refresh_token"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("x_refresh_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Connection & Refresh Button
                    OutlinedButton(
                        onClick = {
                            // Persist latest values first
                            prefs.xClientId = xClientIdInput
                            prefs.xClientSecret = xClientSecretInput
                            prefs.xAccessToken = xAccessTokenInput
                            prefs.xRefreshToken = xRefreshTokenInput
                            prefs.xAuthMethod = "OAUTH2_USER"

                            isTestingXConnection = true
                            xConnectionTestMessage = null
                            viewModel.testAndRefreshXConnection { success, message ->
                                isTestingXConnection = false
                                isXConnectionSuccess = success
                                xConnectionTestMessage = message
                                if (success) {
                                    xAccessTokenInput = prefs.xAccessToken
                                    xRefreshTokenInput = prefs.xRefreshToken
                                }
                            }
                        },
                        enabled = !isTestingXConnection && xClientIdInput.isNotBlank() && xRefreshTokenInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_x_oauth_connection_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingXConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refreshing & Verifying...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Connection & Refresh Token", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyanAccent)
                        }
                    }

                    if (xConnectionTestMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val bannerBg = if (isXConnectionSuccess) EmeraldVerified.copy(alpha = 0.12f) else RoseError.copy(alpha = 0.12f)
                        val bannerBorder = if (isXConnectionSuccess) EmeraldVerified.copy(alpha = 0.35f) else RoseError.copy(alpha = 0.35f)
                        val bannerColor = if (isXConnectionSuccess) EmeraldVerified else RoseError

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bannerBg)
                                .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isXConnectionSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = xConnectionTestMessage ?: "",
                                    fontSize = 11.sp,
                                    color = bannerColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // X Bearer Token (Legacy)
                    Text(
                        text = "X API Bearer Token (App-Only)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "App-Only bearer token. Used for search and reading endpoints.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = xTokenInput,
                        onValueChange = { xTokenInput = it },
                        placeholder = { Text("Enter X Bearer Token (AAAA...)") },
                        singleLine = true,
                        visualTransformation = if (showXToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            KeyFieldActions(
                                hasValue = xTokenInput.isNotBlank(),
                                isVisible = showXToken,
                                onToggleVisibility = { showXToken = !showXToken },
                                onPaste = { readClipboard()?.let { xTokenInput = it } },
                                onClear = { xTokenInput = "" },
                                pasteTestTag = "paste_x_bearer_token"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("x_bearer_token_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save Settings Button
        Button(
            onClick = {
                prefs.customGeminiKeyInput = geminiKeyInput
                prefs.geminiModel = selectedModel
                prefs.xAuthMethod = xAuthMethod
                prefs.xClientId = xClientIdInput
                prefs.xClientSecret = xClientSecretInput
                prefs.xAccessToken = xAccessTokenInput
                prefs.xRefreshToken = xRefreshTokenInput
                prefs.xBearerToken = xTokenInput
                prefs.isXBlue = isXBlue
                prefs.attachImageToPost = attachImage
                Toast.makeText(context, "All Settings saved successfully", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_settings_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save All Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // Clearance so the Save button is never trapped under the bottom navigation bar.
        Spacer(modifier = Modifier.height(120.dp))
    }
}

/**
 * Compact trailing action row for a credential field: optional Clear, always-available Paste,
 * and an optional show/hide toggle for masked fields.
 */
@Composable
private fun KeyFieldActions(
    hasValue: Boolean,
    isVisible: Boolean?,
    onToggleVisibility: (() -> Unit)?,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    pasteTestTag: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasValue) {
            IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear field",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        IconButton(
            onClick = onPaste,
            modifier = Modifier
                .size(34.dp)
                .testTag(pasteTestTag)
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Paste from clipboard",
                tint = CyanAccent,
                modifier = Modifier.size(17.dp)
            )
        }
        if (onToggleVisibility != null && isVisible != null) {
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle visibility",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

internal const val KEY_ALIAS_GEMINI = "GEMINI"
internal const val KEY_ALIAS_CLIENT_ID = "CLIENT_ID"
internal const val KEY_ALIAS_CLIENT_SECRET = "CLIENT_SECRET"
internal const val KEY_ALIAS_ACCESS_TOKEN = "ACCESS_TOKEN"
internal const val KEY_ALIAS_REFRESH_TOKEN = "REFRESH_TOKEN"
internal const val KEY_ALIAS_BEARER = "BEARER"

/**
 * Parses a pasted blob of credentials into canonical aliases.
 *
 * Deliberately forgiving: accepts `KEY=value` and `KEY: value`, optional `export ` prefixes,
 * surrounding quotes, trailing commas (JSON-ish pastes) and `#` comment lines. Matching is done
 * on substrings so `TWITTER_CLIENT_ID`, `X_CLIENT_ID` and `client id` all resolve to CLIENT_ID.
 */
internal fun parseKeyBlob(raw: String): Map<String, String> {
    val result = mutableMapOf<String, String>()

    raw.lines().forEach { line ->
        val cleaned = line.trim()
            .removePrefix("export ")
            .trim()
            .trim('{', '}', ',')
            .trim()
        if (cleaned.isEmpty() || cleaned.startsWith("#") || cleaned.startsWith("//")) return@forEach

        val separator = cleaned.indexOfFirst { it == '=' || it == ':' }
        if (separator <= 0) return@forEach

        val rawName = cleaned.substring(0, separator).trim().trim('"', '\'').trim()
        val rawValue = cleaned.substring(separator + 1).trim().trim(',').trim().trim('"', '\'').trim()
        if (rawName.isEmpty() || rawValue.isEmpty()) return@forEach

        val name = rawName.uppercase().replace(' ', '_').replace('-', '_')
        val alias = when {
            name.contains("GEMINI") || name.contains("GOOGLE_API") -> KEY_ALIAS_GEMINI
            name.contains("CLIENT_ID") -> KEY_ALIAS_CLIENT_ID
            name.contains("CLIENT_SECRET") -> KEY_ALIAS_CLIENT_SECRET
            name.contains("REFRESH") -> KEY_ALIAS_REFRESH_TOKEN
            name.contains("BEARER") -> KEY_ALIAS_BEARER
            name.contains("ACCESS_TOKEN") -> KEY_ALIAS_ACCESS_TOKEN
            else -> null
        } ?: return@forEach

        // First occurrence wins so a stale duplicate lower in the blob cannot clobber it.
        result.putIfAbsent(alias, rawValue)
    }

    return result
}

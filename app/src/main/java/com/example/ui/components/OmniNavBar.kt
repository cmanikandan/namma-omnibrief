package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

enum class AppDestination(val label: String, val testTag: String) {
    HEADLINES("Today", "nav_tab_headlines"),
    ARTICLE_TO_X("X Drafter", "nav_tab_article_to_x"),
    CONFERENCE("Conference", "nav_tab_conference"),
    HISTORY("Archive", "nav_tab_history"),
    SETTINGS("Settings", "nav_tab_settings")
}

/** Designed label size. What the user sees at the default Text Size setting. */
private const val NAV_LABEL_SP = 11f

/**
 * Floor for the auto-shrink below. Chosen so the longest label ("Conference") still fits a fifth of
 * a compact phone screen at the largest Text Size, and so nothing ever becomes unreadable.
 */
private const val NAV_LABEL_MIN_SP = 8f

/** Step used when shrinking. Small enough to look deliberate, large enough to settle quickly. */
private const val NAV_LABEL_SHRINK_STEP_SP = 0.5f

@Composable
fun OmniNavBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** Number of briefs currently in Room. Shown on the Archive tab; hidden when zero. */
    historyCount: Int = 0
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(width = 0.5.dp, color = ObsidianBorder)
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = ObsidianSurface,
        contentColor = TextSecondary,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedDestination == AppDestination.HEADLINES,
            onClick = { onDestinationSelected(AppDestination.HEADLINES) },
            icon = {
                Icon(
                    imageVector = if (selectedDestination == AppDestination.HEADLINES) {
                        Icons.Filled.Whatshot
                    } else {
                        Icons.Outlined.Whatshot
                    },
                    contentDescription = "Today's headlines"
                )
            },
            label = {
                NavLabel(
                    text = AppDestination.HEADLINES.label,
                    selected = selectedDestination == AppDestination.HEADLINES
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanAccent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag(AppDestination.HEADLINES.testTag)
        )

        NavigationBarItem(
            selected = selectedDestination == AppDestination.ARTICLE_TO_X,
            onClick = { onDestinationSelected(AppDestination.ARTICLE_TO_X) },
            icon = {
                Icon(
                    imageVector = if (selectedDestination == AppDestination.ARTICLE_TO_X) {
                        Icons.AutoMirrored.Filled.Article
                    } else {
                        Icons.AutoMirrored.Outlined.Article
                    },
                    contentDescription = "Article to X Drafter"
                )
            },
            label = {
                NavLabel(
                    text = AppDestination.ARTICLE_TO_X.label,
                    selected = selectedDestination == AppDestination.ARTICLE_TO_X
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanAccent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag(AppDestination.ARTICLE_TO_X.testTag)
        )

        NavigationBarItem(
            selected = selectedDestination == AppDestination.CONFERENCE,
            onClick = { onDestinationSelected(AppDestination.CONFERENCE) },
            icon = {
                Icon(
                    imageVector = if (selectedDestination == AppDestination.CONFERENCE) {
                        Icons.Filled.Mic
                    } else {
                        Icons.Outlined.Mic
                    },
                    contentDescription = "Conference Reporter"
                )
            },
            label = {
                NavLabel(
                    text = AppDestination.CONFERENCE.label,
                    selected = selectedDestination == AppDestination.CONFERENCE
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanAccent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag(AppDestination.CONFERENCE.testTag)
        )

        NavigationBarItem(
            selected = selectedDestination == AppDestination.HISTORY,
            onClick = { onDestinationSelected(AppDestination.HISTORY) },
            icon = {
                Icon(
                    imageVector = if (selectedDestination == AppDestination.HISTORY) {
                        Icons.Filled.History
                    } else {
                        Icons.Outlined.History
                    },
                    contentDescription = "Local Rollover Archive"
                )
            },
            label = {
                NavLabel(
                    text = if (historyCount > 0) {
                        "${AppDestination.HISTORY.label} ($historyCount)"
                    } else {
                        AppDestination.HISTORY.label
                    },
                    selected = selectedDestination == AppDestination.HISTORY
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanAccent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag(AppDestination.HISTORY.testTag)
        )

        NavigationBarItem(
            selected = selectedDestination == AppDestination.SETTINGS,
            onClick = { onDestinationSelected(AppDestination.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedDestination == AppDestination.SETTINGS) {
                        Icons.Filled.Tune
                    } else {
                        Icons.Outlined.Tune
                    },
                    contentDescription = "App Settings"
                )
            },
            label = {
                NavLabel(
                    text = AppDestination.SETTINGS.label,
                    selected = selectedDestination == AppDestination.SETTINGS
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = CyanAccent,
                indicatorColor = CyanAccent,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            ),
            modifier = Modifier.testTag(AppDestination.SETTINGS.testTag)
        )
    }
}

/**
 * A bottom-bar label that is guaranteed to stay on one line.
 *
 * ## Why this is not just a [Text]
 *
 * Five tabs share the screen width, so each label gets about a fifth of it — roughly 70dp on a
 * phone. At the larger Text Size settings a word like "Conference" no longer fits, and Material's
 * default behaviour is to wrap it. That both breaks the word across two lines and makes the whole
 * navigation bar taller, which is what the user reported.
 *
 * Clamping with `maxLines = 1` alone would only trade wrapping for a clipped or ellipsised label,
 * which is no better. Instead the label starts at the size the user asked for and steps down, half
 * a point at a time, until it fits its cell:
 *
 * - Where there is room (short labels, smaller scales) the Text Size setting is honoured in full.
 * - Where there is not, the label shrinks rather than wraps, down to [NAV_LABEL_MIN_SP].
 * - [TextOverflow.Ellipsis] is a last-resort backstop at the floor; in practice the floor is low
 *   enough that it is never reached with the current labels.
 *
 * The measuring passes are hidden with [drawWithContent] so the user never sees the text settle.
 * The search state is keyed on both the text and the ambient font scale, so the label grows back
 * when the user picks a smaller size instead of staying stuck at whatever fitted before.
 */
@Composable
private fun NavLabel(text: String, selected: Boolean) {
    val fontScale = LocalDensity.current.fontScale
    var sizeSp by remember(text, fontScale) { mutableFloatStateOf(NAV_LABEL_SP) }
    var settled by remember(text, fontScale) { mutableStateOf(false) }

    Text(
        text = text,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.2f).sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && sizeSp > NAV_LABEL_MIN_SP) {
                sizeSp = (sizeSp - NAV_LABEL_SHRINK_STEP_SP).coerceAtLeast(NAV_LABEL_MIN_SP)
            } else {
                settled = true
            }
        }
    )
}

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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

enum class AppDestination(val label: String, val testTag: String) {
    ARTICLE_TO_X("X Drafter", "nav_tab_article_to_x"),
    CONFERENCE("Conference", "nav_tab_conference"),
    HISTORY("Archive (10)", "nav_tab_history"),
    SETTINGS("Settings", "nav_tab_settings")
}

@Composable
fun OmniNavBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
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
                Text(
                    text = AppDestination.ARTICLE_TO_X.label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedDestination == AppDestination.ARTICLE_TO_X) FontWeight.Bold else FontWeight.Normal
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
                Text(
                    text = AppDestination.CONFERENCE.label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedDestination == AppDestination.CONFERENCE) FontWeight.Bold else FontWeight.Normal
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
                Text(
                    text = AppDestination.HISTORY.label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedDestination == AppDestination.HISTORY) FontWeight.Bold else FontWeight.Normal
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
                Text(
                    text = AppDestination.SETTINGS.label,
                    fontSize = 11.sp,
                    fontWeight = if (selectedDestination == AppDestination.SETTINGS) FontWeight.Bold else FontWeight.Normal
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

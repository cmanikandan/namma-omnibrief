package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.HeadlineItem
import com.example.ui.theme.BrandGradient
import com.example.ui.theme.ChipBlush
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.RoseError
import com.example.ui.theme.SunsetGradient
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.MainViewModel

/**
 * Home screen: the top Hacker News stories matched against the user's standing interests.
 *
 * This is the landing destination, so it must look alive immediately. While the feed loads it
 * shows shimmering placeholder cards rather than a bare spinner, which makes the wait feel shorter
 * and keeps the layout from jumping when content arrives.
 */
@Composable
fun HeadlinesScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val headlines by viewModel.headlines.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingHeadlines.collectAsStateWithLifecycle()
    val error by viewModel.headlinesError.collectAsStateWithLifecycle()
    val lastUpdated by viewModel.headlinesLastUpdated.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("headlines_list"),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeroHeader(lastUpdated, isLoading, onRefresh = { viewModel.refreshHeadlines() }) }

        if (error != null && headlines.isEmpty()) {
            item { ErrorCard(error!!) { viewModel.refreshHeadlines() } }
        }

        if (isLoading && headlines.isEmpty()) {
            items(6) { ShimmerCard() }
        }

        itemsIndexed(headlines) { index, item ->
            HeadlineCard(
                rank = index + 1,
                item = item,
                onOpen = { viewModel.openHeadline(item) },
                onDraft = { viewModel.draftPostFromHeadline(item) }
            )
        }
    }
}

@Composable
private fun HeroHeader(lastUpdated: String?, isLoading: Boolean, onRefresh: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(BrandGradient))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TODAY ON HACKER NEWS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        letterSpacing = 1.8.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your Top 10",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (lastUpdated != null) "Updated $lastUpdated" else "Fetching the latest…",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f))
                        .clickable(enabled = !isLoading) { onRefresh() }
                        .testTag("refresh_headlines"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh headlines",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "GenAI · OpenAI · Gemini · Google · Anthropic · India Tech",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun HeadlineCard(
    rank: Int,
    item: HeadlineItem,
    onOpen: () -> Unit,
    onDraft: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .testTag("headline_card_$rank")
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                // Rank badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(SunsetGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 21.sp
                    )
                    if (item.domain.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.domain,
                            fontSize = 12.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Interest tags
            if (item.matchedInterests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.matchedInterests.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(ChipBlush)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isFrontPage) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = "On the front page",
                        tint = RoseError,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "${item.points}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${item.commentCount}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.weight(1f))

                // Turn this story into an X draft
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianCard)
                        .clickable { onDraft() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("draft_from_headline_$rank"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Draft",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open article",
                    tint = TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Placeholder card shown while the feed loads. Pulses between two alpha values so the screen reads
 * as "working" rather than "broken".
 */
@Composable
private fun ShimmerCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianCard)
                        .alpha(alpha)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianCard)
                            .alpha(alpha)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianCard)
                            .alpha(alpha)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .width(90.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianCard)
                    .alpha(alpha)
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RoseError.copy(alpha = 0.08f))
            .border(1.dp, RoseError.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Could not load headlines",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RoseError
            )
            Spacer(Modifier.height(4.dp))
            Text(text = message, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(RoseError)
                    .clickable { onRetry() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Retry",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

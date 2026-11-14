/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.crossfade
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.viewmodels.ChartsViewModel

private enum class ChartTab {
    SONGS, ARTISTS, VIDEOS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChartsScreen(
    navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val chartsPage by viewModel.chartsPage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(ChartTab.SONGS) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            viewModel.loadCharts()
        }
    }

    // Extract grouped datasets
    val songItems = remember(chartsPage) {
        chartsPage?.sections
            ?.filter { !it.title.contains("video", ignoreCase = true) }
            ?.flatMap { it.items.filterIsInstance<SongItem>() }
            ?.distinctBy { it.id }
            ?: emptyList()
    }

    val artistItems = remember(chartsPage) {
        chartsPage?.sections
            ?.flatMap { it.items.filterIsInstance<ArtistItem>() }
            ?.distinctBy { it.id }
            ?: emptyList()
    }

    val videoItems = remember(chartsPage) {
        chartsPage?.sections
            ?.filter { it.title.contains("video", ignoreCase = true) }
            ?.flatMap { it.items.filterIsInstance<SongItem>() }
            ?.distinctBy { it.id }
            ?: emptyList()
    }

    val countryCode = YouTube.locale.gl
    val countryName = remember(countryCode) {
        CountryCodeToName[countryCode] ?: countryCode
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.charts),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    com.metrolist.music.ui.component.IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (isLoading && chartsPage == null) {
                ChartsShimmerLoading()
            } else {
                val heroThumbnail = when (selectedTab) {
                    ChartTab.SONGS -> songItems.firstOrNull()?.thumbnail
                    ChartTab.ARTISTS -> artistItems.firstOrNull()?.thumbnail
                    ChartTab.VIDEOS -> videoItems.firstOrNull()?.thumbnail
                } ?: songItems.firstOrNull()?.thumbnail

                val heroTitle = when (selectedTab) {
                    ChartTab.SONGS -> if (songItems.isNotEmpty()) "Top ${songItems.size} • $countryName" else "Charts • $countryName"
                    ChartTab.ARTISTS -> "Top Artists • $countryName"
                    ChartTab.VIDEOS -> "Music Videos • $countryName"
                }

                val heroSubtitle = when (selectedTab) {
                    ChartTab.SONGS -> if (songItems.isNotEmpty()) "Top ${songItems.size} songs • Updated daily" else "Updated daily"
                    ChartTab.ARTISTS -> if (artistItems.isNotEmpty()) "Top ${artistItems.size} artists • Updated daily" else "Updated daily"
                    ChartTab.VIDEOS -> if (videoItems.isNotEmpty()) "Top ${videoItems.size} videos • Updated daily" else "Updated daily"
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 1. Centered Flagship Header
                    item(key = "charts_hero_header") {
                        ChartsHeroHeader(
                            title = heroTitle,
                            subtitle = heroSubtitle,
                            thumbnailUrl = heroThumbnail,
                            onPlayAll = {
                                if (songItems.isNotEmpty()) {
                                    val firstSong = songItems.first()
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(videoId = firstSong.id),
                                            preloadItem = firstSong.toMediaMetadata(),
                                        ),
                                    )
                                }
                            },
                            onShuffle = {
                                if (songItems.isNotEmpty()) {
                                    val randomSong = songItems.random()
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = WatchEndpoint(videoId = randomSong.id),
                                            preloadItem = randomSong.toMediaMetadata(),
                                        ),
                                    )
                                }
                            },
                        )
                    }

                    // 2. Modern Segmented Category Filter Pills
                    item(key = "charts_category_pills") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryPill(
                                label = "Top Songs",
                                count = songItems.size,
                                isSelected = selectedTab == ChartTab.SONGS,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = ChartTab.SONGS
                                },
                            )

                            if (artistItems.isNotEmpty()) {
                                CategoryPill(
                                    label = "Top Artists",
                                    count = artistItems.size,
                                    isSelected = selectedTab == ChartTab.ARTISTS,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTab = ChartTab.ARTISTS
                                    },
                                )
                            }

                            if (videoItems.isNotEmpty()) {
                                CategoryPill(
                                    label = "Music Videos",
                                    count = videoItems.size,
                                    isSelected = selectedTab == ChartTab.VIDEOS,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTab = ChartTab.VIDEOS
                                    },
                                )
                            }
                        }
                    }

                    // 3. Tab Contents
                    when (selectedTab) {
                        ChartTab.SONGS -> {
                            if (songItems.isEmpty()) {
                                item {
                                    EmptyChartNotice(message = "No trending songs available for this region.")
                                }
                            } else {
                                itemsIndexed(
                                    items = songItems,
                                    key = { _, song -> "rank_song_${song.id}" },
                                ) { index, song ->
                                    val isActive = song.id == mediaMetadata?.id
                                    RankedSongRow(
                                        rank = index + 1,
                                        song = song,
                                        isActive = isActive,
                                        isPlaying = isPlaying,
                                        onPlay = {
                                            if (isActive) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        endpoint = WatchEndpoint(videoId = song.id),
                                                        preloadItem = song.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        },
                                        onMenuClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        ChartTab.ARTISTS -> {
                            if (artistItems.isEmpty()) {
                                item {
                                    EmptyChartNotice(message = "No trending artists available for this region.")
                                }
                            } else {
                                item {
                                    // Render clean 3-column artists grid
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        artistItems.chunked(3).forEachIndexed { rowIndex, rowArtists ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                rowArtists.forEachIndexed { colIndex, artist ->
                                                    val globalRank = rowIndex * 3 + colIndex + 1
                                                    ModernArtistCard(
                                                        rank = globalRank,
                                                        artist = artist,
                                                        onClick = {
                                                            navController.navigate("artist/${artist.id}")
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                }
                                                // Fill empty slots in last row
                                                repeat(3 - rowArtists.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ChartTab.VIDEOS -> {
                            if (videoItems.isEmpty()) {
                                item {
                                    EmptyChartNotice(message = "No trending videos available for this region.")
                                }
                            } else {
                                itemsIndexed(
                                    items = videoItems,
                                    key = { _, video -> "rank_video_${video.id}" },
                                ) { index, video ->
                                    val isActive = video.id == mediaMetadata?.id
                                    RankedSongRow(
                                        rank = index + 1,
                                        song = video,
                                        isActive = isActive,
                                        isPlaying = isPlaying,
                                        onPlay = {
                                            if (isActive) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        endpoint = WatchEndpoint(videoId = video.id),
                                                        preloadItem = video.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        },
                                        onMenuClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = video,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsHeroHeader(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Thumbnail - Large centered with elevation shadow
        Surface(
            modifier = Modifier
                .size(220.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (!thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl.resize(1080, 1080))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trending_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subtitle
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row (Shuffle + Play)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onShuffle,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Surface(
                onClick = onPlayAll,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "pillBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "pillText",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = textColor,
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isSelected) textColor.copy(alpha = 0.85f) else textColor.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun RankedSongRow(
    rank: Int,
    song: SongItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val rowBackground = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }

    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFE2E8F0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color.White.copy(alpha = 0.40f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Rank Indicator
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = String.format("%02d", rank),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (rank <= 3) FontWeight.Black else FontWeight.SemiBold,
                    fontSize = if (rank <= 3) 16.sp else 13.sp,
                ),
                color = rankColor,
            )
        }

        // Song Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(song.thumbnail)
                    .crossfade(true)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Artists
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = if (isActive) Color(0xFFE94E66) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                ),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Action Menu
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ModernArtistCard(
    rank: Int,
    artist: ArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rankBadgeColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFE2E8F0)
        3 -> Color(0xFFCD7F32)
        else -> Color.White.copy(alpha = 0.60f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(artist.thumbnail)
                    .crossfade(true)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = artist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            )

            // Rank Badge
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E24))
                    .border(1.dp, rankBadgeColor, CircleShape)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    ),
                    color = rankBadgeColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
            ),
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyChartNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChartsShimmerLoading() {
    ShimmerHost(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )

            Spacer(modifier = Modifier.height(20.dp))

            TextPlaceholder(
                height = 24.dp,
                modifier = Modifier.width(180.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextPlaceholder(
                height = 14.dp,
                modifier = Modifier.width(130.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            repeat(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        TextPlaceholder(height = 16.dp, modifier = Modifier.fillMaxWidth(0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        TextPlaceholder(height = 12.dp, modifier = Modifier.fillMaxWidth(0.4f))
                    }
                }
            }
        }
    }
}

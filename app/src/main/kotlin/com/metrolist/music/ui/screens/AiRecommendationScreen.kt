/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.utils.glassCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.RecommendationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecommendationScreen(
    viewModel: RecommendationViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    val isSystemDark = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val useDarkTheme = remember(darkTheme, isSystemDark) {
        when (darkTheme) {
            DarkMode.AUTO -> isSystemDark
            DarkMode.ON -> true
            DarkMode.OFF -> false
        }
    }
    val isPureBlack = pureBlack && useDarkTheme

    val colorScheme = MaterialTheme.colorScheme
    val accentPrimary = if (useDarkTheme) Color(0xFF00F2FE) else colorScheme.primary
    val accentSecondary = if (useDarkTheme) Color(0xFF4FACFE) else colorScheme.secondary
    val actualBg = when {
        isPureBlack -> Color.Black
        useDarkTheme -> Color(0xFF070809)
        else -> colorScheme.background
    }
    val cardBg = if (useDarkTheme) Color(0xFF121316).copy(alpha = 0.85f) else colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val primaryTextColor = if (useDarkTheme) Color.White else colorScheme.onSurface
    val secondaryTextColor = if (useDarkTheme) Color.LightGray else colorScheme.onSurfaceVariant
    val buttonContentColor = if (useDarkTheme) Color.Black else colorScheme.onPrimary

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.discover_mix),
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = primaryTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = actualBg,
                    titleContentColor = primaryTextColor
                )
            )
        },
        containerColor = actualBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Seed Song Preview Card
            val cardShape = RoundedCornerShape(24.dp)
            if (mediaMetadata != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .then(
                            if (isPureBlack) {
                                Modifier.background(Color.Black, cardShape)
                            } else {
                                Modifier.glassCard(shape = cardShape, borderColor = accentPrimary, backgroundColor = cardBg)
                            }
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = mediaMetadata?.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recommendation_seed),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = mediaMetadata?.title ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "",
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .then(
                            if (isPureBlack) {
                                Modifier.background(Color.Black, cardShape)
                            } else {
                                Modifier.glassCard(shape = cardShape, backgroundColor = cardBg)
                            }
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.no_track_playing_mix_fallback),
                            fontSize = 14.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            // Generate Button
            Button(
                onClick = {
                    viewModel.generateRecommendations(mediaMetadata?.id ?: "")
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(accentPrimary, accentSecondary)),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.generate_discovery_mix),
                        color = buttonContentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // UI Content based on state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState) {
                    is RecommendationViewModel.UiState.Idle -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.radio),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.tap_generate_discovery_mix),
                                color = secondaryTextColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    is RecommendationViewModel.UiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentPrimary,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.fetching_smart_recommendations),
                                color = primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    is RecommendationViewModel.UiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = colorScheme.error, fontSize = 14.sp)
                        }
                    }
                    is RecommendationViewModel.UiState.Success -> {
                        val mixQueueTitle = stringResource(R.string.discover_mix)
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.generated_queue_count, state.songs.size),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = primaryTextColor
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val playlistName = "AI Mix - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                                val playlistEntity = PlaylistEntity(
                                                    name = playlistName,
                                                    isLocal = true,
                                                    bookmarkedAt = LocalDateTime.now()
                                                )
                                                database.insert(playlistEntity)
                                                val createdPlaylist = database.playlistBlocking(playlistEntity.id)
                                                if (createdPlaylist != null) {
                                                    state.songs.forEach { songItem ->
                                                        database.insert(songItem.toMediaMetadata())
                                                    }
                                                    database.addSongsToPlaylist(
                                                        createdPlaylist,
                                                        state.songs.map { it.id to it.setVideoId }
                                                    )
                                                    val message = context.getString(R.string.saved_as_playlist, playlistName)
                                                    withContext(Dispatchers.Main) {
                                                        snackbarHostState.showSnackbar(message)
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.save_as_playlist), color = accentPrimary)
                                    }

                                    TextButton(
                                        onClick = {
                                            playerConnection?.playQueue(
                                                ListQueue(
                                                    title = mixQueueTitle,
                                                    items = state.songs.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    ) {
                                        Text(stringResource(R.string.play_all), color = accentSecondary)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.songs, key = { it.id }) { song ->
                                    val itemShape = RoundedCornerShape(12.dp)
                                    val itemBg = if (isPureBlack) Color.Black else cardBg
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(itemShape)
                                            .clickable {
                                                playerConnection?.playQueue(
                                                    ListQueue(
                                                        title = mixQueueTitle,
                                                        items = state.songs.map { it.toMediaItem() },
                                                        startIndex = state.songs.indexOf(song)
                                                    )
                                                )
                                            }
                                            .then(
                                                if (isPureBlack) {
                                                    Modifier.background(itemBg, itemShape)
                                                } else {
                                                    Modifier.glassCard(shape = itemShape, backgroundColor = itemBg)
                                                }
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = song.thumbnail,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artists.joinToString(", ") { it.name },
                                                fontSize = 12.sp,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

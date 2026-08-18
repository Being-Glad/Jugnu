/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.utils.glassCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.metrolist.innertube.models.SongItem
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    val isSystemDark = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val isPureBlack = pureBlack && isSystemDark

    // Neon colors for premium theme
    val neonTeal = Color(0xFF00F2FE)
    val neonViolet = Color(0xFF4FACFE)
    val obsidianBg = Color(0xFF070809)
    val actualBg = if (isPureBlack) Color.Black else obsidianBg
    val cardBg = Color(0xFF121316).copy(alpha = 0.8f)

    // Animation for breathing button/glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Discover Mix",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = actualBg,
                    titleContentColor = Color.White
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
                                Modifier.glassCard(shape = cardShape, borderColor = neonTeal)
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
                                text = "RECOMMENDATION SEED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = neonTeal,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = mediaMetadata?.title ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "",
                                fontSize = 14.sp,
                                color = Color.LightGray,
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
                                Modifier.glassCard(shape = cardShape)
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
                            text = "No track playing. Generating mix from Liked Songs fallback.",
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // Glowing Generate Button
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
                            Brush.linearGradient(listOf(neonTeal, neonViolet)),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Generate Spotify-style Mix",
                        color = Color.Black,
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
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap Generate to build your personalized discovery mix.",
                                color = Color.Gray,
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
                                color = neonTeal,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fetching smart recommendations...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    is RecommendationViewModel.UiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = Color.Red, fontSize = 14.sp)
                        }
                    }
                    is RecommendationViewModel.UiState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Generated Queue (${state.songs.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
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
                                                    withContext(Dispatchers.Main) {
                                                        // Inform user or show success
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Save as Playlist", color = neonTeal)
                                    }

                                    TextButton(
                                        onClick = {
                                            playerConnection?.playQueue(
                                                ListQueue(
                                                    title = "AI Discover Mix",
                                                    items = state.songs.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Play All", color = neonViolet)
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
                                                        title = "AI Discover Mix",
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
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artists.joinToString(", ") { it.name },
                                                fontSize = 12.sp,
                                                color = Color.LightGray,
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

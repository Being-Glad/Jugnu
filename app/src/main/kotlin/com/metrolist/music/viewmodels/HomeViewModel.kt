/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.WrappedSeenKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.ui.screens.wrapped.WrappedAudioService
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import com.metrolist.music.utils.CountryDetector
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)
    val trendingSongs = MutableStateFlow<List<SongItem>?>(null)
    val trendingArtists = MutableStateFlow<List<ArtistItem>?>(null)
    val trendingArtistImages = MutableStateFlow<Map<String, String>>(emptyMap())
    private val allTrendingSongsList = mutableListOf<SongItem>()

    // Official API data for podcast sections
    val savedPodcastShows = MutableStateFlow<List<com.metrolist.innertube.models.PodcastItem>>(emptyList())
    val episodesForLater = MutableStateFlow<List<SongItem>>(emptyList())

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {
                // Keep Listening (History/Heavy Rotation)
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            is com.metrolist.music.db.entities.Artist -> ArtistItem(
                                id = item.id,
                                title = item.title,
                                thumbnail = item.thumbnailUrl,
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                // Quick Picks
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }
            
            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        is com.metrolist.music.db.entities.Artist -> otherSources.add(ArtistItem(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnailUrl,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

	val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit {
                it[WrappedSeenKey] = true
            }
        }
    }
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    // Track if we're currently processing account data
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)
        
        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }
        
        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.events().first().firstOrNull()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = LocalDateTime.now().minusWeeks(4)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
            
            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    private suspend fun fetchTrendingSongs(forceRefresh: Boolean = false) {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        val contentCountry = context.dataStore.get(ContentCountryKey, SYSTEM_DEFAULT)
        val activeCountry = when (contentCountry) {
            CountryDetector.GLOBAL_CODE -> "GLOBAL"
            SYSTEM_DEFAULT -> CountryDetector.detectCountry(context)
            else -> contentCountry
        }
        val countryChanged = cachedTrendingCountry != null && cachedTrendingCountry != activeCountry

        val now = System.currentTimeMillis()
        val cachedSongs = cachedTrendingSongs
        if (!forceRefresh && !countryChanged && !cachedSongs.isNullOrEmpty() && (now - cachedTrendingFetchedAtMs) <= TRENDING_CACHE_TTL_MS) {
            allTrendingSongsList.clear()
            allTrendingSongsList.addAll(cachedSongs)
            trendingSongs.value = cachedSongs.take(12)
            cachedTrendingArtists?.takeIf { it.isNotEmpty() }?.let {
                trendingArtists.value = it
            }
            if (trendingArtists.value.isNullOrEmpty()) {
                viewModelScope.launch(Dispatchers.IO) { fetchTrendingArtistImages() }
            }
            return
        }

        Timber.tag("HomeViewModel").d("Fetching fresh trending charts for country=$activeCountry (forceRefresh=$forceRefresh, countryChanged=$countryChanged)")

        // 1. Try YouTube Music Charts page (real regional Trending Songs + Top Artists)
        YouTube.getChartsPage().onSuccess { page ->
            val artistItems = page.sections
                .sortedByDescending { section ->
                    when {
                        section.title.contains("artist", ignoreCase = true) -> 2
                        section.chartType == ChartsPage.ChartType.TRENDING -> 1
                        else -> 0
                    }
                }
                .flatMap { it.items.filterIsInstance<ArtistItem>() }
                .distinctBy { it.id }

            if (artistItems.isNotEmpty()) {
                val topArtists = artistItems.take(12)
                cachedTrendingArtists = topArtists
                trendingArtists.value = topArtists
            }

            val songSections = page.sections.filter { section ->
                section.items.any { it is SongItem }
            }
            val preferredSongSection =
                songSections.firstOrNull {
                    it.chartType == ChartsPage.ChartType.TRENDING &&
                        !it.title.contains("video", ignoreCase = true)
                }
                    ?: songSections.firstOrNull {
                        it.title.contains("top songs", ignoreCase = true)
                    }
                    ?: songSections.firstOrNull {
                        !it.title.contains("video", ignoreCase = true)
                    }
                    ?: songSections.firstOrNull()

            val items = (preferredSongSection?.items ?: emptyList())
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .filterYoutubeShorts(hideYoutubeShorts)

            if (items.isNotEmpty()) {
                Timber.tag("HomeViewModel").d("Loaded ${items.size} trending songs from Charts page (${preferredSongSection?.title})")
                allTrendingSongsList.clear()
                allTrendingSongsList.addAll(items)
                cachedTrendingSongs = items
                cachedTrendingCountry = activeCountry
                cachedTrendingFetchedAtMs = System.currentTimeMillis()
                trendingSongs.value = selectFeaturedTrending(items, rotateOnRefresh = forceRefresh && !cachedSongs.isNullOrEmpty())
                saveTrendingDiskCache(context, items)
                if (artistItems.isEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) { fetchTrendingArtistImages(refreshMissing = true) }
                }
                return
            }
        }.onFailure {
            Timber.tag("HomeViewModel").w(it, "Charts page failed")
            reportException(it)
        }

        // 2. Try global trending playlist
        YouTube.playlist("PL4fGSI1pDJn5kI81J1fYxT5m1JIuxEUMS").onSuccess { page ->
            val items = page.songs
                .distinctBy { it.id }
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .filterYoutubeShorts(hideYoutubeShorts)
            if (items.isNotEmpty()) {
                allTrendingSongsList.clear()
                allTrendingSongsList.addAll(items)
                cachedTrendingSongs = items
                cachedTrendingFetchedAtMs = System.currentTimeMillis()
                trendingSongs.value = items.take(12)
                saveTrendingDiskCache(context, items)
                viewModelScope.launch(Dispatchers.IO) { fetchTrendingArtistImages(refreshMissing = true) }
                return
            }
        }.onFailure {
            reportException(it)
        }

        // 3. Try global hits playlist
        YouTube.playlist("PL4fGSI1pDJn6jXS_O5LiqBh3UXLKXkHs7").onSuccess { page ->
            val items = page.songs
                .distinctBy { it.id }
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .filterYoutubeShorts(hideYoutubeShorts)
            if (items.isNotEmpty()) {
                allTrendingSongsList.clear()
                allTrendingSongsList.addAll(items)
                cachedTrendingSongs = items
                cachedTrendingFetchedAtMs = System.currentTimeMillis()
                trendingSongs.value = items.take(12)
                saveTrendingDiskCache(context, items)
                viewModelScope.launch(Dispatchers.IO) { fetchTrendingArtistImages(refreshMissing = true) }
                return
            }
        }.onFailure {
            reportException(it)
        }

        // 4. Try homePage sections (do NOT persist into cachedTrendingFetchedAtMs so next open retries Charts)
        val homeSongs = homePage.value?.sections?.flatMap { it.items }
            ?.filterIsInstance<SongItem>()
            ?.distinctBy { it.id }
            ?.filterExplicit(hideExplicit)
            ?.filterVideoSongs(hideVideoSongs)
            ?.filterYoutubeShorts(hideYoutubeShorts)
            ?: emptyList()

        if (homeSongs.isNotEmpty()) {
            allTrendingSongsList.clear()
            allTrendingSongsList.addAll(homeSongs)
            cachedTrendingSongs = homeSongs
            trendingSongs.value = homeSongs.take(12)
            fetchTrendingArtistImages(refreshMissing = true)
            return
        }

        // 5. Try quickPicks only if trendingSongs is still empty (offline fallback, never cached)
        if (trendingSongs.value.isNullOrEmpty()) {
            val quickSongs = quickPicks.value?.map { song ->
                SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { com.metrolist.innertube.models.Artist(name = it.name, id = it.id) },
                    thumbnail = song.thumbnailUrl ?: "",
                    explicit = false
                )
            } ?: emptyList()

            if (quickSongs.isNotEmpty()) {
                allTrendingSongsList.clear()
                allTrendingSongsList.addAll(quickSongs)
                trendingSongs.value = quickSongs.take(12)
                fetchTrendingArtistImages(refreshMissing = false)
            }
        }
    }

    /**
     * Keeps the top 3 chart leaders anchored while rotating the remaining slots across top-25 chart tracks
     * on explicit refreshes so pull-to-refresh surfaces fresh chart songs.
     */
    private fun selectFeaturedTrending(items: List<SongItem>, rotateOnRefresh: Boolean): List<SongItem> {
        if (!rotateOnRefresh || items.size <= 12) return items.take(12)
        val anchors = items.take(3)
        val pool = items.drop(3).take(22).shuffled()
        return (anchors + pool.take(9)).distinctBy { it.id }
    }

    fun refreshTrendingSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchTrendingSongs(forceRefresh = true)
        }
    }

    /**
     * Fetches real artist profile photos for each unique artist in trendingSongs.
     * Replaces album art with actual artist headshots via YouTube artist page.
     */
    private suspend fun fetchTrendingArtistImages(refreshMissing: Boolean = false) {
        val songs = trendingSongs.value ?: return
        val uniqueArtists = songs
            .mapNotNull { song -> song.artists.firstOrNull { it.id != null } }
            .distinctBy { it.id!! }
            .take(12)

        val images = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())
        if (!refreshMissing && !cachedArtistImages.isNullOrEmpty()) {
            images.putAll(cachedArtistImages!!)
        }

        coroutineScope {
            uniqueArtists.forEach { artist ->
                launch(Dispatchers.IO) {
                    val artistId = artist.id!!
                    if (!refreshMissing && images.containsKey(artistId)) return@launch
                    val dbThumb = database.getArtistById(artistId)?.thumbnailUrl
                    if (!dbThumb.isNullOrEmpty()) {
                        images[artistId] = dbThumb
                    } else {
                        YouTube.artist(artistId).onSuccess { page ->
                            val thumb = page.artist.thumbnail
                            if (!thumb.isNullOrEmpty()) {
                                images[artistId] = thumb
                            }
                        }
                    }
                }
            }
        }
        if (images.isNotEmpty()) {
            cachedArtistImages = images.toMap()
            trendingArtistImages.value = images.toMap()
        }
    }

    private suspend fun load() {
        isLoading.value = true
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val fromTimeStamp = LocalDateTime.now().minusWeeks(2)

        // Phase 1: Fast local DB queries (completes in ~10ms)
        coroutineScope {
            launch(Dispatchers.IO) { getQuickPicks() }

            launch(Dispatchers.IO) {
                forgottenFavorites.value = database.forgottenFavorites().first()
                    .filterVideoSongs(hideVideoSongs).shuffled().take(20)
            }

            launch(Dispatchers.IO) {
                val songs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
                    .filterVideoSongs(hideVideoSongs).shuffled().take(10)
                val albums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
                    .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                val artists = database.mostPlayedArtists(fromTimeStamp).first()
                    .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                keepListening.value = (songs + albums + artists).shuffled()
            }
        }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }

        // Local data is ready! Set isLoading to false INSTANTLY (< 15ms) so screen renders!
        isLoading.value = false

        // Phase 2: All heavy network operations run concurrently in background without blocking screen rendering
        viewModelScope.launch(Dispatchers.IO) { fetchTrendingSongs(forceRefresh = true) }

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.home().onSuccess { page ->
                homePage.value = page.copy(
                    sections = page.sections.mapNotNull { section ->
                        val filtered = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts)
                        if (filtered.isEmpty()) null else section.copy(items = filtered)
                    }
                )
            }.onFailure { reportException(it) }
        }

        if (YouTube.cookie != null) {
            viewModelScope.launch(Dispatchers.IO) { loadAccountPlaylists() }
        }

        viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }
        viewModelScope.launch(Dispatchers.IO) { getCommunityPlaylists() }

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.explore().onSuccess { page ->
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                )
            }.onFailure { reportException(it) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        page.sections.takeLast(3).forEach { section -> items += section.items }
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .distinctBy { item -> item.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(12)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(10) +
                                page.albums.shuffled().take(5) +
                                page.artists.shuffled().take(3) +
                                page.playlists.shuffled().take(3))
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val albumRecommendations = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .mapNotNull { album ->
                    val items = mutableListOf<YTItem>()
                    YouTube.album(album.id).onSuccess { page ->
                        page.otherVersions.let { items += it }
                    }
                    album.artists.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.lastOrNull()?.items?.let { items += it }
                        }
                    }
                    SimilarRecommendation(
                        title = album,
                        items = items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(10)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            similarRecommendations.value = (artistRecommendations + songRecommendations + albumRecommendations).shuffled()
            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip

            // Fetch podcast-specific data when podcasts chip is selected
            if (chip.title.contains("Podcast", ignoreCase = true)) {
                fetchPodcastData()
            }
        }
    }

    private suspend fun fetchPodcastData() {
        // Fetch saved podcast shows from official API
        YouTube.savedPodcastShows().onSuccess { shows ->
            savedPodcastShows.value = shows
        }.onFailure {
            reportException(it)
        }

        // Fetch episodes for later from official API
        YouTube.episodesForLater().onSuccess { episodes ->
            episodesForLater.value = episodes
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // If a chip is selected, reload the chip's content instead of the default home
            val currentChip = selectedChip.value
            if (currentChip != null) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, true)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val nextSections = YouTube.home(params = currentChip.endpoint?.params).getOrNull()
                if (nextSections != null) {
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        sections = nextSections.sections.map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                    )
                }
            } else {
                load()
            }
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
            if (selectedChip.value != null) {
                fetchTrendingSongs(forceRefresh = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wrappedManager.dispose()
    }

    init {
        // Fast restore of trending carousel from disk cache on cold launch
        if (cachedTrendingSongs.isNullOrEmpty()) {
            val diskCached = loadTrendingDiskCache(context)
            if (!diskCached.isNullOrEmpty()) {
                cachedTrendingSongs = diskCached
            }
        }

        // Instant pre-population of trending songs & artists from cache or local DB while fresh fetch runs
        if (!cachedTrendingSongs.isNullOrEmpty()) {
            allTrendingSongsList.clear()
            allTrendingSongsList.addAll(cachedTrendingSongs!!)
            trendingSongs.value = cachedTrendingSongs!!.take(12)
            cachedTrendingArtists?.takeIf { it.isNotEmpty() }?.let {
                trendingArtists.value = it
            }
            if (!cachedArtistImages.isNullOrEmpty()) {
                trendingArtistImages.value = cachedArtistImages!!
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val dbSongs = database.quickPicks().first()
                if (dbSongs.isNotEmpty()) {
                    val items = dbSongs.take(8).map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { com.metrolist.innertube.models.Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    if (trendingSongs.value == null) {
                        trendingSongs.value = items
                    }
                }
            }
        }

        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO) {
            showWrappedCard.collect { shouldShow ->
                if (shouldShow && !wrappedManager.state.value.isDataReady) {
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect

                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {
                            YouTube.cookie = cookie

                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }

        // Listen for ContentCountryKey preference changes and reload trending charts instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[ContentCountryKey] }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    fetchTrendingSongs(forceRefresh = true)
                }
        }
    }

    private var isHomeDataLoaded = false

    fun onHomeScreenOpened() {
        if (!isHomeDataLoaded) {
            loadHomeData()
            return
        }
        val isTrendingStale = (System.currentTimeMillis() - cachedTrendingFetchedAtMs) > TRENDING_CACHE_TTL_MS
        if (isTrendingStale) {
            viewModelScope.launch(Dispatchers.IO) {
                fetchTrendingSongs(forceRefresh = true)
            }
        }
    }

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
    companion object {
        private const val TRENDING_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val TRENDING_CACHE_FILE = "trending_carousel_cache.json"
        internal var cachedTrendingSongs: List<SongItem>? = null
        private var cachedTrendingArtists: List<ArtistItem>? = null
        private var cachedArtistImages: Map<String, String>? = null
        private var cachedTrendingCountry: String? = null
        private var cachedTrendingFetchedAtMs: Long = 0L

        private fun saveTrendingDiskCache(context: Context, items: List<SongItem>) {
            try {
                val array = JSONArray()
                items.take(30).forEach { item ->
                    val obj = JSONObject()
                    obj.put("id", item.id)
                    obj.put("title", item.title)
                    obj.put("thumbnail", item.thumbnail)
                    obj.put("explicit", item.explicit)
                    val artistsArray = JSONArray()
                    item.artists.forEach { artist ->
                        val aObj = JSONObject()
                        aObj.put("name", artist.name)
                        aObj.put("id", artist.id)
                        artistsArray.put(aObj)
                    }
                    obj.put("artists", artistsArray)
                    array.put(obj)
                }
                val file = File(context.cacheDir, TRENDING_CACHE_FILE)
                file.writeText(array.toString())
            } catch (e: Exception) {
                Timber.w(e, "Failed to save trending disk cache")
            }
        }

        private fun loadTrendingDiskCache(context: Context): List<SongItem>? {
            return try {
                val file = File(context.cacheDir, TRENDING_CACHE_FILE)
                if (!file.exists()) return null
                val text = file.readText()
                if (text.isBlank()) return null
                val array = JSONArray(text)
                val list = mutableListOf<SongItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    val thumbnail = obj.optString("thumbnail", "")
                    val explicit = obj.optBoolean("explicit", false)
                    val artistsArray = obj.optJSONArray("artists")
                    val artists = mutableListOf<Artist>()
                    if (artistsArray != null) {
                        for (j in 0 until artistsArray.length()) {
                            val aObj = artistsArray.getJSONObject(j)
                            artists.add(
                                Artist(
                                    name = aObj.getString("name"),
                                    id = if (aObj.has("id") && !aObj.isNull("id")) aObj.getString("id") else null
                                )
                            )
                        }
                    }
                    list.add(
                        SongItem(
                            id = id,
                            title = title,
                            artists = artists,
                            thumbnail = thumbnail,
                            explicit = explicit
                        )
                    )
                }
                if (list.isNotEmpty()) list else null
            } catch (e: Exception) {
                Timber.w(e, "Failed to load trending disk cache")
                null
            }
        }
    }
}

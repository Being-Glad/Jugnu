/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.constants.SongSortType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationEngine @Inject constructor(
    private val localKotlinRecommender: LocalKotlinRecommender,
    private val databaseDao: DatabaseDao,
    private val httpClient: HttpClient,
    private val jsonParser: Json,
) {
    private val mbidCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MBID_CACHE_MAX_SIZE
    }

    suspend fun getRecommendations(currentSongId: String, limit: Int = 10): List<SongItem> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()

        val seedSongId = if (currentSongId.isNotBlank()) {
            currentSongId
        } else {
            val quickPicksList = databaseDao.quickPicks().first()
            val likedSongsList = databaseDao.likedSongs(SongSortType.CREATE_DATE, true).first()
            val candidateSongs = (quickPicksList + likedSongsList).map { it.id }.distinct()
            if (candidateSongs.isNotEmpty()) {
                candidateSongs.random()
            } else {
                ""
            }
        }

        if (seedSongId.isNotBlank()) {
            try {
                Timber.tag("RecommendationEngine").d("Fetching recommendations from YouTube Next API for seed: $seedSongId")
                val ytNextResult = YouTube.next(WatchEndpoint(videoId = seedSongId)).getOrNull()
                val ytRecommendations = ytNextResult?.items
                    .orEmpty()
                    .filter { it.id != seedSongId }
                    .distinctBy { it.id }
                if (ytRecommendations.isNotEmpty()) {
                    Timber.tag("RecommendationEngine").d("Successfully fetched ${ytRecommendations.size} recommendations from YouTube")
                    return@withContext ytRecommendations.take(limit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("RecommendationEngine").w(e, "Failed to fetch recommendations from YouTube Next API")
            }

            try {
                // Fallback to ListenBrainz/MusicBrainz
                val currentSong = databaseDao.getSongById(seedSongId)
                if (currentSong != null) {
                    val songTitle = currentSong.song.title
                    val artistName = currentSong.artists.firstOrNull()?.name ?: ""
                    if (artistName.isNotEmpty()) {
                        val mbid = fetchMusicBrainzId(songTitle, artistName)
                        if (mbid != null) {
                            val similarTracks = fetchListenBrainzSimilar(mbid, limit)
                            if (similarTracks.isNotEmpty()) {
                                val resolvedSongs = resolveTracksToYtItems(similarTracks)
                                    .filter { it.id != seedSongId }
                                    .distinctBy { it.id }
                                if (resolvedSongs.isNotEmpty()) {
                                    return@withContext resolvedSongs.take(limit)
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("RecommendationEngine").e(e, "Error fetching from ListenBrainz/MusicBrainz")
            }
        }

        // Fallback to local kotlin recommender
        return@withContext getLocalFallbackSongItems(seedSongId, limit)
            .filter { it.id != seedSongId }
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun getLocalFallbackSongItems(currentSongId: String, limit: Int): List<SongItem> {
        val localRecommendations = localKotlinRecommender.getRecommendations(currentSongId, limit)
        return localRecommendations.map { song ->
            SongItem(
                id = song.id,
                title = song.title,
                artists = song.artists.map { com.metrolist.innertube.models.Artist(it.name, it.id) },
                album = song.album?.let { com.metrolist.innertube.models.Album(it.title, it.id) },
                duration = (song.song.duration).takeIf { it > 0 },
                thumbnail = song.thumbnailUrl ?: "",
                explicit = song.song.explicit,
                libraryAddToken = song.song.libraryAddToken,
                libraryRemoveToken = song.song.libraryRemoveToken
            )
        }
    }

    private suspend fun fetchMusicBrainzId(title: String, artist: String): String? {
        val cacheKey = "${title.trim().lowercase()}\u0000${artist.trim().lowercase()}"
        synchronized(mbidCache) {
            mbidCache[cacheKey]?.let { return it }
        }

        val url = "https://musicbrainz.org/ws/2/recording"
        return try {
            val escapedTitle = escapeLuceneTerm(title)
            val escapedArtist = escapeLuceneTerm(artist)
            val responseText = httpClient.get(url) {
                header("User-Agent", "Jugnu/1.0.0")
                parameter("query", "recording:\"$escapedTitle\" AND artist:\"$escapedArtist\"")
                parameter("fmt", "json")
            }.bodyAsText()

            val jsonObject = jsonParser.parseToJsonElement(responseText).jsonObject
            val recordings = jsonObject["recordings"]?.jsonArray
            val mbid = recordings?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            if (!mbid.isNullOrBlank()) {
                synchronized(mbidCache) {
                    mbidCache[cacheKey] = mbid
                }
            }
            mbid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("RecommendationEngine").w(e, "Failed to fetch MusicBrainz ID")
            null
        }
    }

    private suspend fun fetchListenBrainzSimilar(mbid: String, limit: Int): List<Pair<String, String>> {
        val url = "https://labs.api.listenbrainz.org/similar-recordings"
        return try {
            val responseText = httpClient.get(url) {
                parameter("recording_mbid", mbid)
                parameter("algorithm", "session_based_days_9000_session_limit_100_session_mins_180")
            }.bodyAsText()

            val jsonObject = jsonParser.parseToJsonElement(responseText).jsonObject
            val payload = jsonObject["payload"]?.jsonObject ?: return emptyList()
            val recordings = payload["similar_recordings"]?.jsonArray ?: return emptyList()

            recordings.mapNotNull { item ->
                val obj = item.jsonObject
                val trackName = obj["track_name"]?.jsonPrimitive?.content
                    ?: obj["title"]?.jsonPrimitive?.content
                val artistName = obj["artist_name"]?.jsonPrimitive?.content
                    ?: obj["artist"]?.jsonPrimitive?.content
                if (trackName != null && artistName != null) {
                    trackName to artistName
                } else null
            }.take(limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("RecommendationEngine").w(e, "Failed to fetch ListenBrainz recommendations")
            emptyList()
        }
    }

    private suspend fun resolveTracksToYtItems(tracks: List<Pair<String, String>>): List<SongItem> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        tracks.map { (title, artist) ->
            async {
                semaphore.withPermit {
                    try {
                        val searchQuery = "$title $artist"
                        val searchResult = YouTube.search(searchQuery, SearchFilter.FILTER_SONG).getOrNull()
                        searchResult?.items?.firstOrNull { it is SongItem } as? SongItem
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("RecommendationEngine").w(e, "Failed to resolve track to YouTube Music: $title by $artist")
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun escapeLuceneTerm(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val MBID_CACHE_MAX_SIZE = 256
        private const val MAX_CONCURRENT_SEARCHES = 4
    }
}

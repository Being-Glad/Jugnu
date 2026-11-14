/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

@Singleton
class LocalKotlinRecommender @Inject constructor(
    private val databaseDao: DatabaseDao
) {
    suspend fun getRecommendations(currentSongId: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()

        val allLibrarySongs = databaseDao.allSongs().first()
        if (allLibrarySongs.isEmpty()) {
            return@withContext emptyList()
        }

        val currentSong = currentSongId.takeIf { it.isNotBlank() }?.let { databaseDao.getSongById(it) }
        if (currentSong == null) {
            val liked = allLibrarySongs
                .filter { it.id != currentSongId && it.song.liked }
                .shuffled()
            if (liked.size >= limit) {
                return@withContext liked.take(limit)
            }
            val seenIds = liked.mapTo(HashSet(liked.size)) { it.id }
            val extras = allLibrarySongs
                .filter { it.id != currentSongId && seenIds.add(it.id) }
                .sortedByDescending { it.song.totalPlayTime }
            return@withContext (liked + extras).take(limit)
        }

        val currentArtists = currentSong.artists.mapTo(HashSet()) { it.id }
        val currentAlbumId = currentSong.album?.id

        val scoredCandidates = allLibrarySongs
            .asSequence()
            .filter { it.id != currentSongId }
            .map { song ->
                val artistOverlap = song.artists.count { it.id in currentArtists }
                val albumMatch = if (currentAlbumId != null && song.album?.id == currentAlbumId) 1 else 0
                val playTimeWeight = ln(song.song.totalPlayTime.coerceAtLeast(0L).toDouble() + 1.0)
                val likedBoost = if (song.song.liked) 2.5 else 0.0

                val score = (artistOverlap * 5.0) + (albumMatch * 3.0) + (playTimeWeight * 0.5) + likedBoost
                song to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()

        // Enforce a per-primary-artist diversity cap before filling remaining slots.
        val result = ArrayList<Song>(limit)
        val seenIds = HashSet<String>(limit * 2)
        val artistCounts = HashMap<String, Int>()
        val overflow = ArrayList<Song>()

        for (song in scoredCandidates) {
            if (result.size >= limit) break
            val primaryArtistId = song.artists.firstOrNull()?.id
            val count = if (primaryArtistId != null) artistCounts.getOrDefault(primaryArtistId, 0) else 0
            if (primaryArtistId == null || count < MAX_PER_ARTIST) {
                if (seenIds.add(song.id)) {
                    result.add(song)
                    if (primaryArtistId != null) {
                        artistCounts[primaryArtistId] = count + 1
                    }
                }
            } else {
                overflow.add(song)
            }
        }

        if (result.size < limit) {
            for (song in overflow) {
                if (result.size >= limit) break
                if (seenIds.add(song.id)) {
                    result.add(song)
                }
            }
        }

        if (result.size < limit) {
            val fillPool = allLibrarySongs
                .filter { it.id != currentSongId && it.id !in seenIds }
                .shuffled()
            for (song in fillPool) {
                if (result.size >= limit) break
                if (seenIds.add(song.id)) {
                    result.add(song)
                }
            }
        }

        result
    }

    companion object {
        private const val MAX_PER_ARTIST = 3
    }
}

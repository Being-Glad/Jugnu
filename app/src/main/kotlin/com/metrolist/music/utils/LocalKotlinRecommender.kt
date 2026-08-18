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

@Singleton
class LocalKotlinRecommender @Inject constructor(
    private val databaseDao: DatabaseDao
) {
    suspend fun getRecommendations(currentSongId: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val currentSong = databaseDao.getSongById(currentSongId)
        val allLibrarySongs = databaseDao.allSongs().first()
        
        if (allLibrarySongs.isEmpty()) {
            return@withContext emptyList()
        }

        if (currentSong == null) {
            return@withContext allLibrarySongs
                .filter { it.song.liked }
                .shuffled()
                .take(limit)
        }

        val currentArtists = currentSong.artists.map { it.id }.toSet()
        val currentAlbumId = currentSong.album?.id

        val similarityList = allLibrarySongs
            .filter { it.id != currentSongId }
            .map { song ->
                val artistOverlap = song.artists.count { it.id in currentArtists }
                val albumMatch = if (currentAlbumId != null && song.album?.id == currentAlbumId) 1 else 0
                val playTimeWeight = Math.log(song.song.totalPlayTime.toDouble() + 1.0)
                
                val score = (artistOverlap * 5.0) + (albumMatch * 3.0) + (playTimeWeight * 0.5)
                song to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }

        if (similarityList.size >= limit) {
            similarityList.take(limit)
        } else {
            val filledList = similarityList.toMutableList()
            val remainingCount = limit - filledList.size
            val fillPool = allLibrarySongs
                .filter { it.id != currentSongId && it.id !in filledList.map { s -> s.id } }
                .shuffled()
            
            filledList.addAll(fillPool.take(remainingCount))
            filledList
        }
    }
}

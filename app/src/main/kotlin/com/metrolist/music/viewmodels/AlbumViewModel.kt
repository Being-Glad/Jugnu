/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        fetchAlbum()
    }

    fun retry() {
        fetchAlbum(forceRefresh = true)
    }

    private fun fetchAlbum(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            val album = database.album(albumId).first()
            val cached = if (!forceRefresh) {
                albumCache.get(albumId)?.takeIf {
                    System.currentTimeMillis() - it.fetchedAtMs <= ALBUM_CACHE_TTL_MS
                }
            } else {
                null
            }

            if (cached != null) {
                playlistId.value = cached.page.album.playlistId
                otherVersions.value = cached.page.otherVersions
                val hasSongsInDb = !database.albumWithSongs(albumId).first()?.songs.isNullOrEmpty()
                if (album == null || !hasSongsInDb) {
                    database.transaction {
                        if (album == null) {
                            insert(cached.page)
                        } else {
                            update(album.album, cached.page, album.artists)
                        }
                    }
                }
                _isLoading.value = false
                return@launch
            }

            YouTube
                .album(albumId)
                .onSuccess {
                    albumCache.put(albumId, CachedAlbum(it, System.currentTimeMillis()))
                    playlistId.value = it.album.playlistId
                    otherVersions.value = it.otherVersions
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }
                    _isLoading.value = false
                }.onFailure {
                    reportException(it)
                    _error.value = it.message ?: "Failed to load album"
                    _isLoading.value = false
                    if (it.message?.contains("NOT_FOUND") == true) {
                        database.query {
                            album?.album?.let(::delete)
                        }
                    }
                }
        }
    }

    private data class CachedAlbum(
        val page: AlbumPage,
        val fetchedAtMs: Long,
    )

    companion object {
        private const val ALBUM_CACHE_TTL_MS = 5 * 60 * 1000L
        private val albumCache = LruCache<String, CachedAlbum>(24)
    }
}

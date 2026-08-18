/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.utils.RecommendationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val recommendationEngine: RecommendationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun generateRecommendations(currentSongId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val songs = recommendationEngine.getRecommendations(currentSongId, limit = 25)
                if (songs.isEmpty()) {
                    _uiState.value = UiState.Error("No recommendations found.")
                } else {
                    _uiState.value = UiState.Success(songs)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    sealed interface UiState {
        object Idle : UiState
        object Loading : UiState
        data class Success(val songs: List<SongItem>) : UiState
        data class Error(val message: String) : UiState
    }
}

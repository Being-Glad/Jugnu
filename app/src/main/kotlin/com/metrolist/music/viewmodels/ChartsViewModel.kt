/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadCharts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            YouTube.getChartsPage()
                .onSuccess { page ->
                    var sections = page.sections.sortedByDescending { section ->
                        when {
                            section.items.any { it is com.metrolist.innertube.models.SongItem } -> 2
                            section.items.any { it is com.metrolist.innertube.models.ArtistItem } -> 1
                            else -> 0
                        }
                    }

                    // Fallback: If YouTube charts didn't include song items, inject regional trending songs
                    if (sections.none { it.items.any { item -> item is com.metrolist.innertube.models.SongItem } }) {
                        val cachedSongs = com.metrolist.music.viewmodels.HomeViewModel.cachedTrendingSongs
                        if (!cachedSongs.isNullOrEmpty()) {
                            val trendingSection = ChartsPage.ChartSection(
                                title = "Trending songs",
                                items = cachedSongs,
                                chartType = ChartsPage.ChartType.TRENDING,
                            )
                            sections = listOf(trendingSection) + sections
                        } else {
                            // If cachedSongs is not ready yet, fetch regional trending hits from YouTube.home()
                            YouTube.home().onSuccess { homePage ->
                                val homeSongs = homePage.sections.flatMap { it.items }
                                    .filterIsInstance<com.metrolist.innertube.models.SongItem>()
                                    .distinctBy { it.id }
                                if (homeSongs.isNotEmpty()) {
                                    val trendingSection = ChartsPage.ChartSection(
                                        title = "Trending songs",
                                        items = homeSongs,
                                        chartType = ChartsPage.ChartType.TRENDING,
                                    )
                                    sections = listOf(trendingSection) + sections
                                    com.metrolist.music.viewmodels.HomeViewModel.cachedTrendingSongs = homeSongs
                                }
                            }
                        }
                    }

                    timber.log.Timber.tag("ChartsViewModel").d("Loaded charts with ${sections.size} sections: ${sections.map { "${it.title} (${it.items.size} items)" }}")
                    _chartsPage.value = page.copy(sections = sections)
                }
                .onFailure { e ->
                    val cachedSongs = com.metrolist.music.viewmodels.HomeViewModel.cachedTrendingSongs
                    if (!cachedSongs.isNullOrEmpty()) {
                        val trendingSection = ChartsPage.ChartSection(
                            title = "Trending songs",
                            items = cachedSongs,
                            chartType = ChartsPage.ChartType.TRENDING,
                        )
                        _chartsPage.value = ChartsPage(sections = listOf(trendingSection), continuation = null)
                    } else {
                        // Try fallback to YouTube.home()
                        YouTube.home().onSuccess { homePage ->
                            val homeSongs = homePage.sections.flatMap { it.items }
                                .filterIsInstance<com.metrolist.innertube.models.SongItem>()
                                .distinctBy { it.id }
                            if (homeSongs.isNotEmpty()) {
                                val trendingSection = ChartsPage.ChartSection(
                                    title = "Trending songs",
                                    items = homeSongs,
                                    chartType = ChartsPage.ChartType.TRENDING,
                                )
                                _chartsPage.value = ChartsPage(sections = listOf(trendingSection), continuation = null)
                                return@launch
                            }
                        }
                        _error.value = "Failed to load charts: ${e.message}"
                    }
                }
            
            _isLoading.value = false
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            _chartsPage.value?.continuation?.let { continuation ->
                _isLoading.value = true
                YouTube.getChartsPage(continuation)
                    .onSuccess { newPage ->
                        _chartsPage.value = _chartsPage.value?.copy(
                            sections = _chartsPage.value?.sections.orEmpty() + newPage.sections,
                            continuation = newPage.continuation
                        )
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load more: ${e.message}"
                    }
                _isLoading.value = false
            }
        }
    }
}

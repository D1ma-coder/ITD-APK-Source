package com.itd.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.SearchData
import com.itd.app.data.model.TrendingHashtag
import com.itd.app.data.remote.SearchApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val data: SearchData = SearchData(),
    val trending: List<TrendingHashtag> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val api: SearchApi) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            try {
                val trending = api.trending().data.hashtags
                _state.update { it.copy(trending = trending) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun search(query: String) {
        job?.cancel()
        _state.update { it.copy(query = query, loading = query.isNotBlank(), error = null) }
        if (query.isBlank()) {
            _state.update { it.copy(data = SearchData(), loading = false) }
            return
        }
        job = viewModelScope.launch {
            delay(350)
            try {
                val data = api.search(query).data
                _state.update { it.copy(data = data, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun clear() = search("")
}

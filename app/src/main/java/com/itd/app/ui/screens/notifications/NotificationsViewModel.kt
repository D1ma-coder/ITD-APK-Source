package com.itd.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itd.app.data.model.NotificationItem
import com.itd.app.data.remote.NotificationsApi
import com.itd.app.data.remote.UsersApi
import com.itd.app.util.LogTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Activity-wide notifications state: the list, the unread badge and follow-back actions. */
class NotificationsViewModel(private val api: NotificationsApi, private val users: UsersApi) : ViewModel() {
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val _followStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val followStates: StateFlow<Map<String, Boolean>> = _followStates.asStateFlow()
    private val _followInProgress = MutableStateFlow<Set<String>>(emptySet())
    val followInProgress: StateFlow<Set<String>> = _followInProgress.asStateFlow()
    private var offset = 0

    /** Reloads the first page and marks everything as read (the tab is open). */
    fun load() = viewModelScope.launch {
        _isLoading.value = true
        try {
            offset = 0
            val response = api.notifications(0, PAGE_SIZE)
            _notifications.value = response.notifications
            _unreadCount.value = response.notifications.count { !it.read }
            _hasMore.value = response.hasMore
            offset = response.notifications.size
            markAllRead()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogTag.PUSH.tree().e(e, "notifications load failed")
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val response = api.notifications(offset, PAGE_SIZE)
                if (response.notifications.isEmpty()) {
                    _hasMore.value = false
                } else {
                    val known = _notifications.value.map { it.id }.toSet()
                    _notifications.value = _notifications.value + response.notifications.filter { it.id !in known }
                    _hasMore.value = response.hasMore
                    offset += response.notifications.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogTag.PUSH.tree().e(e, "notifications page failed")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun markAllRead() {
        _unreadCount.value = 0
        viewModelScope.launch {
            try {
                api.markAllRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogTag.PUSH.tree().e(e, "mark all read failed")
            }
        }
    }

    fun refreshCount() = viewModelScope.launch {
        try {
            _unreadCount.value = api.count().count
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** A notification delivered over the live stream. */
    fun onLiveNotification(item: NotificationItem) {
        if (_notifications.value.any { it.id == item.id }) return
        _notifications.value = listOf(item) + _notifications.value
        if (!item.read) _unreadCount.value += 1
    }

    fun toggleFollow(userId: String, following: Boolean) {
        _followInProgress.value = _followInProgress.value + userId
        _followStates.value = _followStates.value + (userId to !following)
        viewModelScope.launch {
            try {
                if (following) users.unfollow(userId) else users.follow(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _followStates.value = _followStates.value + (userId to following)
            } finally {
                _followInProgress.value = _followInProgress.value - userId
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}

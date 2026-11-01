package com.itd.app.di

import android.content.Context
import com.itd.app.data.local.TokenStore
import com.itd.app.data.remote.*
import com.itd.app.data.repository.AuthRepository
import com.itd.app.data.repository.MediaRepository
import com.itd.app.halloween.HalloweenRepository

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)
    val apiClient = ApiClient(tokenStore)
    val authApi = AuthApi(apiClient)
    val authRepository = AuthRepository(authApi, apiClient, tokenStore)
    val postsApi = PostsApi(apiClient)
    val filesApi = FilesApi(apiClient)
    val mediaRepository = MediaRepository(context, filesApi)
    val usersApi = UsersApi(apiClient)
    val notificationsApi = NotificationsApi(apiClient)
    val searchApi = SearchApi(apiClient)
    val subscriptionApi = SubscriptionApi(apiClient)
    val platformApi = PlatformApi(apiClient)
    val devicesApi = DevicesApi(apiClient)
    val halloweenRepository = HalloweenRepository(apiClient)
    val postInteractor = com.itd.app.ui.components.PostInteractor(postsApi, halloweenRepository)
    val analytics by lazy { com.itd.app.data.analytics.Analytics.get(apiClient) }
}

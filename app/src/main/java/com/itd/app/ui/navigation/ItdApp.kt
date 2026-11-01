package com.itd.app.ui.navigation

import com.itd.app.ui.screens.account.AccountStatusScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.itd.app.di.AppContainer
import com.itd.app.di.viewModelFactory
import com.itd.app.ui.common.*
import com.itd.app.ui.screens.auth.*
import com.itd.app.ui.screens.login.*
import com.itd.app.ui.screens.profilesetup.*

@Composable
fun ItdApp(container: AppContainer, componentContext: com.arkivanov.decompose.ComponentContext, pendingLink: String? = null, onLinkConsumed: () -> Unit = {}) {
    val root: RootViewModel = viewModel(factory = viewModelFactory { RootViewModel(container.authRepository, container.devicesApi) })
    val auth by root.authState.collectAsStateWithLifecycle()
    val error by root.startupError.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { com.itd.app.ui.common.UpdateGate(container.platformApi) {
        when {
            auth == null && error != null -> ErrorContent(error!!, { root.restoreSession() })
            auth == null -> LoadingContent()
            auth!!.profileRequired -> {
                val setup: ProfileSetupViewModel = viewModel(factory = viewModelFactory { ProfileSetupViewModel(container.usersApi, container.authRepository) })
                ProfileSetupScreen(setup)
            }
            auth!!.authenticated && !auth!!.banned && !auth!!.deleted -> {
                val overlay = remember(auth!!.userId) {
                    com.itd.app.decompose.OverlayComponent(componentContext, auth!!.user?.username, container.apiClient.json)
                }
                MainScreen(container, overlay, auth!!.user, auth!!.userId, { root.logout() }, pendingLink, onLinkConsumed)
            }
            auth!!.banned || auth!!.deleted -> AccountStatusScreen(container, auth!!.canRestore, auth!!.restoreDeadline, { root.logout() })
            else -> {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "login") {
                    composable("login") {
                        val login: LoginViewModel = viewModel(factory = viewModelFactory { LoginViewModel(container.authRepository) })
                        LoginScreen(login, { nav.popBackStack("login", false) }, { nav.navigate("forgot") }, { nav.navigate("register") })
                    }
                    composable("register") {
                        val register: AuthFlowViewModel = viewModel(factory = viewModelFactory { AuthFlowViewModel(AuthFlow.Register, container.authRepository) })
                        AuthFlowScreen(register, { nav.popBackStack() }, { nav.popBackStack("login", false) })
                    }
                    composable("forgot") {
                        val forgot: AuthFlowViewModel = viewModel(factory = viewModelFactory { AuthFlowViewModel(AuthFlow.ForgotPassword, container.authRepository) })
                        AuthFlowScreen(forgot, { nav.popBackStack() }, { nav.popBackStack("login", false) })
                    }
                }
            }
        }
    }
    }
}

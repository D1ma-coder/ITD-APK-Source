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
import com.itd.app.ui.screens.forgot.*
import com.itd.app.ui.screens.otp.*
import com.itd.app.ui.screens.login.*
import com.itd.app.ui.screens.profilesetup.*
import com.itd.app.ui.screens.register.*

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
                var signUp by remember { mutableStateOf<SignUpResult?>(null) }
                NavHost(nav, startDestination = "login") {
                    composable("login") {
                        val login: LoginViewModel = viewModel(factory = viewModelFactory { LoginViewModel(container.authRepository) })
                        LoginScreen(login, { nav.popBackStack("login", false) }, { nav.navigate("forgot") }, { nav.navigate("register") })
                    }
                    composable("register") {
                        val register: RegisterViewModel = viewModel(factory = viewModelFactory { RegisterViewModel(container.authRepository) })
                        RegisterScreen(register, { email, password, flowToken ->
                            signUp = SignUpResult(email, password, flowToken)
                            nav.navigate("verify")
                        }, { nav.popBackStack() })
                    }
                    composable("verify") {
                        val pending = signUp ?: return@composable
                        val verify: OtpViewModel = viewModel(factory = viewModelFactory {
                            OtpViewModel(container.authRepository, pending.email, pending.password, pending.flowToken)
                        })
                        VerifyEmailScreen(verify, { nav.popBackStack("login", false) }, { nav.popBackStack() })
                    }
                    composable("forgot") {
                        val forgot: ForgotPasswordViewModel = viewModel(factory = viewModelFactory { ForgotPasswordViewModel(container.authRepository) })
                        ForgotPasswordScreen(forgot, { nav.popBackStack("login", false) }, { nav.popBackStack() })
                    }
                }
            }
        }
    }
    }
}

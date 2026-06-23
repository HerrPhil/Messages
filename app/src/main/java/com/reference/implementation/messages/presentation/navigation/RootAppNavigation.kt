package com.reference.implementation.messages.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.data.manager.AuthState
import com.reference.implementation.messages.data.manager.UnauthReason
import com.reference.implementation.messages.data.manager.UserRoleState
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeScreen
import com.reference.implementation.messages.presentation.screens.adminmessage.AdminMessageScreen
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinScreen
import com.reference.implementation.messages.presentation.screens.home.HomeScreen
import com.reference.implementation.messages.presentation.screens.login.LoginScreen
import com.reference.implementation.messages.presentation.screens.message.MessageScreen
import kotlinx.serialization.Serializable

// 1. Define compile-time strongly typed destinations

@Serializable
object Login

@Serializable
object MainHub

@Composable
fun RootAppNavigation(
    rootNavController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    val context = LocalContext.current

    // Safely collect the state respecting the lifecycle of the activity/window
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val userRoleState by viewModel.userRoleState.collectAsStateWithLifecycle()

    // Reactively swap the destination graph based on authState
    LaunchedEffect(authState) {

        val state = authState // this exists to facilitate smart cast

        if (state is AuthState.Unauthenticated) {
            if (state.reason == UnauthReason.FORCE_LOGOUT) {
                Toast.makeText(
                    context,
                    "Your session has expired. Please log in again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        when (authState) {
            is AuthState.Unauthenticated -> {
                rootNavController.navigate(route = Login) {
                    // This is the magic clause: it completely clears the main_hub destination
                    // along with its entire ViewModelStore, destroying all sub-screen ViewModels!
                    popUpTo(route = MainHub) { inclusive = true }
                }
            }

            is AuthState.Authenticated -> {
                rootNavController.navigate(route = MainHub) {
                    popUpTo(route = Login) { inclusive = true }
                }
            }
        }
    }

    // NEW: Clean, isolated Logging Side-Effect
    // This will ONLY run once per state change, completely off the main composition thread.
    LaunchedEffect(userRoleState) {
        viewModel.logRoleStateTransition(userRoleState)
    }

    NavHost(navController = rootNavController, startDestination = Login) {
        // Unauthenticated
        composable<Login> {
            LoginScreen()
        }
        // Authenticated
        composable<MainHub> {

            // Remember the config so it is only re-evaluated when the user role actually changes
            val config = remember(userRoleState) {
                when (userRoleState) {
                    is UserRoleState.Administrator -> HubConfig(
                        startDestination = Route.AdminHome,
                        defaultRoute = Route.AdminHome::class.qualifiedName ?: "No route",
                        titlesLambda = { qualifiedRouteName ->
                            when (qualifiedRouteName) {
                                Route.AdminMessages::class.qualifiedName -> "Administrator Message Centre"
                                Route.Bulletins::class.qualifiedName -> "Bulletin Board"
                                // Home title move to "else" to make "when" statement exhaustive.
                                else -> "Administrator Home Page"
                            }

                        },
                        bottomBarTabs = listOf(
                            Route.AdminHome,
                            Route.AdminMessages,
                            Route.Bulletins
                        ),
                        screenNavBuilder = {
                            composable<Route.AdminHome> {
                                AdminHomeScreen()
                            }
                            composable<Route.AdminMessages> {
                                AdminMessageScreen()
                            }
                            composable<Route.Bulletins> {
                                BulletinScreen()
                            }
                        }
                    )

                    is UserRoleState.RegularUser -> HubConfig(
                        startDestination = Route.Home,
                        defaultRoute = Route.Home::class.qualifiedName ?: "No route",
                        titlesLambda = { qualifiedRouteName ->
                            when (qualifiedRouteName) {
                                Route.Messages::class.qualifiedName -> "Message Centre"
                                Route.Bulletins::class.qualifiedName -> "Bulletin Board"
                                // Home title move to "else" to make "when" statement exhaustive.
                                else -> "User Home Page"
                            }
                        },
                        bottomBarTabs = listOf(Route.Home, Route.Messages, Route.Bulletins),
                        screenNavBuilder = {
                            composable<Route.Home> {
                                HomeScreen()
                            }
                            composable<Route.Messages> {
                                MessageScreen()
                            }
                            composable<Route.Bulletins> {
                                BulletinScreen()
                            }
                        }
                    )

                    else -> null
                }
            }

            // Only render the hub if a valid authorized configuration is mapped
            config?.let {
                AuthenticatedMainParameterHub(
                    startDestination = it.startDestination,
                    defaultRoute = it.defaultRoute,
                    titlesLambda = it.titlesLambda,
                    bottomBarTabs = it.bottomBarTabs,
                    screenNavBuilder = it.screenNavBuilder
                )
            }
        }
    }
}

private data class HubConfig(
    val startDestination: Route,
    val defaultRoute: String,
    val titlesLambda: (String?) -> String,
    val bottomBarTabs: List<Route>,
    val screenNavBuilder: NavGraphBuilder.() -> Unit
)
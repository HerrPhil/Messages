package com.reference.implementation.messages.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.data.manager.AuthState
import com.reference.implementation.messages.data.manager.UnauthReason
import com.reference.implementation.messages.data.manager.UserRoleState
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.login.LoginScreen
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

    // NEW: Clean, isolated Logging Side-Effect
    // This will ONLY run once per state change, completely off the main composition thread.
    LaunchedEffect(userRoleState) {
        viewModel.logRoleStateTransition(userRoleState)
    }

    // Reactively swap the destination graph based on authState
    LaunchedEffect(authState) {

        val state = authState // this exists to facilitate smart cast

        if (state is AuthState.Unauthenticated && state.reason == UnauthReason.FORCE_LOGOUT) {
            Toast.makeText(
                context,
                "Your session has expired. Please log in again.",
                Toast.LENGTH_LONG
            ).show()
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
                // NEW: When we keep NavHost at the root level (for example, to preserve transition
                // animations between Login and MainHub), you must guard the navigate() imperative
                // call so it only executes when not already on MainHub.
                // GUARD Code: only navigate if we are not ALREADY sitting on MainHub!!!!
                val destination = rootNavController.currentBackStackEntry?.destination
                if (destination?.hasRoute<MainHub>() == true) return@LaunchedEffect

                // this is a new route and imperatively navigates
                rootNavController.navigate(route = MainHub) {
                    popUpTo(route = Login) { inclusive = true }
                }

            }
        }
    }

    NavHost(navController = rootNavController, startDestination = Login) {
        // Unauthenticated
        composable<Login> {
            LoginScreen()
        }
        // Authenticated
        composable<MainHub> {

            Audit.createInstance()
                .writeLog("RootAppNavigation composable is navigating to Route.MainHub")

            // Remember the config so it is only re-evaluated when the user role actually changes
            val config = remember(userRoleState) {
                when (userRoleState) {
                    is UserRoleState.Administrator -> HubConfig(
                        startDestination = Route.AdminHome,
                        defaultRoute = Route.AdminHome::class.qualifiedName ?: "No route",
                        onRouteSelectTitle = { qualifiedRouteName ->
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
                        )
                    )

                    is UserRoleState.RegularUser -> HubConfig(
                        startDestination = Route.Home,
                        defaultRoute = Route.Home::class.qualifiedName ?: "No route",
                        onRouteSelectTitle = { qualifiedRouteName ->
                            when (qualifiedRouteName) {
                                Route.Messages::class.qualifiedName -> "Message Centre"
                                // The following option reduces title flicker
                                // when NavHost state changes instantly,
                                // and top bar visibility slide up
                                // and fade out are still processing.
                                Route.MessageDetail::class.qualifiedName + "/{id}" -> "Message Centre"
                                Route.Bulletins::class.qualifiedName -> "Bulletin Board"
                                // The following option reduces title flicker
                                // when NavHost state changes instantly,
                                // and top bar visibility slide up
                                // and fade out are still processing.
                                Route.BulletinDetail::class.qualifiedName + "/{id}" -> "Bulletin Board"
                                // Home title move to "else" to make "when" statement exhaustive.
                                else -> "User Home Page"
                            }
                        },
                        bottomBarTabs = listOf(
                            Route.Home,
                            Route.MessagesGraph,
                            Route.BulletinsGraph
                        )
                    )

                    else -> null
                }
            }

            // Only render the hub if a valid authorized configuration is mapped
            config?.let {
                AuthenticatedMainParameterHub(
                    startDestination = it.startDestination,
                    defaultRoute = it.defaultRoute,
                    onRouteSelectTitle = it.onRouteSelectTitle,
                    bottomBarTabs = it.bottomBarTabs
                )
            }
        }
    }
}

private data class HubConfig(
    val startDestination: Route,
    val defaultRoute: String,
    val onRouteSelectTitle: (String?) -> String,
    val bottomBarTabs: List<Route>
)
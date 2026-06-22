package com.reference.implementation.messages.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.data.audit.Audit
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

    NavHost(navController = rootNavController, startDestination = Login) {
        // Unauthenticated
        composable<Login> {
            LoginScreen()
        }
        // Authenticated
        composable<MainHub> {

            when (userRoleState) {


                is UserRoleState.Loading -> {
                    LaunchedEffect(userRoleState) {
                        Audit.createInstance().writeLog("Root App Navigation: still loading ...")
                    }
                }

                is UserRoleState.Administrator -> {

                    val startDestination = Route.AdminHome

                    val defaultRoute = Route.AdminHome::class.qualifiedName ?: "No route"

                    val titlesLambda: (String?) -> String = { qualifiedRouteName ->
                        when (qualifiedRouteName) {
                            Route.AdminMessages::class.qualifiedName -> "Administrator Message Centre"
                            Route.Bulletins::class.qualifiedName -> "Bulletin Board"
                            // Home title move to "else" to make "when" statement exhaustive.
                            else -> "Administrator Home Page"
                        }
                    }

                    val bottomBarTabs =
                        listOf(Route.AdminHome, Route.AdminMessages, Route.Bulletins)

                    val screenNavBuilder: NavGraphBuilder.() -> Unit = {
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

                    AuthenticatedMainParameterHub(
                        startDestination,
                        defaultRoute,
                        titlesLambda,
                        bottomBarTabs,
                        screenNavBuilder
                    )
                }

                is UserRoleState.RegularUser -> {

                    val startDestination = Route.Home

                    val defaultRoute = Route.Home::class.qualifiedName ?: "No route"

                    val titlesLambda: (String?) -> String = { qualifiedRouteName ->
                        when (qualifiedRouteName) {
                            Route.Messages::class.qualifiedName -> "Message Centre"
                            Route.Bulletins::class.qualifiedName -> "Bulletin Board"
                            // Home title move to "else" to make "when" statement exhaustive.
                            else -> "User Home Page"
                        }
                    }

                    val bottomBarTabs = listOf(Route.Home, Route.Messages, Route.Bulletins)

                    val screenNavBuilder: NavGraphBuilder.() -> Unit = {
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

                    AuthenticatedMainParameterHub(
                        startDestination,
                        defaultRoute,
                        titlesLambda,
                        bottomBarTabs,
                        screenNavBuilder
                    )
                }

                is UserRoleState.Unknown -> {
                    LaunchedEffect(userRoleState) {
                        Audit.createInstance()
                            .writeLog("Root App Navigation: The user role is cleared - logout event")
                    }
                }
            }
        }
    }
}

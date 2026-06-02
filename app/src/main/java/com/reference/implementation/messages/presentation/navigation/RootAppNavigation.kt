package com.reference.implementation.messages.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.data.manager.AuthState
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

    // Safely collect the state respecting the lifecycle of the activity/window
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    // Reactively swap the destination graph based on authState
    LaunchedEffect(authState) {
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
            AuthenticatedMainHub()
        }
    }
}

package com.reference.implementation.messages.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.data.manager.AuthState
import com.reference.implementation.messages.data.manager.UnauthReason
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

    // Reactively swap the destination graph based on authState
    LaunchedEffect(authState) {

        val state = authState

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
            AuthenticatedMainHub()
        }
    }
}

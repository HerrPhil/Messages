package com.reference.implementation.messages.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.presentation.screens.login.LoginScreen
import kotlinx.serialization.Serializable

// 1. Define compile-time strongly typed destinations

@Serializable
object Login

@Serializable
object MainHub

@Composable
fun RootAppNavigation(rootNavController: NavHostController = rememberNavController()) {
    NavHost(navController = rootNavController, startDestination = Login) {
        // Unauthenticated
        composable<Login> {
            LoginScreen(onLogin = {
                // Pop login so back button from home does not return here
                rootNavController.navigate(route = MainHub) {
                    popUpTo(route = Login) { inclusive = true }
                }
            })
        }
        composable<MainHub> {
            AuthenticatedMainHub(onLogout = {
                rootNavController.navigate(route = Login) {
                    // This is the magic clause: it completely clears the main_hub destination
                    // along with its entire ViewModelStore, destroying all sub-screen ViewModels!
                    popUpTo(route = Login) { inclusive = true }
                }
            })
        }
    }
}

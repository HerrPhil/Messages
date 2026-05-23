package com.reference.implementation.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.reference.implementation.messages.presentation.screens.Screen
import com.reference.implementation.messages.presentation.screens.home.HomeScreen
import com.reference.implementation.messages.presentation.screens.login.LoginScreen

@Composable
fun MessageApp() {

    // On the May 16, 2026 iteration, the message app will add simple navigation between
    // the Login screen and the Message screen.

    // 1. Track the current screen state
    var currentScreen by remember { mutableStateOf(Screen.Login) }

    // The key() function forces a total state wipe whenever currentScreen changes
//    key(currentScreen) {


    // 2. Conditional rendering based on the state
    when (currentScreen) {
        Screen.Login -> {
            // The Gemini AI claim for key() here:
            // Using a unique key forces Compose to reset the state and
            // recreate the ViewModel when this block becomes active again.
            key(Screen.Login) {
                LoginScreen(onLogin = { currentScreen = Screen.Home })
            }
        }

        Screen.Home -> {
            // The Gemini AI claim for key() here:
            // Using a unique key forces Compose to reset the state and
            // recreate the ViewModel when this block becomes active again.
            key(Screen.Home) {
                HomeScreen(onLogout = { currentScreen = Screen.Login })
            }
        }
    }
//    }


    // On this iteration, I have no navigation to choose screens.
    // I have wired up one screen: LoginScreen.
    // This will be the starting point.
//    LoginScreen(navigateToHome = { userUiState ->
//        println("Hey login is complete")
//        println("Your user ui state looks like")
//        when (userUiState) {
//            is UserUiState.Success -> {
//                // evidence that values from the server are arriving in the UI successfully!
//                println("user id: ${userUiState.userId}, email: ${userUiState.email}, name: ${userUiState.name}, age: ${userUiState.age}")
//            }
//
//            else -> println("seems to not be a successful login after all")
//        }
//    })
}

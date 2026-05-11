package com.reference.implementation.messages

import androidx.compose.runtime.Composable
import com.reference.implementation.messages.presentation.screens.login.LoginScreen
import com.reference.implementation.messages.presentation.screens.user.UserUiState

@Composable
fun MessageApp() {

    // On this iteration, I have no navigation to choose screens.
    // I have wired up one screen: LoginScreen.
    // This will be the starting point.
    LoginScreen(navigateToHome = { accessToken, userUiState ->
        println("Hey login is complete")
        println("Your session has this access token for api call headers: $accessToken")
        println("Your user ui state looks like")
        when (userUiState) {
            is UserUiState.Success -> {
                // evidence that values from the server are arriving in the UI successfully!
                println("user id: ${userUiState.userId}, email: ${userUiState.email}, name: ${userUiState.name}, age: ${userUiState.age}")
            }

            else -> println("seems to not be a successful login after all")
        }
    })
}

package com.reference.implementation.messages.presentation.screens.login

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.user.UserUiState

@Composable
fun LoginScreen(
    navigateToHome: (String, UserUiState) -> Unit,
    viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        LoginBody(
            viewModel.uiState,
            email = email,
            password = password,
            onCancelClick = { viewModel.cancelLoading() },
            onLoginClick = { viewModel.login(email, password) },
            onEmailChange = { newEmail: String -> email = newEmail },
            onPasswordChange = { newPassword: String -> password = newPassword },
            onSuccessAction = navigateToHome,
            contentPadding = innerPadding
        )
    }


    val uiState = viewModel.uiState
}

@Composable
fun LoginBody(
    uiState: LoginUiState,
    email: String,
    password: String,
    onCancelClick: () -> Unit,
    onLoginClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSuccessAction: (String, UserUiState) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {

    // TODO decide whether to use content padding in Box or Column of login screen.
    //      My sample project used it on a list of data, for example.

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is LoginUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    CircularProgressIndicator()

                    Spacer(modifier = Modifier.height(16.dp))

                    // The "Force Cancel" Button
                    Button(onClick = onCancelClick) {
                        Text(text = "Cancel Request")
                    }
                }
            }

            is LoginUiState.Retrying -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    val orangeColor = Color(0xFFFF9800)

                    CircularProgressIndicator(
                        color = orangeColor, trackColor = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // The "Force Cancel" Button
                    Button(onClick = onCancelClick) {
                        Text(text = "Cancel Request")
                    }

                    Text(
                        text = "Connection jittery...Retry attempt #${uiState.attempt}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            is LoginUiState.Success -> {
                SuccessMessage(uiState.accessToken, uiState)
                onSuccessAction(uiState.accessToken, uiState.userUiState)
            }

            is LoginUiState.Warning -> {
                WarningMessage(uiState.message, onRetry = onLoginClick)
            }

            is LoginUiState.Error -> {
                ErrorMessage(uiState.message, onRetry = onLoginClick)
            }

            else -> { // idle state
                LoginDetails(
                    email = email,
                    password = password,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onLoginClick = onLoginClick,
                    onCancelClick = onLoginClick
                )
            }
        }
    }
}

@Composable
fun LoginDetails(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { onEmailChange(it) },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { onPasswordChange(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Login")
        }
        TextButton(
            onClick = onCancelClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun SuccessMessage(accessToken: String, loginUiState: LoginUiState.Success) {
    val userName = when (loginUiState.userUiState) {
        is UserUiState.Success -> loginUiState.userUiState.name
        else -> "no name"
    }
    Text(
        text = "Success: your access token is $accessToken for user $userName"
    )
}

@Composable
fun WarningMessage(message: String, onRetry: () -> Unit) {
    Text(
        text = "Warning: $message"
    )
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Text(
        text = "Error: $message"
    )
}

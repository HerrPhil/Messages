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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.user.UserUiState

@Composable
fun LoginScreen(
//    onLogin: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    Scaffold(modifier = Modifier.padding(all = 24.dp)) { innerPadding ->

        val uiState = viewModel.uiState
        // Collect states reactively
        val email by viewModel.email.collectAsStateWithLifecycle()
        val password by viewModel.password.collectAsStateWithLifecycle()
        val isSubmitEnabled by viewModel.isSubmitEnabled.collectAsStateWithLifecycle()
        var passwordVisible by remember { mutableStateOf(false) }

        LoginBody(
            uiState = uiState,
            email = email,
            password = password,
            passwordVisible = passwordVisible,
            isSubmitEnabled = isSubmitEnabled,
            onPasswordToggle = { passwordVisible = !passwordVisible },
            onCancelClick = { viewModel.cancel() },
            onLoginClick = { viewModel.login(email, password) },
            onEmailChange = { newEmail: String -> viewModel.onEmailChange(newEmail) },
            onPasswordChange = { newPassword: String -> viewModel.onPasswordChange(newPassword) },
//            onSuccessAction = onLogin,
            contentPadding = innerPadding
        )

    }
}

@Composable
fun LoginBody(
    uiState: LoginUiState,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isSubmitEnabled: Boolean,
    onPasswordToggle: () -> Unit,
    onCancelClick: () -> Unit,
    onLoginClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
//    onSuccessAction: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is LoginUiState.Loading -> {
                LoadingMessage(onCancelClick, contentPadding)
            }

            is LoginUiState.Retrying -> {
                RetryingMessage(onCancelClick, uiState, contentPadding)
            }

            is LoginUiState.Success -> {
                SuccessMessage(uiState)

                // TODO - stop doing this, here - it stays in RootAppNavigation
//                onSuccessAction()
            }

            is LoginUiState.Error -> {
                ErrorMessage(uiState.message, onRetry = onLoginClick, onCancelClick, contentPadding)
            }

            else -> { // idle state
                LoginDetails(
                    email = email,
                    password = password,
                    passwordVisible = passwordVisible,
                    isSubmitEnabled = isSubmitEnabled,
                    onPasswordToggle = onPasswordToggle,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onLoginClick = { onLoginClick() },
                    contentPadding
                )
            }
        }
    }
}

@Preview
@Composable
fun LoadingMessagePreview() {
    LoadingMessage(
        onCancelClick = {},
        contentPadding = PaddingValues(24.dp)
    )
}

@Composable
private fun LoadingMessage(
    onCancelClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues = contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val darkGreyColor = Color(0xFF5A5A5A)

        CircularProgressIndicator(
            color = darkGreyColor,
            trackColor = Color.LightGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // The "Force Cancel" Button
        Button(onClick = onCancelClick) {
            Text(text = "Cancel Request")
        }
    }
}

@Preview
@Composable
fun RetryingMessagePreview() {
    RetryingMessage(
        onCancelClick = {},
        uiState = LoginUiState.Retrying(1),
        contentPadding = PaddingValues(24.dp)
    )
}

@Composable
private fun RetryingMessage(
    onCancelClick: () -> Unit,
    uiState: LoginUiState.Retrying,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val orangeColor = Color(0xFFFF9800)

        CircularProgressIndicator(
            color = orangeColor,
            trackColor = Color.LightGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // The "Force Cancel" Button
        Button(onClick = onCancelClick) {
            Text(text = "Cancel Request")
        }

        Text(
            text = "Connection jittery...Retry attempt #${uiState.attempt}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SuccessMessage(loginUiState: LoginUiState.Success) {
    val userName = when (loginUiState.userUiState) {
        is UserUiState.Success -> loginUiState.userUiState.name
        else -> "no name"
    }
    val email = when (loginUiState.userUiState) {
        is UserUiState.Success -> loginUiState.userUiState.email
        else -> "no email"
    }
    Log.d("LoginScreen", "Success: user $userName, email $email, is logged in successfully")
    Audit.createInstance()
        .writeLog("Success: user $userName, email $email, is logged in successfully")
}

@Preview
@Composable
fun ErrorMessagePreview() {
    ErrorMessage(
        message = "Something went wrong",
        onRetry = {},
        onCancelClick = {},
        contentPadding = PaddingValues(24.dp)
    )
}

@Composable
fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    onCancelClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues = contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Retry")
        }

        // The "Force Cancel" Button
        Button(
            onClick = onCancelClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(text = "Cancel Request")
        }

    }
}

@Preview
@Composable
fun LoginDetailsPreview() {
    LoginDetails(
        email = "test@learn.com",
        password = "qwerty1234",
        passwordVisible = false,
        isSubmitEnabled = false,
        onPasswordToggle = {},
        onEmailChange = {},
        onPasswordChange = {},
        onLoginClick = {},
        contentPadding = PaddingValues(24.dp)
    )
}

@Composable
fun LoginDetails(
    email: String,
    password: String,
    passwordVisible: Boolean,
    isSubmitEnabled: Boolean,
    onPasswordToggle: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues = contentPadding),
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { onPasswordChange(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.Visibility
                else
                    Icons.Filled.VisibilityOff

                // Localized description for accessibility
                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = onPasswordToggle) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = isSubmitEnabled, // Controlled entirely by the ViewModel
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Login")
        }
    }
}

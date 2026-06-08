package com.reference.implementation.messages.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // TODO re-factor roleState up to AuthenticatedMainHub to make the decision what route to follow.
    //  Each route will go to a different screen. There the app can choose the administrator
    //  route and screen based on the user role.
//    val roleState by viewModel.userRoleState.collectAsStateWithLifecycle()

    // When you use Compose elements like AnimatedContent or CrossFade, to mention two examples,
    // they pass a local snapshot copy of the state into the lambda block (usually named "it",
    // or renamed to "currentState").
    // You must strictly use that local lambda variable, because local variables cannot be mutated
    // by other threads, allowing Kotlin's smart casting to work flawlessly.
    AnimatedContent(targetState = uiState, label = "DashboardStateTransition") { currentState ->
        when (currentState) {
            is HomeUiState.Idle -> {
                WelcomeHome()
            }

            is HomeUiState.Loading -> {
                LoadingContent()
            }

            is HomeUiState.Retrying -> {
                val retryAttempt = currentState.attempt
                RetryingContent(retryAttempt)
            }

            is HomeUiState.Success -> {
                HomeDetails(currentState)
            }

            is HomeUiState.Error -> {
                ErrorContent(currentState.message)
            }
        }
    }
}

@Composable
fun WelcomeHome() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome Home")
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun RetryingContent(retryAttempt: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val orangeColor = Color(0xFFFF9800)

        CircularProgressIndicator(
            color = orangeColor,
            trackColor = Color.LightGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection jittery...Retry attempt $retryAttempt",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }

}

@Composable
fun HomeDetails(
    currentState: HomeUiState.Success,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {

        // when currentState arrives, then we know it is the Success state
        Column(
            modifier = modifier
                .padding(24.dp)
                .fillMaxSize(),
        ) {
            Text(
                text = "User: ${currentState.userName}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Email: ${currentState.userEmail}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Read Messages: ${currentState.readMessages}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Unread Messages: ${currentState.unreadMessages}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Roles: ${currentState.roles.joinToString { it }}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Permissions loaded: ${currentState.permissions.size}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Permissions: ${currentState.permissions.joinToString { it }}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


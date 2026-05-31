package com.reference.implementation.messages.presentation.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.user.UserUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedShell(
    title: String,
    onLogout: () -> Unit,
    viewModel: AuthenticatedShellViewModel = viewModel(factory = AppViewModelProvider.Factory),
    bottomBar: @Composable () -> Unit, // Injected bottom bar, managed by AuthenticatedMainHub
    content: @Composable (PaddingValues) -> Unit
) {

    val uiState = viewModel.uiState
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle the other home screen UI states; expect this logic to change as more
    // navigation drawer items are added.
    when (uiState) {
        is AuthenticatedShellUiState.LogoutComplete -> {
            LogoutMessage(uiState)
            // handle the root navigation
            onLogout()
        }

        is AuthenticatedShellUiState.Error -> {
            viewModel.cancel() // go back to "Idle" state
            // Share message in a Toast
            Toast
                .makeText(LocalContext.current, uiState.message, Toast.LENGTH_SHORT)
                .show()
        }

        else -> {
            Log.d("AuthenticatedShell", "no matching state")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {

                // Menu Title
                Text(
                    text = "App Menu",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text(text = "Log Out") },
                    selected = false,
                    onClick = {

                        // Prevent accidental double-clicks if already working
                        if (uiState is AuthenticatedShellUiState.Idle) {

                            // Handle the Security Sandbox clearance, changes this UI state
                            viewModel.logout()

                            // handle the drawer state
                            scope.launch { drawerState.close() }
                        }
                    }
                )

            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    }
                )
            },
            bottomBar = bottomBar // Render the bottom bar here
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}


@Composable
fun LogoutMessage(authenticatedShellUiState: AuthenticatedShellUiState.LogoutComplete) {
    val userName = when (authenticatedShellUiState.userUiState) {
        is UserUiState.Success -> authenticatedShellUiState.userUiState.name
        else -> "no name"
    }
    val email = when (authenticatedShellUiState.userUiState) {
        is UserUiState.Success -> authenticatedShellUiState.userUiState.email
        else -> "no email"
    }
    Log.d("LoginScreen", "Success: user $userName, email $email, is logged in successfully")
    Audit.createInstance()
        .writeLog("Success: user $userName, email $email, is logged in successfully")
}

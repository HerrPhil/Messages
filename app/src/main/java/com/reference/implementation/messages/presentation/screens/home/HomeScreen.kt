package com.reference.implementation.messages.presentation.screens.home

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.data.audit.Audit
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.login.LoginUiState
import com.reference.implementation.messages.presentation.screens.user.UserUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    // 1. Manage the open/close state of the drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    val onResetHome = { viewModel.reset() }

    // Handle the other home screen UI states; expect this logic to change as more
    // navigation drawer items are added.
    when (uiState) {
        is HomeUiState.LogoutComplete -> {
            LogoutMessage(uiState)
            onLogout()
            onResetHome()
        }
//        is HomeUiState.Warning -> {
//            viewModel.cancel() // go back to "Idle" state - enables onClick in menu
        // Share message in a Toast
//            Toast
//                .makeText(LocalContext.current, uiState.message, Toast.LENGTH_SHORT)
//                .show()
//        }
        is HomeUiState.Error -> {
            viewModel.cancel() // go back to "Idle" state
            // Share message in a Toast
            Toast
                .makeText(LocalContext.current, uiState.message, Toast.LENGTH_SHORT)
                .show()
        }

        else -> {
            Log.d("HomeScreen", "no matching state")
        }
    }

    // 2. The outer wrapper that provides the drawer functionality
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // The actual content inside the sliding drawer
            ModalDrawerSheet {
                Text(
                    text = "App Menu",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                // Logout Menu Item
                NavigationDrawerItem(
                    label = { Text(text = "Logout") },
                    selected = false,
                    icon = {
                        if (uiState is HomeUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp), // Match standard icon size
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout"
                            )
                        }
                    },
                    onClick = {
                        // Prevent accidental double-clicks if already working
                        if (uiState is HomeUiState.Idle) {
                            viewModel.logout()
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        // 3. The main screen UI structure
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Home") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            // Your future business feature screens will be placed here
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Welcome to the Home Screen!")
            }
        }
    }
}

@Composable
fun LogoutMessage(homeUiState: HomeUiState.LogoutComplete) {
    val userName = when (homeUiState.userUiState) {
        is UserUiState.Success -> homeUiState.userUiState.name
        else -> "no name"
    }
    val email = when (homeUiState.userUiState) {
        is UserUiState.Success -> homeUiState.userUiState.email
        else -> "no email"
    }
    Log.d("LoginScreen", "Success: user $userName, email $email, is logged in successfully")
    Audit.createInstance()
        .writeLog("Success: user $userName, email $email, is logged in successfully")
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text(
//            text = "Success: user $userName, email $email, is logged in successfully",
//            style = MaterialTheme.typography.labelMedium,
//            color = MaterialTheme.colorScheme.primary
//        )
//    }
}

package com.reference.implementation.messages.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import kotlinx.coroutines.launch

// A simple data wrapper to hold our shell controls
data class ShellUiController(
    val isTopBarVisible: Boolean = true,
    val updateTopBarVisibility: (Boolean) -> Unit = {}
)

// Declare the static token
val LocalShellUiController: ProvidableCompositionLocal<ShellUiController> =
    compositionLocalOf { ShellUiController() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedShell(
    title: String,
    viewModel: AuthenticatedShellViewModel = viewModel(factory = AppViewModelProvider.Factory),
    bottomBar: @Composable () -> Unit, // Injected bottom bar, managed by AuthenticatedMainHub
    content: @Composable (PaddingValues) -> Unit
) {

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 1. Collect the Flow as native Compose State
    val isTopBarVisible by viewModel.isTopBarVisible.collectAsStateWithLifecycle()

    // 2. Map the controller interface directly to the ViewModel functions
    val shellController = remember(viewModel) {
        ShellUiController(
            isTopBarVisible = isTopBarVisible,
            updateTopBarVisibility = { visible -> viewModel.setTopBarVisibility(visible) }
        )
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

                        // handle the drawer state
                        scope.launch { drawerState.close() }

                        // Handle the Security Sandbox clearance.
                        // It is a fire-and-forget call.
                        // RootAppNavigation now manages the top-level navigation.
                        viewModel.logout()
                    }
                )

            }
        }
    ) {
        CompositionLocalProvider(LocalShellUiController provides shellController) {
            Scaffold(
                topBar = {
                    AnimatedVisibility(
                        visible = isTopBarVisible,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            },
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
                    }
                },
                bottomBar = bottomBar // Render the bottom bar here
            ) { paddingValues ->
                content(paddingValues)
            }
        }
    }
}

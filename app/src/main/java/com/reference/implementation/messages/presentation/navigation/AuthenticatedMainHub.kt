package com.reference.implementation.messages.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinScreen
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinViewModel
import com.reference.implementation.messages.presentation.screens.home.HomeScreen
import com.reference.implementation.messages.presentation.screens.message.MessageScreen


// 2. Authenticated Hub Level: Houses the drawer and layout
//@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedMainHub() {

    // Isolated NavController for the internal tabs
    val childNavController = rememberNavController()

    // Track the current backstack destination to highlight the correct bottom bar icon
    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Dynamic title mapping based on current destination
    val qualifiedRouteName = currentDestination?.route ?: Route.Home::class.qualifiedName

    val displayTitle = when (qualifiedRouteName) {
        Route.Messages::class.qualifiedName -> "Message Centre"
        Route.Bulletins::class.qualifiedName -> "Bulletin Board"
        else -> "User Home Page" // Home title move to "else" to make "when" statement exhaustive.
    }

    // --- Complex Back Handling ---
    // If the user presses back, we want the nested graph to pop its own stack first.
    // If the child graph is already at its starting destination (Home), we let the system
    // bubble the back press up
    // (which could close the app or do nothing, preventing jumping back to Login).
    // --- Back Handler Details ---
    // I was curious why BackHandler works. It plunked in the code and just works.
    // The Javadocs, or kotlindocs, cleared up the mystery for me.
    // The BackHandler registers a callback with the OS, to be notified when
    // the system back button is pressed.
    val canNavigateUp = childNavController.previousBackStackEntry != null
    BackHandler(enabled = canNavigateUp) {
        childNavController.navigateUp()
    }

    AuthenticatedShell(
        title = displayTitle,
        bottomBar = {
            NavigationBar {

                // For maximum, cleanest separation of concerns,
                // the Route values are essentially the tabs!
                val bottomBarTabs = listOf(Route.Home, Route.Messages, Route.Bulletins)

                bottomBarTabs.forEach { route ->

                    val localQualifiedRouteName = route::class.qualifiedName!!

                    val selected =
                        qualifiedRouteName?.endsWith(suffix = localQualifiedRouteName) == true

                    NavigationBarItem(
                        selected = selected,
                        label = { Text(route.label) },
                        icon = { Icon(imageVector = route.icon, contentDescription = route.label) },
                        onClick = {
                            // The user changed destinations
                            childNavController.navigate(route) {
                                // Pop up to the start destination of the graph
                                // to avoid building up a large stack of destinations.
                                popUpTo(childNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination
                                // when re-selecting the same item.
                                launchSingleTop = true
                                // Restore state when re-selecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->

        // The inner area executes the nested NavHost graph

        NavHost(
            navController = childNavController,
            startDestination = Route.Home,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable<Route.Home> {
                HomeScreen()
            }
            composable<Route.Messages> {
                MessageScreen(onMessageClicked = {})
            }
            composable<Route.Bulletins> {
                val viewModel: BulletinViewModel = viewModel(factory = AppViewModelProvider.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                BulletinScreen(
                    uiState = uiState,
                    onBulletinClicked = {}
                )
            }
        }
    }
}

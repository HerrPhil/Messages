package com.reference.implementation.messages.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeScreen
import com.reference.implementation.messages.presentation.screens.adminmessage.AdminMessageScreen
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinDetailScreen
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinDetailViewModel
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinScreen
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinViewModel
import com.reference.implementation.messages.presentation.screens.home.HomeScreen
import com.reference.implementation.messages.presentation.screens.home.HomeViewModel
import com.reference.implementation.messages.presentation.screens.message.MessageDetailScreen
import com.reference.implementation.messages.presentation.screens.message.MessageDetailViewModel
import com.reference.implementation.messages.presentation.screens.message.MessageScreen
import com.reference.implementation.messages.presentation.screens.message.MessageViewModel

object MyKeyObject

// 2. Authenticated Hub Level: Houses the drawer and layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedMainParameterHub(
    startDestination: Route,
    defaultRoute: String,
    onRouteSelectTitle: (String?) -> String,
    bottomBarTabs: List<Route>,
) {

    // Isolated NavController for the internal tabs
    val childNavController = rememberNavController()


    // Track the current backstack destination to highlight the correct bottom bar icon
    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Dynamic title mapping based on current destination
    val qualifiedRouteName = currentDestination?.route ?: defaultRoute

    val displayTitle = onRouteSelectTitle(qualifiedRouteName)

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
                bottomBarTabs.forEach { route ->

                    val localQualifiedRouteName = route::class.qualifiedName!!

                    val selected = qualifiedRouteName.endsWith(suffix = localQualifiedRouteName)

                    // navigation bar item option: bigger highlight size
                    // Learn how to roll my own highlight
                    NavigationBarItem(
                        selected = selected,
                        label = { Text(route.label) },
                        onClick = {
                            // Old location of childNavController.navigate(route) {...}
                            // It moved to the Box(modifier = Modifier.clickable(onClick = {...}))
                            // See below
                        },
                        // 1. Turn off the default M3 indicator by making it Transparent
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            // CRUCIAL: Force the native, misaligned item ripple to completely turn off
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            // 2. Build your own roomy custom background container
                            Box(
                                modifier = Modifier
                                    // Tune these dimensions to make the highlight as large as you want!
                                    .size(width = 80.dp, height = 40.dp)
                                    // A. Force clip to your exact visual shape token first
                                    .clip(MaterialTheme.shapes.medium)
                                    // B. Apply your custom background color
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    // C. Attach the clickable action directly to the box layout
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        // Inject a clean M3 bounded ripple that matches the clipped shape
                                        indication = ripple(
                                            bounded = true,
                                            color = MaterialTheme.colorScheme.primary // Optional override
                                        ),
                                        onClick = {
                                            /* Trigger navigation route switch here */
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
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = route.icon,
                                    contentDescription = route.label,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    )


                }
            }
        }
    ) { paddingValues ->
        // The inner area executes the nested NavHost graph, picks a screen from a route.
        // That is the content() of the authenticated shell.
        // All the composable are co-located here.
        // The routes assigned to the bottom tab bar items ensure the correct route is followed.
        // The addition of childNavController.navigate(...) calls to detail/edit screens
        // require this so that these drill-down navigations are handled by the childNavController.
        NavHost(
            navController = childNavController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {

            composable<Route.AdminHome> {
                AdminHomeScreen()
            }

            composable<Route.AdminMessages> {
                AdminMessageScreen()
            }

            composable<Route.Home> {
                val viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(uiState)
            }

            composable<Route.Messages> {
                val viewModel: MessageViewModel = viewModel(factory = AppViewModelProvider.Factory)
                val key:Any = MyKeyObject
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val uiEvents = viewModel.uiEvents
                val onSearchChanged: (String) -> Unit = { newQuery ->
                    viewModel.onSearchChanged(newQuery)
                }
                val onMessageClicked: (Int) -> Unit = { messageId ->
                    // The hub owns the controller and executes the actual routing
                    childNavController.navigate(Route.MessageDetail(id = messageId))
                }
                val onRestoreMessage: (MessageDomainModel) -> Unit = { deletedMessage ->
                    viewModel.onRestoreMessage(deletedMessage)
                }
                val onDeleteMessage: (Int) -> Unit = { messageId ->
                    viewModel.onDeleteMessage(messageId)
                }
                val onToggleReadStatus: (Int, Boolean) -> Unit = { messageId, newReadStatus ->
                    viewModel.onToggleReadStatus(messageId, newReadStatus)
                }

                MessageScreen(
                    uiState,
                    uiEvents,
                    key,
                    searchQuery,
                    onMessageClicked,
                    onSearchChanged,
                    onRestoreMessage,
                    onDeleteMessage,
                    onToggleReadStatus
                )
            }

            composable<Route.MessageDetail> {
                // ViewModel is automatically constructed with the correct ID inside the SavedStateHandle!
                val viewModel: MessageDetailViewModel = viewModel( factory = AppViewModelProvider.Factory)
                // Grab the data stream from the ViewModel
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val onDeleteMessage: (Int) -> Unit = { messageId ->
                    viewModel.onDeleteMessage(messageId)
                }
                val onToggleReadStatus: (Int, Boolean) -> Unit = { messageId, newReadStatus ->
                    viewModel.onToggleReadStatus(messageId, newReadStatus)
                }

                MessageDetailScreen(
                    uiState = uiState,
                    // Executing popBackStack clears this destination off the stack
                    // and returns the user back to the message list smoothly
                    onNavigateBack = { childNavController.popBackStack() },
                    onDeleteMessage,
                    onToggleReadStatus
                )
            }

            composable<Route.Bulletins> {
                val viewModel: BulletinViewModel = viewModel(factory = AppViewModelProvider.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                BulletinScreen(
                    uiState = uiState,
                    onBulletinClicked = {bulletinId ->
                        // The hub owns the controller and executes the actual routing
                        childNavController.navigate(Route.BulletinDetail(id = bulletinId))
                    }
                )
            }

            composable<Route.BulletinDetail> {
                // ViewModel is automatically constructed with the correct ID inside the SavedStateHandle!
                val viewModel: BulletinDetailViewModel = viewModel( factory = AppViewModelProvider.Factory)
                // Grab the data stream from the ViewModel
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                BulletinDetailScreen(
                    uiState = uiState,
                    // Executing popBackStack clears this destination off the stack
                    // and returns the user back to the message list smoothly
                    onNavigateBack = { childNavController.popBackStack() }
                )
            }
        }
    }
}


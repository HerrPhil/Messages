package com.reference.implementation.messages.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

// Remember we can create compile-time strongly typed destinations
// They use class or data class for routes with arguments
// Here is a sample if the app allows messages to be edited.
@Serializable
data class MessageEditXXX(val messageId: Int, val message: String)

// 2. Authenticated Hub Level: Houses the drawer and layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedMainParameterHub(
    startDestination: Route,
    defaultRoute: String,
    titlesLambda: (String?) -> String,
    bottomBarTabs: List<Route>,
    screenNavBuilder: NavGraphBuilder.() -> Unit
) {

    // Isolated NavController for the internal tabs
    val childNavController = rememberNavController()

    // Track the current backstack destination to highlight the correct bottom bar icon
    val navBackStackEntry by childNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Dynamic title mapping based on current destination
    val qualifiedRouteName = currentDestination?.route ?: defaultRoute

    val displayTitle = titlesLambda(qualifiedRouteName)

    // --- Complex Back Handling ---
    // If the user presses back, we want the nested graph to pop its own stack first.
    // If the child graph is already at its starting destination (Home), we let the system
    // bubble the back press up
    // (which could close the app or do nothing, preventing jumping back to Login).
    // --- Back Handler Details ---
    // I was curious why BackHandler works. It plunked in the code and just works.
    // The javadocs, er kotlindocs, cleared up the mystery for me.
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

//                    NavigationBarItem(
//                        selected = selected,
//                        label = { Text(route.label) },
//                        icon = { Icon(imageVector = route.icon, contentDescription = route.label) },
//                        onClick = {
//                            // The user changed destinations
//                            childNavController.navigate(route) {
//                                // Pop up to the start destination of the graph
//                                // to avoid building up a large stack of destinations.
//                                popUpTo(childNavController.graph.findStartDestination().id) {
//                                    saveState = true
//                                }
//                                // Avoid multiple copies of the same destination
//                                // when re-selecting the same item.
//                                launchSingleTop = true
//                                // Restore state when re-selecting a previously selected item
//                                restoreState = true
//                            }
//                        }
//                    )

                    // navigation bar item option: bigger highlight size
                    // Learn how to roll my own highlight
                    NavigationBarItem(
                        selected = selected,
                        label = { Text(route.label) },
                        onClick = {
//                            // The user changed destinations
//                            childNavController.navigate(route) {
//                                // Pop up to the start destination of the graph
//                                // to avoid building up a large stack of destinations.
//                                popUpTo(childNavController.graph.findStartDestination().id) {
//                                    saveState = true
//                                }
//                                // Avoid multiple copies of the same destination
//                                // when re-selecting the same item.
//                                launchSingleTop = true
//                                // Restore state when re-selecting a previously selected item
//                                restoreState = true
//                            }
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
        NavHost(
            navController = childNavController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
            builder = screenNavBuilder
        )
    }
}

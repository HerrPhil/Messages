package com.reference.implementation.messages

import androidx.compose.runtime.Composable
import com.reference.implementation.messages.presentation.navigation.RootAppNavigation

@Composable
fun MessageApp() {

    // On the May 16, 2026 iteration, the message app will add simple navigation between
    // the Login screen and the Message screen. It is now re-factored away.

    // On the May 30, 2026 iteration, the message app now has proper nav controller navigation,
    // both at the main level (unauthenticated/authenticated), and the business feature screens.

    // This is where RootLevelAppNavigation is called()
    // RootLevelAppNavigation() is the new container of the above screens.
    // LoginScreen() lives directly in it.
    // HomeScreen() is pushed down into AuthenticatedMainHub().
    // The Navigation Drawer is placed inside a single container composable,
    // AuthenticatedShell(), that only hosts screens requiring the drawer.

    RootAppNavigation()

}

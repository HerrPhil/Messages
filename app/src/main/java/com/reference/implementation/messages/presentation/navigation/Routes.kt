package com.reference.implementation.messages.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {

    // Abstract property forces every child to declare its icon.
    // Bottom bar icons are strictly local UI visual cues, they do not belong inside
    // the serialized data that defines a navigation path.
    // Therefore, an icon is abstract in the Route and @Transient in the data objects.
    // This tells the compiler to completely ignore the icon field during serialization.
    // When you have a sealed class hierarchy used for navigation or serialization,
    // every single child must have its own @Serializable annotation,
    // even if it's a stateless data object.
    // Kotlinx Serialization favours explicit configuration over implicit magic.

    abstract val icon: ImageVector
    abstract val label: String

    /**
     * Here are the three routes for 'Regular User' accounts
     */

    @Serializable
    data object Home : Route() {
        @Transient
        override val icon = Icons.Default.Home

        @Transient
        override val label = "Home Sweet Home"
    }

    @Serializable
    data object Messages : Route() {
        @Transient
        override val icon = Icons.Default.Email

        @Transient
        override val label = "Your Messages"
    }

    /**
     * Here are the three routes for 'Administrator' account
     */


    @Serializable
    data object AdminHome : Route() {
        @Transient
        override val icon = Icons.Filled.Business

        @Transient
        override val label = "Admin Home"
    }


    @Serializable
    data object AdminMessages : Route() {
        @Transient
        override val icon: ImageVector = Icons.AutoMirrored.Filled.ListAlt
        @Transient
        override val label = "All User Messages"
    }

    /**
     * Here is the one route shared by both Regular and Administrator users
     */

    @Serializable
    data object Bulletins : Route() {
        @Transient
        override val icon: ImageVector = Icons.AutoMirrored.Filled.List
        @Transient
        override val label = "Bulletins"
    }




}

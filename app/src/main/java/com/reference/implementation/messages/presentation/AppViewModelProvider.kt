package com.reference.implementation.messages.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reference.implementation.messages.MessageApplication
import com.reference.implementation.messages.presentation.navigation.AuthenticatedShellViewModel
import com.reference.implementation.messages.presentation.navigation.RootViewModel
import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeViewModel
import com.reference.implementation.messages.presentation.screens.adminmessage.AdminMessageViewModel
import com.reference.implementation.messages.presentation.screens.home.HomeViewModel
import com.reference.implementation.messages.presentation.screens.login.LoginViewModel
import com.reference.implementation.messages.presentation.screens.message.MessageViewModel

object AppViewModelProvider {

    // Home to all the view model initializers!
    val Factory = viewModelFactory {

        // The root view model initializer.
        initializer {
            RootViewModel(
                messageApplication().container.authSessionManager,
                messageApplication().container.roleManager
            )
        }

        // The authenticated shell view model initializer.
        initializer {
            AuthenticatedShellViewModel(
                messageApplication().container.logoutUseCase
            )
        }

        // The login view model initializer.
        initializer {
            LoginViewModel(messageApplication().container.loginUseCase)
        }

        // The home view model initializer.
        initializer {
            HomeViewModel(
                messageApplication().container.getUserDashboardUseCase
            )
        }

        // The message view model initializer.
        initializer {
            MessageViewModel(
                messageApplication().container.loadActiveMessagesUseCase,
                messageApplication().container.getActiveMessagesUseCase,
                messageApplication().container.markMessageAsReadUseCase,
                messageApplication().container.markMessageAsUnreadUseCase,
                messageApplication().container.deleteMessageUseCase,
                messageApplication().container.restoreMessageUseCase,
                messageApplication().container.getMessageUiEventsUseCase
            )
        }

        // The message view model initializer.
        initializer {
            AdminHomeViewModel(
                // TODO add use case(s) to get admin dashboard info
            )
        }

        // The message view model initializer.
        initializer {
            AdminMessageViewModel(
                // TODO add use case(s) to get admin message info
            )
        }

    }
}

/**
 * Here is an extension function to queries for [Application] object and returns an instance of
 * [com.reference.implementation.messages.MessageApplication].
 */
fun CreationExtras.messageApplication(): MessageApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MessageApplication)

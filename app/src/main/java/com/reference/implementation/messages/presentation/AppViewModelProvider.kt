package com.reference.implementation.messages.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reference.implementation.messages.MessageApplication
import com.reference.implementation.messages.presentation.navigation.AuthenticatedShellViewModel
import com.reference.implementation.messages.presentation.navigation.RootViewModel
import com.reference.implementation.messages.presentation.screens.adminhome.AdminHomeViewModel
import com.reference.implementation.messages.presentation.screens.adminmessage.AdminMessageViewModel
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinDetailViewModel
import com.reference.implementation.messages.presentation.screens.bulletin.BulletinViewModel
import com.reference.implementation.messages.presentation.screens.home.HomeViewModel
import com.reference.implementation.messages.presentation.screens.login.LoginViewModel
import com.reference.implementation.messages.presentation.screens.message.MessageDetailViewModel
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
                messageApplication().container.getUserDashboardUseCase,
            )
        }

        // The message view model initializer.
        initializer {
            // 1. grab the SavedStateHandle from CreationExtras ('this' parameter)
            val savedStateHandle = this.createSavedStateHandle()

            MessageViewModel(
                savedStateHandle,
                messageApplication().container.loadActiveMessagesUseCase,
                messageApplication().container.getCachedMessagesUseCase,
                messageApplication().container.markMessageAsReadUseCase,
                messageApplication().container.markMessageAsUnreadUseCase,
                messageApplication().container.deleteMessageUseCase,
                messageApplication().container.restoreMessageUseCase,
                messageApplication().container.markMessageAsImportantUseCase,
                messageApplication().container.markMessageAsNotImportantUseCase,
                messageApplication().container.getMessageUiEventsUseCase
            )
        }

        initializer {
            // 1. grab the SavedStateHandle from CreationExtras ('this' parameter)
            val savedStateHandle = this.createSavedStateHandle()

            // 2. Instantiate the ViewModel cleanly
            MessageDetailViewModel(
                savedStateHandle,
                messageApplication().container.getCachedMessagesUseCase,
                messageApplication().container.markMessageAsReadUseCase,
                messageApplication().container.markMessageAsUnreadUseCase,
                messageApplication().container.deleteMessageUseCase
            )
        }

        // The message view model initializer.
        initializer {
            AdminHomeViewModel(
                messageApplication().container.getAdminDashboardUseCase
            )
        }

        initializer {
            BulletinViewModel(
                messageApplication().container.loadAllBulletinsUseCase,
                messageApplication().container.getAllBulletinsUseCase,
                messageApplication().container.markBulletinAsBookmarkUseCase,
                messageApplication().container.markBulletinAsNotBookmarkUseCase
            )
        }

        initializer {
            // 1. grab the SavedStateHandle from CreationExtras ('this' parameter)
            val savedStateHandle = this.createSavedStateHandle()

            // 2. Instantiate the ViewModel cleanly
            BulletinDetailViewModel(
                savedStateHandle,
                messageApplication().container.loadBulletinUseCase,
                messageApplication().container.getBulletinUseCase
            )
        }

        initializer {
            val savedStateHandle = this.createSavedStateHandle()

            AdminMessageViewModel(
                savedStateHandle,
                messageApplication().container.loadActiveMessagesUseCase,
                messageApplication().container.loadSelectedMessagesUseCase,
                messageApplication().container.getCachedMessagesUseCase,
                messageApplication().container.loadAllUsersUseCase,
                messageApplication().container.getAdminUserInformationUseCase,
                messageApplication().container.markMessageAsReadUseCase,
                messageApplication().container.markMessageAsUnreadUseCase,
                messageApplication().container.deleteMessageUseCase,
                messageApplication().container.restoreMessageUseCase,
                messageApplication().container.markMessageAsImportantUseCase,
                messageApplication().container.markMessageAsNotImportantUseCase,
                messageApplication().container.getMessageUiEventsUseCase
            )
        }
    }
}

/**
 * Here is an extension function to queries for [android.app.Application] object and returns an instance of
 * [com.reference.implementation.messages.MessageApplication].
 */
fun CreationExtras.messageApplication(): MessageApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MessageApplication)

package com.reference.implementation.messages.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reference.implementation.messages.MessageApplication
import com.reference.implementation.messages.presentation.screens.login.LoginViewModel

object AppViewModelProvider {

    // Home to all the view model initializers!
    val Factory = viewModelFactory {

        // First view model initializer to be added,
        // the login view model initializer.
        initializer {
            LoginViewModel(messageApplication().container.loginUseCase)
        }
    }
}

/**
 * Here is an extension function to queries for [Application] object and returns an instance of
 * [com.reference.implementation.messages.MessageApplication].
 */
fun CreationExtras.messageApplication(): MessageApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MessageApplication)

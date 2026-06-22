package com.reference.implementation.messages.presentation.screens.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.home.HomeViewModel

@Composable
fun MessageScreen(
    viewModel: MessageViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    // TODO finish screen that uses UI state
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Your future business feature screens will be placed here
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to the Message Screen!")
    }
}
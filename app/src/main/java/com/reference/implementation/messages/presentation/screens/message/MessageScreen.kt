package com.reference.implementation.messages.presentation.screens.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider

@Composable
fun MessageScreen(
    viewModel: MessageViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Your future business feature screens will be placed here
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome to the Message Screen!")
        if (uiState is MessageUiState.Success) {
            Text(text = "You will have ${(uiState as MessageUiState.Success).list.size} message to view")
        } else {
            Text(text = "Under Construction - TODO")
        }
    }
}
package com.reference.implementation.messages.presentation.screens.home

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    val uiState = viewModel.uiState

    // Handle the other home screen UI states; expect this logic to change as more
    // navigation drawer items are added.
    when (uiState) {
        is HomeUiState.Idle -> {
            HomeDetails()
        }
        is HomeUiState.Loading -> {
            Log.d("HomeScreen", "TODO loading feature")
        }
        is HomeUiState.Success -> {
            Log.d("HomeScreen", "TODO success feature")
        }
        is HomeUiState.Error -> {
            viewModel.cancel() // go back to "Idle" state
            // Share message in a Toast
            Toast
                .makeText(LocalContext.current, uiState.message, Toast.LENGTH_SHORT)
                .show()
        }
    }
}

@Composable
fun HomeDetails() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to the Home Screen!")
    }
}

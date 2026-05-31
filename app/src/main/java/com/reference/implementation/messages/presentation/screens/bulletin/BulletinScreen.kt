package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.screens.home.HomeViewModel

@Composable
fun BulletinScreen(
//    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // Your future business feature screens will be placed here
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to the Bulletin Screen!")
    }
}

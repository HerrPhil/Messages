package com.reference.implementation.messages.presentation.screens.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.reference.implementation.messages.presentation.navigation.LocalShellUiController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    // 1. Grab the shell controller from the environment
    val shellController = LocalShellUiController.current

    // 2. Fire-and-forget toggle scoped strictly to this screen's lifecycle
    DisposableEffect(Unit) {
        shellController.updateTopBarVisibility(false) // Hide shell top bar on entry
        onDispose {
            shellController.updateTopBarVisibility(true) // Restore shell top bar on exit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Message Centre") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // Center the stub text cleanly on the screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Viewing details for message ID: $messageId",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
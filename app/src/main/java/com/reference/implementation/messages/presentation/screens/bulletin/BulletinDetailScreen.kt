package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reference.implementation.messages.presentation.components.ErrorContent
import com.reference.implementation.messages.presentation.components.LoadingContent
import com.reference.implementation.messages.presentation.components.RetryingContent
import com.reference.implementation.messages.presentation.components.Welcome
import com.reference.implementation.messages.presentation.components.getRelativeTimeString
import com.reference.implementation.messages.presentation.navigation.LocalShellUiController
import com.reference.implementation.messages.ui.theme.MessagesTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinDetailScreen(
    uiState: BulletinDetailUiState,
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
                title = {
                    // Dynamically render based on your UI State, too!!!!
                    AnimatedContent(
                        targetState = uiState,
                        label = "TitleStateAnimation"
                    ) { state ->
                        when (state) {
                            is BulletinDetailUiState.Success -> {
                                Text(
                                    text = state.data.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            else -> {
                                Text("Bulletins")
                            }
                        }
                    }
                },
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
        when (val currentState = uiState) {
            is BulletinDetailUiState.Idle -> {
                Welcome("Bulletin Details")
            }

            is BulletinDetailUiState.Loading -> {
                LoadingContent()
            }

            is BulletinDetailUiState.Retrying -> {
                val retryAttempt = "attempt #${currentState.attempt}"
                RetryingContent(retryAttempt)
            }

            is BulletinDetailUiState.Error -> {
                ErrorContent(currentState.message)
            }

            is BulletinDetailUiState.Success -> {
                val data = currentState.data
                BulletinPost(
                    data = data,
                    innerPadding = innerPadding
                )
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun BulletinPostPreview() {
    val data = BulletinUiDetail(
        id = 123,
        userId = 9,
        title = "Email Server Outage",
        post = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
        timestamp = "2026-07-13T22:28:56.321Z"
    )
    val innerPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            BulletinPost(
                data,
                innerPadding
            )
        }
    }
}

@Composable
fun BulletinPost(
    data: BulletinUiDetail,
    innerPadding: PaddingValues
) {
    // We use a base Surface as the flat canvas
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        color = MaterialTheme.colorScheme.surface // Flat surface color
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- 2. Metadata Section (Column prevents horizontal squeeze!) ---
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Posted ${getRelativeTimeString(data.timestamp)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Subtle Line Divider
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 4. Flat Body Text (No Cards, No Inner Scroll Modifiers!)
            item {
                Text(
                    text = data.post,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
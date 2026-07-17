package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                title = { Text(text = "Bulletins") },
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
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()), // Ensure long text is scrollable
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. The Subject Header Pill (Top-Left, Custom Rounded Shape)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.align(Alignment.Start) // Aligns it top-left; curious about TopStart (2 biases)
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 2. Subtle Metadata (Date/Time)
        Text(
            text = "Posted ${getRelativeTimeString(data.timestamp)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 3. The Large Outlined Card with the actual Post Body
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = data.post,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp // Slightly increase line height for readability of long-form text
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp) // Generous inner padding so text does not touch the borders
            )
        }
    }
}
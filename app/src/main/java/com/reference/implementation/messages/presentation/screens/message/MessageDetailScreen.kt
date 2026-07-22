package com.reference.implementation.messages.presentation.screens.message

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
fun MessageDetailScreen(
    uiState: MessageDetailUiState,
    onNavigateBack: () -> Unit,
    onDeleteMessage: (Int) -> Unit,
    onToggleReadStatus: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {

    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    val showDeleteDialog = { showDeleteConfirmationDialog = true }
    val dismissDeleteDialog = { showDeleteConfirmationDialog = false }

    // Grab the shell controller from the environment
    val shellController = LocalShellUiController.current

    // Fire-and-forget toggle scoped strictly to this screen's lifecycle
    DisposableEffect(Unit) {
        shellController.updateTopBarVisibility(false) // Hide shell top bar on entry
        onDispose {
            shellController.updateTopBarVisibility(true) // Restore shell top bar on exit
        }
    }

    // Pinned scroll behaviour allows the app bar to gain an elevation shadow/tint on scroll
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    // Dynamically render based on your UI State, too!!!!
                    AnimatedContent(
                        targetState = uiState,
                        label = "TitleStateAnimation"
                    ) { state ->
                        when (state) {
                            is MessageDetailUiState.Success -> {
                                Text(
                                    text = state.data.subject,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            else -> {
                                Text("Message Centre")
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
                },
                actions = {
                    when (uiState) {
                        is MessageDetailUiState.Success -> {
                            val messageUiDetail = uiState.data

                            // Toggle Read Status
                            IconButton(
                                onClick = {
                                    onToggleReadStatus(
                                        messageUiDetail.id,
                                        !messageUiDetail.read
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (messageUiDetail.read) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                                    contentDescription = if (messageUiDetail.read) "mark unread" else "mark read"
                                )
                            }

                            // Delete Action
                            IconButton(onClick = showDeleteDialog) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Message"
                                )
                            }


                        }

                        else -> {
                            Log.d("MessageDetailScreen", "no actions added")
                        }
                    }

                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->

        when (val currentState = uiState) {
            is MessageDetailUiState.Idle -> {
                Welcome("Message Details")
            }

            is MessageDetailUiState.Loading -> {
                LoadingContent()
            }

            is MessageDetailUiState.Retrying -> {
                val retryAttempt = "attempt #${currentState.attempt}"
                RetryingContent(retryAttempt)
            }

            is MessageDetailUiState.Error -> {
                ErrorContent(currentState.message)
            }

            is MessageDetailUiState.Success -> {
                val data = currentState.data
                MessageContent(
                    data = data,
                    innerPadding = innerPadding
                )
            }
        }
    }


    // Dialog Placement: At the root level, right after Scaffold
    // Only enter if we actually have a Success state AND the dialog flag is true
    if (showDeleteConfirmationDialog && uiState is MessageDetailUiState.Success) {
        val messageUiDetail = uiState.data
        AlertDialog(
            onDismissRequest = dismissDeleteDialog,
            title = { Text("Delete Message?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissDeleteDialog()
                        onDeleteMessage(messageUiDetail.id)
                        // Immediately navigate back.
                        // Otherwise, it is a 'delete' antipattern to display a screen of deleted data
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = dismissDeleteDialog) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun MessageContentPreview() {
    val data = MessageUiDetail(
        id = 123,
        userId = 9,
        read = false,
        subject = "Email Server Outage",
        body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
                + "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
                + "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.",
        createdAt = "2026-07-13T22:28:56.321Z"
    )
    val innerPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            MessageContent(
                data,
                innerPadding
            )
        }
    }
}

@Composable
fun MessageContent(
    data: MessageUiDetail,
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
                        text = "Posted ${getRelativeTimeString(data.createdAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // "Unread" Pill using secondaryContainer for a soft, standard M3 highlight
                    if (!data.read) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Unread",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Subtle Line Divider
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 4. Flat Body Text (No Cards, No Inner Scroll Modifiers!)
            item {
                Text(
                    text = data.body,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
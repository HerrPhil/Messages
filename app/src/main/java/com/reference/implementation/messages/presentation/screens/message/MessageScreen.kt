package com.reference.implementation.messages.presentation.screens.message

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.reference.implementation.messages.domain.model.MessageDomainModel
import com.reference.implementation.messages.presentation.components.DateTimeLabel
import com.reference.implementation.messages.presentation.components.EmptyListContent
import com.reference.implementation.messages.presentation.components.ErrorContent
import com.reference.implementation.messages.presentation.components.LoadingContent
import com.reference.implementation.messages.presentation.components.RetryingContent
import com.reference.implementation.messages.presentation.components.Welcome
import com.reference.implementation.messages.ui.theme.MessagesTheme
import com.reference.implementation.messages.ui.theme.Purple40
import com.reference.implementation.messages.ui.theme.Purple80
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MessageScreen(
    uiState: MessageUiState,
    uiEvents: Flow<MessageUiEvent>,
    key: Any,
    searchQuery: String,
    onMessageClicked: (Int) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRestoreMessage: (MessageDomainModel) -> Unit,
    onDeleteMessage: (Int) -> Unit,
    onToggleReadStatus: (Int, Boolean) -> Unit
) {

    val snackbarHostState = remember { SnackbarHostState() }

    // Grab the lifecycle owner from the current composition context
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Observe the hot event channel safely across UI lifecycles
    LaunchedEffect(key) {
        uiEvents
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { event ->
                when (event) {
                    is MessageUiEvent.showToast -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }

                    is MessageUiEvent.showDeleteSnackbar -> {
                        // This suspends until an action happens or it fades away
                        val result = snackbarHostState.showSnackbar(
                            message = "Message deleted",
                            actionLabel = "UNDO",
                            // FOR TESTING PURPOSES:
                            // when actionLabel is not null (e.g. UNDO)
                            // then by not setting duration the duration defaults to INDEFINITE
                            duration = SnackbarDuration.Short
                        )

                        if (result == SnackbarResult.ActionPerformed) {
                            // The user clicked UNDO! Fire and forget back into the UDF loop!
                            onRestoreMessage(event.deletedMessage)
                        }
                    }
                }
            }
    }

    // When you use Compose elements like AnimatedContent or CrossFade, to mention two examples,
    // they pass a local snapshot copy of the state into the lambda block (usually named "it",
    // or renamed to "currentState").
    // You must strictly use that local lambda variable, because local variables cannot be mutated
    // by other threads, allowing Kotlin's smart casting to work flawlessly.
    when (val currentState = uiState) {
        is MessageUiState.Idle -> {
            Welcome("Messages")
        }

        is MessageUiState.Loading -> {
            LoadingContent()
        }

        is MessageUiState.Retrying -> {
            val retryAttempt = "attempt #${currentState.attempt}"
            RetryingContent(retryAttempt)
        }

        is MessageUiState.Error -> {
            ErrorContent(currentState.message)
        }

        is MessageUiState.Success -> {
            val list = currentState.list
            MessageDetails(
                searchQuery = searchQuery,
                onSearchValueChanged = { searchInput ->
                    onSearchChanged(searchInput)
                },
                onDelete = { messageId ->
                    onDeleteMessage(messageId)
                },
                onToggleReadStatus = { messageId, newReadStatus ->
                    onToggleReadStatus(messageId, newReadStatus)
                },
                onMessageClicked = onMessageClicked,
                list = list,
                snackbarHostState = snackbarHostState
            )
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
fun MessageDetailsPreview() {
    val searchQuery = "test"
    val list = List(4, { index ->
        MessageUiDetail(
            id = index,
            subject = "test subject",
            body = "test message $index",
            read = false,
            userId = 123,
            createdAt = "2026-07-13T22:28:56.321Z"
        )
    })
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            MessageDetails(
                searchQuery,
                onSearchValueChanged = {},
                onDelete = {},
                onToggleReadStatus = { id, newReadStatus -> },
                onMessageClicked = {},
                list = list,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}

@Composable
fun MessageDetails(
    searchQuery: String,
    onSearchValueChanged: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onToggleReadStatus: (Int, Boolean) -> Unit,
    onMessageClicked: (Int) -> Unit,
    list: List<MessageUiDetail>,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {

    // Here is the search-and-scroll feature for message details

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        // The root content container is a standard Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Consumes the Scaffold top bar/ system spacing
        ) {
            MessageSearchInput(
                searchQuery = searchQuery,
                onSearchValueChanged,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // This Box is the "Anchor Container"
            Box(modifier = Modifier.weight(1f)) {

                // Scrollable List (Fills the remaining vertical space)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = list,
                        key = { message -> message.id },
                    ) { message ->
                        SwipeableMessageItem(
                            message = message,
                            onDelete = { onDelete(message.id) },
                            onToggleReadStatus = {
                                onToggleReadStatus(message.id, !message.read)
                            },
                            onItemClicked = { onMessageClicked(message.id) },
                            // THE FIX: Generate the scoped modifier here where the scope is valid!
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                if (list.isEmpty() && searchQuery.isNotEmpty()) {
                    EmptyListContent("No matches for $searchQuery")
                }
                if (list.isEmpty() && searchQuery.isEmpty()) {
                    EmptyListContent("No messages")
                }
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
fun MessageSearchInputPreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            MessageSearchInput(
                searchQuery = "test",
                onSearchValueChanged = {}
            )
        }
    }
}

@Composable
fun MessageSearchInput(
    searchQuery: String,
    onSearchValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    OutlinedTextField(
        value = searchQuery, // 1. Reads the current string state from the ViewModel
        onValueChange = { searchInput ->
            // 2. Pipes the fresh keystroke characters back down immediately
            onSearchValueChanged(searchInput)
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search // Changes keyboard 'Enter' to a magnifying glass
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                // Optional
                // keyboardController?.hide()
                // Otherwise leave it blank to keep the keyboard wide open so the user can keep
                // modifying their query, which perfectly matches how a reactive live-filter screen
                // should behave.
            }
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search messages") },
        placeholder = { Text(text = "Search messages...") },
        shape = MaterialTheme.shapes.medium, // keeping your preferred medium token
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}


@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun SwipeableMessageItemPreview() {
    val messageUiDetail = MessageUiDetail(
        id = 123,
        subject = "test message item",
        body = "Here is a preview test",
        read = false,
        userId = 456,
        createdAt = "2026-07-13T22:28:56.321Z"
    )
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            SwipeableMessageItem(
                message = messageUiDetail,
                onDelete = {},
                onToggleReadStatus = {},
                onItemClicked = {}
            )
        }
    }
}


enum class DragAnchors {
    Start,   // Resting state (Normal)
    Reveal,  // Swiped partially lest to expose action buttons
    Deleted  // Swiped 100% left off the screen to delete
}

/**
 * The "SwipeToDismissBox" wraps the message item card. It requires two key elements:
 * 1. backgroundContent: The layout that sits behind your wrapped card (where your "Delete",
 * "Mark Read", or "Archive" buttons live).
 * 2. enableDismissFromEndToSTart: Setting this to true handles the swipe-left behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMessageItem(
    message: MessageUiDetail,
    onDelete: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onItemClicked: () -> Unit, // TODO use where item row can be clicked to drill down to details
    modifier: Modifier = Modifier
) {

    val density = LocalDensity.current
    val scope = rememberCoroutineScope() // used when closing a half-swiped item

    // 1. PERFECTLY CLEAN CONSTRUCTOR: Absolutely zero configuration parameters
    val draggableState = remember { AnchoredDraggableState(initialValue = DragAnchors.Start) }

    // 2. MODERN GUARD RAIL: Monitor 'targetValue' to handle interceptions or deletions
    LaunchedEffect(draggableState.targetValue) {
        when (draggableState.targetValue) {
            DragAnchors.Deleted -> {
                // If you want to intercept/abort a deletion dynamically (e.g. a flag check)
                val isDeletionAllowed = true

                if (isDeletionAllowed) {
                    onDelete()
                } else {
                    // Force a smooth spring-back animation if the action is blocked
                    launch { draggableState.animateTo(DragAnchors.Start) }
                }
            }

            else -> { /* No action needed for Start or Reveal targets */
            }
        }
    }

    val screenWidthPx = with(density) {
        LocalWindowInfo.current.containerSize.width.dp.toPx()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // THE TRICK: Enforces identical child heights
    ) {
        // --- BACKGROUND LAYER ---
        Box(
            modifier = Modifier
                .fillMaxSize() // This will now stretch cleanly to match the foreground height!
                .background(
                    MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                )
                .onSizeChanged { layoutSize ->
                    val actionTrayWidthPx = layoutSize.width.toFloat() * 0.7f

                    draggableState.updateAnchors(
                        DraggableAnchors {
                            // Must be mapped sequentially from the lowest pixel value to the highest
                            DragAnchors.Deleted at -screenWidthPx     // e.g. -1000f
                            DragAnchors.Reveal at -actionTrayWidthPx  // e.g. -300f
                            DragAnchors.Start at 0f                   // e.g. 0f (Resting position)
                        }
                    )
                }
        ) {
            MessageSwipeActions(
                currentReadStatus = message.read,
                onToggleRead = {
                    onToggleReadStatus()
                    scope.launch { draggableState.animateTo(DragAnchors.Start) }
                },
                onDelete = {
                    onDelete()
                    scope.launch { draggableState.animateTo(DragAnchors.Start) }
                }
            )
        }

        // --- FOREGROUND LAYER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = draggableState.requireOffset().roundToInt(),
                        y = 0
                    )
                }
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal
                )
        ) {
            MessageItemCard(
                message = message,
                onItemClicked = onItemClicked
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSwipeActions(
    currentReadStatus: Boolean,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // You can use the progress or target state to tint the background dynamically
    val backgroundColor = MaterialTheme.colorScheme.errorContainer

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterEnd // Align button to the right side
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .pointerInput(Unit) { /* Let pointer events pass through to children */ }
        ) {
            // Option Action #1: Toggle Read State
            SwipeActionButtonWithTooltip(
                icon = if (currentReadStatus) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                contentDescription = "Toggle Read Status",
                tooltipText = if (currentReadStatus) "mark unread" else "mark read",
                onClick = onToggleRead
            )
            // Optional Action #2: Delete Action
            SwipeActionButtonWithTooltip(
                icon = Icons.Default.Delete,
                contentDescription = "Delete Action",
                tooltipText = "Delete",
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionButtonWithTooltip(
    icon: ImageVector,
    contentDescription: String,
    tooltipText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Maintain the visibility state of the tooltip automatically
    val tooltipState = rememberTooltipState(isPersistent = false)

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
                2.dp
            ),
        tooltip = {
            // 2. The visual popup bubble design
            PlainTooltip {
                Text(text = tooltipText)
            }
        },
        state = tooltipState
    ) {
        // 3. The anchor element that triggers the tooltip on hover
        IconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
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
fun MessageItemCardPreview() {
    val messageUiDetail = MessageUiDetail(
        id = 123,
        subject = "test message item",
        body = "Here is a preview test",
        read = false,
        userId = 456,
        createdAt = "2026-07-13T22:28:56.321Z"
    )
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            MessageItemCard(messageUiDetail, { })
        }
    }
}

@Composable
fun MessageItemCard(message: MessageUiDetail, onItemClicked: () -> Unit) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onItemClicked()
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubjectLine(message.subject, message.read)
            BodyLine(message.body)
            DateTimeLabel("Created at", message.createdAt)
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
fun SubjectLinePreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            SubjectLine("Developer Meeting", true)
        }
    }
}

@Composable
fun SubjectLine(subject: String, read: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed size avatar/image
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "subject line row",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = subject,
            style = MaterialTheme.typography.titleMedium,
            // My display font only does regular and Bold
            fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        if (!read) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isSystemInDarkTheme()) Purple40 else Purple80,
                        shape = CircleShape
                    )
                    .padding(end = 8.dp)
            )
        }

        // Arrow icon (will now display correctly)
        Box(
            modifier = Modifier
                .padding(end = 0.dp) // Padding applied to the Box, not the Icon
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "navigate to message detail screen",
                modifier = Modifier.size(24.dp)
            )
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
fun BodyLinePreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            BodyLine("Developer Meeting asdf asdf asdf ertg")
        }
    }
}

@Composable
fun BodyLine(body: String) {
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
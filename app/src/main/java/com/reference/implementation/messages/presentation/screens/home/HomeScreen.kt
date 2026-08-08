package com.reference.implementation.messages.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reference.implementation.messages.presentation.components.ErrorContent
import com.reference.implementation.messages.presentation.components.RetryingContent
import com.reference.implementation.messages.presentation.components.SkeletonText
import com.reference.implementation.messages.presentation.components.Welcome
import com.reference.implementation.messages.presentation.components.shimmerLoadingAnimation
import com.reference.implementation.messages.ui.theme.MessagesTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState
) {

    // When you use Compose elements like AnimatedContent or CrossFade, to mention two examples,
    // they pass a local snapshot copy of the state into the lambda block (usually named "it",
    // or renamed to "currentState").
    // You must strictly use that local lambda variable, because local variables cannot be mutated
    // by other threads, allowing Kotlin's smart casting to work flawlessly.
    AnimatedContent(targetState = uiState, label = "DashboardStateTransition") { currentState ->
        when (currentState) {
            is HomeUiState.Idle -> {
                Welcome("Home")
            }

            is HomeUiState.Loading -> {
                val loadingState = HomeUiState.Success(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                HomeDetails(loadingState)
            }

            is HomeUiState.Retrying -> {
                val retryAttempt = "attempt #${currentState.attempt}"
                RetryingContent(retryAttempt)
            }

            is HomeUiState.Error -> {
                ErrorContent(currentState.message)
            }

            is HomeUiState.Success -> {
                HomeDetails(currentState)
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
fun HomeDetailsLoadingPreview() {
    MessagesTheme {
        HomeDetails(
            currentState = HomeUiState.Success(
                userName = null,
                userEmail = null,
                unreadMessages = null,
                readMessages = null,
                roles = null,
                permissions = null
            )
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
fun HomeDetailsPreview() {
    MessagesTheme {
        HomeDetails(
            currentState = HomeUiState.Success(
                userName = "Phillip Wray",
                userEmail = "pwray@test.com",
                unreadMessages = 3,
                readMessages = 2,
                roles = listOf("Tech Lead", "Developer"),
                permissions = listOf("Code", "Test", "Lead")
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDetails(
    currentState: HomeUiState.Success,
    modifier: Modifier = Modifier
) {

    // Re-factoring exercise.

    // 1. Initialize the local scroll behaviour for the M3 color blend/lift
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 2. Replace the Box with a Scaffold and attach the nested scroll connection
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // 2(a). Pass the scroll behavior down into the nested scroll system
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // 3. The shell top app bar is handling the resting state for now
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                // Crucial: Consumes the Scaffold's window/app bar spacing
                .padding(innerPadding)
                // Keeps your custom inner padding clean
                .padding(24.dp)
                .fillMaxSize()
                // Makes the column scrollable to trigger the lift
                .verticalScroll(rememberScrollState()),
        ) {

            // Re-factoring exercise: move user and email into the refined ProfileHeader function

            ProfileHeader(currentState.userName, currentState.userEmail)

            // Re-factoring exercise: Move messages summary info into an OutlinedCard
            MessageSummaryInfo(currentState.readMessages, currentState.unreadMessages)

            // Re-factoring exercise: make roles a sub-heading with permissions details
            RolesAndPermissions(currentState.roles, currentState.permissions)
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
fun ProfileHeaderAbsentPreview() {
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            ProfileHeader(
                username = null,
                email = null
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
fun ProfileHeaderPreview() {
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            ProfileHeader("John", "john@learn.com")
        }
    }
}

@Composable
fun ProfileHeader(
    username: String?, // nullable - temporal absence of data - supports skeleton feature
    email: String? // nullable - temporal absence of data - supports skeleton feature
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        // Keeps icon and text perfectly centered horizontally
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Shell (Always visible, optionally shimmers or holds icon)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                // The Background is necessary to color the background of the CircleShape.
                // I cannot rely on the shimmerLoadingAnimation to draw it.
                .background(color = MaterialTheme.colorScheme.primaryContainer)
                .shimmerLoadingAnimation(
                    isLoading = username == null,
                    shimmerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            if (username != null) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp)) // Gives the text room to breathe

        // 2. The Vertical Text Stack
        Column(
            verticalArrangement = Arrangement.Center
        ) {

            if (username == null) {
                // 1. The Shimmering Skeleton Shell
                SkeletonText()
            } else {
                // 2. The Actual Content Payload
                Text(
                    text = username,
                    // Your sharp display font
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (email == null) {
                // 1. The Shimmering Skeleton Shell
                // Make the email skeleton slightly wider so it looks like a real email block!
                SkeletonText(
                    modifier = Modifier
                        .width(180.dp)
                        .height(20.dp)
                )
            } else {
                Text(
                    text = email,
                    // Your clean, legible body font
                    style = MaterialTheme.typography.bodyMedium,
                    // Mutes the email slightly for visual contrast
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun MessageSummaryInfoAbsentPreview() {
    MessagesTheme() {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            MessageSummaryInfo(
                read = null,
                unread = null
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
fun MessageSummaryInfoPreview() {
    MessagesTheme() {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            MessageSummaryInfo(null, null)
//            MessageSummaryInfo(10, 4)
        }
    }
}

@Composable
fun MessageSummaryInfo(
    read: Int?,
    unread: Int?
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Messages",
                // Medium (Regular) font
                style = MaterialTheme.typography.titleMedium,
                // My display font only does regular and Bold
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (read == null) {
                    // 1. The Shimmering Skeleton Shell
                    // Make the read skeleton slightly shorter so
                    SkeletonText(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp),
                        shape = MaterialTheme.shapes.medium

                    )
                } else {
                    Text(text = "Read: $read", style = MaterialTheme.typography.bodyMedium)
                }

                if (unread == null) {
                    // 1. The Shimmering Skeleton Shell
                    // Make the unread skeleton slightly shorter so
                    SkeletonText(
                        modifier = Modifier
                            .width(100.dp)
                            .height(32.dp),
                        shimmerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )

                } else {
                    // Highlight unread count with a vibrant container or color tint
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            text = "$unread Unread",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
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
fun RolesAndPermissionsAbsentPreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            RolesAndPermissions(
                roles = null,
                permissions = null
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
fun RolesAndPermissionsPreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            RolesAndPermissions(
                listOf("Tech Lead", "Developer"),
                listOf("write code", "check code")
            )
        }
    }
}

@Composable
fun RolesAndPermissions(
    roles: List<String>?,
    permissions: List<String>?
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (roles == null) {
                // 1. The Shimmering Skeleton Shell
                SkeletonText()
            } else {
                Text(
                    text = roles.joinToString(separator = ", "),
                    style = MaterialTheme.typography.titleMedium,
                    // My display font only does regular and Bold
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Assigned Permissions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (permissions == null) {
                // 1. The Shimmering Skeleton Shell
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Vary the widths slightly to look organic!
                    SkeletonText(
                        modifier = Modifier
                            .width(84.dp)
                            .height(28.dp), // 👈 Match AssistChip height
                        shape = RoundedCornerShape(8.dp) // 👈 Match AssistChip rounded pill shape
                    )
                    SkeletonText(
                        modifier = Modifier
                            .width(72.dp)
                            .height(28.dp),
                        shape = RoundedCornerShape(8.dp) // 👈 Match AssistChip rounded pill shape
                    )
                }
            } else {
                // This automatically wraps chips to the next line if they run out of screen width
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    permissions.forEach { permission ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = permission,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

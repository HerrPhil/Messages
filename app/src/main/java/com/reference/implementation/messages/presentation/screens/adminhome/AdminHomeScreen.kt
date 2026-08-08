package com.reference.implementation.messages.presentation.screens.adminhome

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reference.implementation.messages.presentation.components.ErrorContent
import com.reference.implementation.messages.presentation.components.RetryingContent
import com.reference.implementation.messages.presentation.components.SkeletonText
import com.reference.implementation.messages.presentation.components.Welcome
import com.reference.implementation.messages.presentation.navigation.Route
import com.reference.implementation.messages.ui.theme.MessagesTheme

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun AdminHomeScreenPreview() {
    val uiState = AdminHomeUiState.Success(
        usersCount = 6,
        summaryMessages = "3 / 5",
        bulletinsCount = 2
    )
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            AdminHomeScreen(uiState)
        }
    }
}

@Composable
fun AdminHomeScreen(uiState: AdminHomeUiState) {

    // The sampler code starts with a Scaffold with TopBar and title "<current-screen>"
    // My AuthenticatedShell provides the Scaffold and TopBar.
    // The UI content here becomes the content slot API trailing lambda of AuthenticatedShell
    when (val currentState = uiState) {

        is AdminHomeUiState.Idle -> Welcome("Admin Home")

        is AdminHomeUiState.Loading -> {
            AdminHomeDetails(usersCount = null, summaryMessages = null, bulletinsCount = null)
        }

        is AdminHomeUiState.Retrying -> {
            val retryAttempt = "attempt #${currentState.attempt}"
            RetryingContent(retryAttempt)
        }

        is AdminHomeUiState.Error -> {
            ErrorContent(currentState.message)
        }

        is AdminHomeUiState.Success -> {
            AdminHomeDetails(
                usersCount = currentState.usersCount,
                summaryMessages = currentState.summaryMessages,
                bulletinsCount = currentState.bulletinsCount
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
fun AdminHomeDetailsPreview() {
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            AdminHomeDetails(
                usersCount = 6,
                summaryMessages = "3 / 5",
                bulletinsCount = 2
            )
        }
    }
}

@Composable
fun AdminHomeDetails(
    usersCount: Int?,
    summaryMessages: String?,
    bulletinsCount: Int?
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)

    ) {
        Text(
            text = "Overview",
            style = MaterialTheme.typography.headlineSmall
        )

        var userCountDescription: String? = null
        if (usersCount != null) {
            userCountDescription = "$usersCount total users"
        }
        AdminDashboardCard(
            title = "User Summary",
            description = userCountDescription, // my "x / y pending messages"
            icon = Icons.Default.Person,
            onClick = {
                Toast
                    .makeText(
                        context,
                        "Click the ${Route.AdminMessages.label} tab",
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }

        )

        var summaryMessagesDescription: String? = null
        if (summaryMessages != null) {
            summaryMessagesDescription = "$summaryMessages pending messages"
        }
        AdminDashboardCard(
            title = "Message Summary",
            description = summaryMessagesDescription, // my "x / y pending messages"
            icon = Icons.Default.Email,
            onClick = {
                Toast
                    .makeText(
                        context,
                        "Click the ${Route.AdminMessages.label} tab",
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }

        )

        var bulletinCountDescription: String? = null
        if (bulletinsCount != null) {
            bulletinCountDescription = "$bulletinsCount total bulletins"
        }
        AdminDashboardCard(
            title = "Bulletin Summary",
            description = bulletinCountDescription, // my "bookmark" bulletins
            icon = Icons.Default.Notifications,
            onClick = {
                Toast
                    .makeText(context, "Click the ${Route.Bulletins.label} tab", Toast.LENGTH_SHORT)
                    .show()
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
fun AdminDashboardCardPreview() {
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            AdminDashboardCard(
                title = "Manage Messages",
                description = null,
//                description = "3 active messages",
                icon = Icons.Default.Email,
                onClick = {}
            )
        }
    }
}

@Composable
fun AdminDashboardCard(
    title: String,
    description: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(text = title, style = MaterialTheme.typography.titleMedium)

                if (description == null) {
                    // 1. The Shimmering Skeleton Shell
                    // Make the read skeleton slightly shorter so
                    SkeletonText(
                        modifier = Modifier
                            .width(80.dp)
                            .height(20.dp),
                        shape = MaterialTheme.shapes.medium,
                        shimmerColor = MaterialTheme.colorScheme.surfaceTint
                    )
                } else {
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}


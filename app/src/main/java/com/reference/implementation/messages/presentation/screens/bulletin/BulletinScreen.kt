package com.reference.implementation.messages.presentation.screens.bulletin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reference.implementation.messages.presentation.AppViewModelProvider
import com.reference.implementation.messages.presentation.components.DateTimeLabel
import com.reference.implementation.messages.presentation.components.ErrorContent
import com.reference.implementation.messages.presentation.components.LoadingContent
import com.reference.implementation.messages.presentation.components.RetryingContent
import com.reference.implementation.messages.presentation.components.Welcome
import com.reference.implementation.messages.ui.theme.MessagesTheme

@Composable
fun BulletinScreen(
    onBulletinClicked: (Int) -> Unit,
    viewModel: BulletinViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val currentState = uiState) {
        is BulletinUiState.Idle -> {
            Welcome("Bulletins")
        }

        is BulletinUiState.Loading -> {
            LoadingContent()
        }

        is BulletinUiState.Retrying -> {
            val retryAttempt = "attempt #${currentState.attempt}"
            RetryingContent(retryAttempt)
        }

        is BulletinUiState.Error -> {
            ErrorContent(currentState.message)
        }

        is BulletinUiState.Success -> {
            val list = currentState.list
            BulletinDetails(
                list = list,
                onBulletinClicked = onBulletinClicked
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
fun BulletinDetailsPreview() {
    val searchQuery = "test"
    val list = List(4, { index ->
        BulletinUiDetail(
            id = index,
            userId = 456,
            title = "test bulletin item $index",
            post = "Here is a preview test",
            timestamp = "2026-07-1${index}T22:28:56.321Z"
        )
    })
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            BulletinDetails(
                list = list,
                onBulletinClicked = {}
            )
        }
    }
}

@Composable
fun BulletinDetails(
    list: List<BulletinUiDetail>,
    onBulletinClicked: (Int) -> Unit
) {
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
            key = { bulletin -> bulletin.id }
        ) { bulletin ->
            BulletinItemCard(
                bulletin = bulletin,
                onItemClicked = { onBulletinClicked(bulletin.id) }
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
fun BulletinItemCardPreview() {
    val bulletinUiDetail = BulletinUiDetail(
        id = 123,
        userId = 456,
        title = "test message item",
        post = "Here is a preview test",
        timestamp = "2026-07-13T22:28:56.321Z"
    )
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            BulletinItemCard(bulletinUiDetail, { })
        }
    }
}


@Composable
fun BulletinItemCard(bulletin: BulletinUiDetail, onItemClicked: () -> Unit) {
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
            TitleLine(bulletin.title)
            Post(bulletin.post) // multi-line
            DateTimeLabel("Created",  bulletin.timestamp)
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
fun TitleLinePreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            TitleLine("Scheduled MailServer Maintenance")
        }
    }
}

@Composable
fun TitleLine(title: String) {
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
                imageVector = Icons.Default.Notifications,
                contentDescription = "Title Line Row",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }


        // Title: self-explanatory
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            // My display font only does Regular and Bold
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        // Arrow Icon
        // (will now display correctly aka will not shrink egregiously when the title fills right)
        Box(
            modifier = Modifier.padding(end = 0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "navigate to the bulletin detail screen",
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
fun PostPreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Post("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.")
        }
    }
}

@Composable
fun Post(content: String) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
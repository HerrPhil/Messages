package com.reference.implementation.messages.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reference.implementation.messages.ui.theme.MessagesTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun WelcomePreview() {
    MessagesTheme {
        // Wrapping in a Surface forces the preview to use your theme's background color
        Surface(color = MaterialTheme.colorScheme.surface) {
            Welcome("Home")
        }
    }
}

@Composable
fun Welcome(content: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to $content")
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun LoadingContentPreview() {
    MessagesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            LoadingContent()
        }
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}


@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun RetryingContentPreview() {
    MessagesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            RetryingContent("Attempt #2")
        }
    }
}

@Composable
fun RetryingContent(retryAttempt: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val orangeColor = Color(0xFFFF9800)

        CircularProgressIndicator(
            color = orangeColor,
            trackColor = Color.LightGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection jittery...Retry attempt $retryAttempt",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
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
fun ErrorContentPreview() {
    MessagesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ErrorContent("Something went wrong")
        }
    }
}

@Composable
fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }

}

@Composable
fun EmptyListContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }

}


// Convert a raw ISO 8601 string value to a formatted date/time value
fun getRelativeTimeString(
    isoString: String,
    zoneId: ZoneId = ZoneId.systemDefault() // Automatically uses user's phone timezone
): String {
    return try {
        // Parse the string on-the-fly here!
        val pastInstant = Instant.parse(isoString)
        val now = Instant.now()
        val duration = Duration.between(pastInstant, now)


        val seconds = duration.seconds
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()

        when {
            seconds < 60 -> "Just now"
            minutes == 1L -> "1 minute ago"
            minutes < 60 -> "$minutes minutes ago"
            hours == 1L -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> {
                // Convert UTC to local zone for accurate date display
                val localDateTime = pastInstant.atZone(zoneId)

                // Format: "July 14, 2026 at 1:33 PM"
                val formatter =
                    DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                "on ${localDateTime.format(formatter)}"
            }
        }
    } catch (e: Exception) {
        "Unknown time"
    }
}



@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun CreatedAtLinePreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            CreatedAtLine("2026-07-13T22:28:56.321Z")
        }
    }
}

@Composable
fun CreatedAtLine(createdAt: String) {
    Text(
        text = "Created ${getRelativeTimeString(createdAt)}",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}

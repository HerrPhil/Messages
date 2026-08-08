package com.reference.implementation.messages.presentation.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.reference.implementation.messages.data.audit.Audit
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
        contentAlignment = Alignment.Center
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
fun ErrorAndRetryContentPreview() {
    MessagesTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ErrorAndRetryContent("Something is wrong", {})
        }
    }
}


@Composable
fun ErrorAndRetryContent(errorMessage: String, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRefresh) { Text("Retry") }
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
        Audit.createInstance().writeLog(e.message ?: "no message")
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
fun DateTimeLabelPreview() {
    MessagesTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            DateTimeLabel("Created", "2026-07-13T22:28:56.321Z")
        }
    }
}

@Composable
fun DateTimeLabel(label: String, theTimestamp: String) {
    Text(
        text = "$label ${getRelativeTimeString(theTimestamp)}",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun Modifier.shimmerLoadingAnimation(
    isLoading: Boolean,
    // Defaults to theme's surfaceVariant, but allows custom overrides!
    // The surfaceVariant is a good trade-off for items or Text widgets.
    shimmerColor: Color = MaterialTheme.colorScheme.surfaceVariant
): Modifier {
    if (!isLoading) return this

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing)
        ),
        label = "ShimmerTranslation"
    )

    // Build gradient using your dynamic theme color instead of hardcoded LightGray!
    val shimmerColors = listOf(
        shimmerColor.copy(alpha = 0.4f),
        shimmerColor.copy(alpha = 0.9f),
        shimmerColor.copy(alpha = 0.4f)
    )

    return this.drawWithCache {
        // Cache the brush
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim, 0f),
            end = Offset(translateAnim + size.width / 1.5f, size.height)
        )

        onDrawWithContent {
            // STEP 1: ALWAYS DRAW THE BASE BACKGROUND COLOR
            // This is what puts the blue circle behind the icon.
            drawRect(color = shimmerColor)

            // STEP 2: ONLY OVERLAY THE SHIMMER SWEEP IF LOADING
            if (isLoading) {
                drawRect(brush = brush)
            }

            // STEP 3: DRAW THE ICON/CONTENT ON TOP
            drawContent()
        }
    }
}

@Composable
fun SkeletonText(
    // 1. Accept a modifier with a default value
    modifier: Modifier = Modifier,
    shimmerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    shape: Shape = RoundedCornerShape(4.dp) // 👈 Default to 4.dp for text lines
) {
    Box(
        modifier = modifier
            .widthIn(min = 120.dp) // Sets your UX designer's minimum width!
            .height(24.dp) // Matches the font line-height
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmerLoadingAnimation(isLoading = true, shimmerColor = shimmerColor)
    )

}

// Reusable 400ms Horizontal Slide Transitions for Detail Screens

val detailEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(400)
        ) + fadeIn(animationSpec = tween(400))
    }

val detailExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(400)
    ) + fadeOut(animationSpec = tween(400))
}

val detailPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(400)
        ) + fadeIn(animationSpec = tween(400))
    }

val detailPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(400)
        ) + fadeOut(animationSpec = tween(400))
    }

// 🟢 Helper Function: Encapsulates detail transitions into a single custom builder
inline fun <reified T : Any> NavGraphBuilder.detailComposable(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable<T>(
        enterTransition = detailEnterTransition,
        exitTransition = detailExitTransition,
        popEnterTransition = detailPopEnterTransition,
        popExitTransition = detailPopExitTransition,
        content = content
    )
}
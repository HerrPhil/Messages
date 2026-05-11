package com.reference.implementation.messages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.reference.implementation.messages.ui.theme.MessagesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessagesTheme {

                // UI re-factoring: the goal is to push any Scaffold down to any Screen
                // @Composable function.
                // Each "Screen" has its own Scaffold as necessary for top bar/bottom bar features.


//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }


                // If I get around to creating a theme for this app with material design website,
                // then I can put the MessageApp() inside a Surface block, to have a surface
                // container with theme-based background color, probably fill to max size.
                MessageApp()

            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MessagesTheme {
        Greeting("Android")
    }
}
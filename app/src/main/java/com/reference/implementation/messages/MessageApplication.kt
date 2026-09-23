package com.reference.implementation.messages

import android.app.Application
import dagger.hilt.android.HiltAndroidApp


// Hilt requires a custom Application class to serve
// as the root node for its compile-time code generation.
@HiltAndroidApp
class MessageApplication : Application() {
}
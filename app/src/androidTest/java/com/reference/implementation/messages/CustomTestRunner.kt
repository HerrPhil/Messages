package com.reference.implementation.messages

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        // Forces the test runner to spawn a HiltTestApplication container context
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
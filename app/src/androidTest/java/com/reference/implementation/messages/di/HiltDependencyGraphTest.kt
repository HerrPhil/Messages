package com.reference.implementation.messages.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HiltDependencyGraphTest {

    // THE GATEKEEPER: This rule forces Hilt to aggressively assemble and validate
    // every single binding, module, and qualifier factory link in the project module graph.
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun verifyHiltDependencyGraphIsCompileSafe() {
        // Forces Hilt to initialize the graph completely.
        // If a single constructor factory argument or binding is missing,
        // this line will instantly throw an initialization exception and
        // fail the test build!
        hiltRule.inject()
    }
}
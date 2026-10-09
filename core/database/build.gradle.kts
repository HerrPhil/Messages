plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.reference.implementation.messages.database"
    compileSdk = 35 // Ensure this matches your :core:data target sdk

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 💡 Copy the exact Kotlin target syntax from your :core:data file right here!
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }
}

// 💡 The clean, standalone KSP configuration block works beautifully with Room 2.8.5+!
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Layer Linkage: Allows Room local cache entities to map to domain objects
    implementation(project(":core:domain"))

    // Local Storage Layer Engine (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler) // KSP handles code generation seamlessly

    // Dagger Hilt Core
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Standard Kotlin & JUnit 4 Test Runners
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.junit)

}

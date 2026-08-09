/*
 * Copyright (c) 2026 AtaraxiaGoddess. All rights reserved.
 */

import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

extensions.configure<ApplicationExtension> {
    namespace = "com.ataraxiagoddess.budgetbrewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ataraxiagoddess.budgetbrewer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "OFFLINE_MODE", "true")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.work)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)   // KSP, not kapt

    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.gotrue.kt)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.multiplatform.settings)
    implementation(libs.kotlinx.datetime)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Charts
    implementation(libs.mpandroidchart)   // Use the alias
    implementation(libs.calendarview)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)

    // Logging
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.dimezis.blurview)

    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink.android)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.fragment.ktx)
}

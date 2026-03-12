plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ataraxiagoddess.budgetbrewer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ataraxiagoddess.budgetbrewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("boolean", "OFFLINE_MODE", "true")
    }

    packaging { resources.excludes.add("org/threeten/bp/format/ChronologyText.properties") }
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

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.viewpager2)

    // Charts
    implementation(libs.mpandroidchart)   // Use the alias
    implementation(libs.calendarview)
    implementation(libs.material.calendarview) {
        exclude(group = "com.android.support")
        exclude(group = "org.threeten")
    }
    implementation(libs.threetenbp)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)

    // Logging
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.dimezis.blurview)

    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.process)
}
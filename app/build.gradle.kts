plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shelf.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shelf.launcher"
        minSdk = 26
        targetSdk = 35
        // On GitHub every build gets a higher number, so updates install over the old version.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 1 + runNumber
        versionName = "2.0.$runNumber"

        // Where the launcher looks for new versions.
        buildConfigField("String", "UPDATE_REPO", "\"dvir9032/tvOS-Launcher\"")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // The signing key comes from GitHub secrets (never stored in the repo).
    // A fixed key means every new APK installs over the previous one.
    val keystorePath = System.getenv("SHELF_KEYSTORE_PATH")
    val keystorePassword = System.getenv("SHELF_KEYSTORE_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("shelf") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "shelf"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the secret (e.g. building on your own computer) fall back to the debug key.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "shelf" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

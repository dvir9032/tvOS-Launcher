// Colorful placeholder TV apps, used only to record the demo video on the emulator.
// They have no code: each one is just a name, a banner and the framework's empty Activity.
plugins {
    id("com.android.application")
}

android {
    namespace = "com.shelf.demo"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "app"
    productFlavors {
        create("cinema") { applicationId = "com.shelf.demo.cinema"; resValue("string", "app_name", "Cinema") }
        create("arcade") { applicationId = "com.shelf.demo.arcade"; resValue("string", "app_name", "Arcade") }
        create("music") { applicationId = "com.shelf.demo.music"; resValue("string", "app_name", "Music") }
        create("sports") { applicationId = "com.shelf.demo.sports"; resValue("string", "app_name", "Sports") }
        create("kids") { applicationId = "com.shelf.demo.kids"; resValue("string", "app_name", "Kids") }
        create("news") { applicationId = "com.shelf.demo.news"; resValue("string", "app_name", "News") }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

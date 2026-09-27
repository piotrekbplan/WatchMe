plugins {
    id("watchme.android.application")
    id("watchme.android.compose")
    id("watchme.android.hilt")
}

android {
    namespace = "pl.watchme"

    defaultConfig {
        applicationId = "pl.watchme"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "EPG_BASE_URL", "\"https://piotrekbplan.github.io/WatchMe/\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}

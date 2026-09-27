import java.util.Properties

plugins {
    id("watchme.android.application")
    id("watchme.android.compose")
    id("watchme.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name, "")

android {
    namespace = "pl.watchme"

    defaultConfig {
        applicationId = "pl.watchme"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "EPG_BASE_URL", "\"https://piotrekbplan.github.io/WatchMe/\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${localProperty("firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${localProperty("firebase.projectId")}\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:lineup"))
    implementation(project(":feature:ranking"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.logback.android)

    testImplementation(project(":core:testing"))
}

plugins {
    id("watchme.android.library")
    id("watchme.android.compose")
}

android {
    namespace = "pl.watchme.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}

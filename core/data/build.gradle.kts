plugins {
    id("watchme.android.library")
    id("watchme.android.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "pl.watchme.data"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":epg-contract"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.slf4j.api)

    testImplementation(project(":core:testing"))
}

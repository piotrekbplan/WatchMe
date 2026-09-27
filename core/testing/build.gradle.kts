plugins {
    id("watchme.kotlin-jvm")
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.assertk)
    api(libs.turbine)
}

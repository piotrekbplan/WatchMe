plugins {
    id("watchme.kotlin-jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
}

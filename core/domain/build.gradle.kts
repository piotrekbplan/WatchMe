plugins {
    id("watchme.kotlin-jvm")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(project(":core:testing"))
}

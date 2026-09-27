plugins {
    id("watchme.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("pl.watchme.epgjob.app.MainKt")
    applicationName = "epg-job"
}

dependencies {
    implementation(project(":epg-contract"))
    implementation(libs.okhttp)
    implementation(libs.kaml)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testRuntimeOnly(libs.logback.classic)
}

plugins {
    id("watchme.kotlin-jvm")
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.serialization.json)
}

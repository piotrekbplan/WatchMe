import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun Project.configureUnitTests() {
    dependencies {
        "testImplementation"(platform(libs.library("junit-bom")))
        "testImplementation"(libs.library("junit-jupiter"))
        "testImplementation"(libs.library("assertk"))
        "testRuntimeOnly"(libs.library("junit-platform-launcher"))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 36

internal const val JAVA_VERSION = 17

internal fun Project.configureJavaToolchain() {
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(JAVA_VERSION))
    }
}

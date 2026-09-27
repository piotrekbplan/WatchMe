import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("watchme.android.library")
            pluginManager.apply("watchme.android.compose")
            pluginManager.apply("watchme.android.hilt")

            dependencies {
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:designsystem"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-navigation-compose"))
                "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("slf4j-api"))

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
                "testImplementation"(libs.library("turbine"))
            }
        }
    }
}

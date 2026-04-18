// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.rust.android) apply false
}

// Remove these lines:
// var versionName = "1.0.0"
// var versionCode = 210

import org.gradle.api.provider.Property
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GetVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun writeVersion() {
        val versionFile = project.layout.projectDirectory.file("app/build/version.txt").asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(versionName.get())
    }
}

tasks.register<GetVersionTask>("getVersion") {
    // Value comes from gradle.properties; falls back to 1.0.0 if not set.
    versionName.set(providers.gradleProperty("APP_VERSION_NAME").orElse("1.0.0"))
}

// These stay available for legacy submodules and scripts.
rootProject.ext.set("appVersionName", providers.gradleProperty("APP_VERSION_NAME").orElse("1.6.9").get())
rootProject.ext.set("appVersionCode", providers.gradleProperty("APP_VERSION_CODE").orElse("325").get().toInt())
rootProject.ext.set("applicationId", "cock.crest.purrfectsnap.lite")
// buildHash: when PurrfectSnap or Snapchat is updated, mappings become outdated and auto-regenerate.
// Include version code so each release has a different hash; use random for uniqueness within same version.
rootProject.ext.set(
    "buildHash",
    (properties["debug_build_hash"] as String?)
        ?: "${rootProject.ext["appVersionCode"]}_${java.security.SecureRandom()
            .nextLong(Long.MAX_VALUE / 1000L, Long.MAX_VALUE)
            .toString(16)}"
)

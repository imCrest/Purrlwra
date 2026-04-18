import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

abstract class GenerateCommonBuildInfoTask : DefaultTask() {
    @get:Input
    abstract val sourceText: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(sourceText.get())
    }
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".common"
    compileSdk = 36

    buildFeatures {
        aidl = true
        compose = true
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main") {
            java.srcDir(layout.buildDirectory.dir("generated/source/buildInfo/kotlin"))
        }
    }
}

val commonVersionName = rootProject.ext["appVersionName"].toString()
val commonVersionCode = rootProject.ext["appVersionCode"].toString().toInt()
val commonApplicationId = rootProject.ext["applicationId"].toString()
val commonPackagePath = commonApplicationId.replace('.', '/')
val commonBuildInfoPackage = "$commonApplicationId.common"
val commonBuildHash = rootProject.ext["buildHash"].toString()
val commonGitHash = providers.environmentVariable("GITHUB_SHA")
    .orElse(providers.environmentVariable("GIT_COMMIT"))
    .orElse(providers.gradleProperty("gitHash"))
    .orElse(providers.systemProperty("git.hash"))
    .orElse("unknown")
    .get()
val commonSifEndpoint = properties["debug_sif_endpoint"]?.toString()
    ?: "https://github.com/SnapEnhance/resources/raw/refs/heads/main/sif"
val commonBuildInfoSource = """
    package $commonBuildInfoPackage

    object CommonBuildInfo {
        const val VERSION_NAME = "$commonVersionName"
        const val VERSION_CODE = $commonVersionCode
        const val APPLICATION_ID = "$commonApplicationId"
        const val BUILD_HASH = "$commonBuildHash"
        const val GIT_HASH = "$commonGitHash"
        const val SIF_ENDPOINT = "$commonSifEndpoint"
    }
""".trimIndent()

val generateCommonBuildInfo = tasks.register<GenerateCommonBuildInfoTask>("generateCommonBuildInfo") {
    sourceText.set(commonBuildInfoSource)
    outputFile.set(
        layout.buildDirectory.file(
            "generated/source/buildInfo/kotlin/$commonPackagePath/common/CommonBuildInfo.kt"
        )
    )
}

tasks.matching { it.name.startsWith("pre") && it.name.endsWith("Build") }.configureEach {
    dependsOn(generateCommonBuildInfo)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(generateCommonBuildInfo)
}

dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation(libs.coroutines)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.documentfile)
    implementation(libs.rhino)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)
    implementation(libs.rhino.android) {
        exclude(group = "org.mozilla", module = "rhino-runtime")
    }

    compileOnly(libs.androidx.activity.ktx)
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.navigation.compose)
    compileOnly(libs.androidx.material.icons.core)
    compileOnly(libs.androidx.material.ripple)
    compileOnly(libs.androidx.material.icons.extended)
    compileOnly(libs.androidx.material3)

    implementation(project(":mapper"))
}

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.android.build.api.artifact.MultipleArtifact
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.KeyStore
import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.2.20-2.0.3"
}

abstract class GenerateDexIntegrityAsset : org.gradle.api.DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexFiles: ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val pinnedSha256: Property<String>

    @get:org.gradle.api.tasks.InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val r8DexDir: DirectoryProperty

    @get:org.gradle.api.tasks.InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun writeIntegrityAsset() {
        val pinned = pinnedSha256.orNull?.trim().orEmpty()
        if (pinned.isNotBlank()) {
            val assetDir = outputDir.get().asFile.apply { mkdirs() }
            assetDir.resolve("ps_integrity.bin").writeText(pinned.lowercase())
            return
        }

        fun mapToApkEntry(file: java.io.File): String {
            val segments = file.invariantSeparatorsPath.split("/")
            val idx = segments.indexOfLast { it == "assets" }
            return if (idx != -1 && idx + 1 < segments.size) {
                ("assets/" + segments.drop(idx + 1).joinToString("/"))
            } else {
                file.name
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val allFiles = mutableListOf<java.io.File>()
        allFiles += r8DexDir.asFileTree.files.filter { it.extension == "dex" }
        allFiles += assetsDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "dex" }.toList()

        allFiles
            .map { mapToApkEntry(it) to it }
            .sortedBy { it.first }
            .forEach { (_, file) ->
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }
        val assetDir = outputDir.get().asFile.apply { mkdirs() }
        assetDir.resolve("ps_integrity.bin").writeText(expectedHash)
    }
}

fun computeKeystoreCertSha256(storeFile: File, storePass: String, keyAlias: String, keyPass: String = storePass): String? {
    if (!storeFile.exists()) return null
    return runCatching {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        storeFile.inputStream().use { ks.load(it, storePass.toCharArray()) }
        val cert = ks.getCertificate(keyAlias) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

fun gradleOrEnv(name: String, providers: org.gradle.api.provider.ProviderFactory): String? {
    return providers.gradleProperty(name).orNull ?: System.getenv(name)
}

android {
    namespace = rootProject.ext["applicationId"].toString()
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = File(System.getProperty("user.home"), ".android/purrfectsnap-lite-release.keystore")
            storePassword = gradleOrEnv("PS_RELEASE_STORE_PASSWORD", providers)
            keyAlias = gradleOrEnv("PS_RELEASE_KEY_ALIAS", providers)
            keyPassword = gradleOrEnv("PS_RELEASE_KEY_PASSWORD", providers)
        }
    }

    defaultConfig {
        val autoCertSha = providers.provider {
            val releaseStore = File(System.getProperty("user.home"), ".android/purrfectsnap-lite-release.keystore")
            val releaseStorePass = gradleOrEnv("PS_RELEASE_STORE_PASSWORD", providers)
            val releaseKeyAlias = gradleOrEnv("PS_RELEASE_KEY_ALIAS", providers)
            val releaseKeyPass = gradleOrEnv("PS_RELEASE_KEY_PASSWORD", providers)
            if (!releaseStorePass.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank()) {
                computeKeystoreCertSha256(
                    releaseStore,
                    storePass = releaseStorePass,
                    keyAlias = releaseKeyAlias,
                    keyPass = releaseKeyPass ?: releaseStorePass
                )
            } else {
                computeKeystoreCertSha256(
                    File(System.getProperty("user.home"), ".android/debug.keystore"),
                    storePass = "android",
                    keyAlias = "androiddebugkey"
                )
            }.orEmpty()
        }
        val expectedCertSha256 = providers.gradleProperty("EXPECTED_CERT_SHA256")
            .orElse(providers.gradleProperty("psExpectedCertSha256"))
            .orElse(autoCertSha)
            .orElse("")
        applicationId = rootProject.ext["applicationId"].toString()
        versionCode = rootProject.ext["appVersionCode"].toString().toInt()
        versionName = rootProject.ext["appVersionName"].toString()
        minSdk = 30
        targetSdk = 36
        multiDexEnabled = true
        buildConfigField("String", "EXPECTED_CERT_SHA256", "\"${expectedCertSha256.get()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles += file("proguard-rules.pro")
            val releaseStore = File(System.getProperty("user.home"), ".android/purrfectsnap-lite-release.keystore")
            val releaseStorePass = gradleOrEnv("PS_RELEASE_STORE_PASSWORD", providers)
            val releaseKeyAlias = gradleOrEnv("PS_RELEASE_KEY_ALIAS", providers)
            if (releaseStore.exists() && !releaseStorePass.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            (properties["debug_flavor"] == null).also {
                isDebuggable = !it
                isMinifyEnabled = it
                isShrinkResources = it
            }
            proguardFiles += file("proguard-rules.pro")
        }
    }

    flavorDimensions += "abi"
    productFlavors {
        packaging {
            jniLibs {
                excludes += "**/*_neon.so"
            }
            resources {
                excludes += "DebugProbesKt.bin"
                excludes += "okhttp3/internal/publicsuffix/**"
                excludes += "META-INF/*.version"
                excludes += "META-INF/services/**"
                excludes += "META-INF/*.kotlin_builtins"
                excludes += "META-INF/*.kotlin_module"
            }
        }
        create("core") {
            dimension = "abi"
        }
        create("armv8") {
            ndk {
                abiFilters += "arm64-v8a"
            }
            dimension = "abi"
        }
        create("armv7") {
            ndk {
                abiFilters += "armeabi-v7a"
            }
            dimension = "abi"
        }
        create("all") {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            dimension = "abi"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
    onVariants { variant ->
        val flavorName = variant.flavorName
        if (properties["debug_flavor"] == flavorName) {
            // variant.makeDefault.set(true) // This is no longer supported
        }

        variant.outputs.forEach { output ->
            val variantOutput = output as com.android.build.api.variant.impl.VariantOutputImpl
            variantOutput.outputFileName.set(
                when {
                    variant.name.startsWith("core") -> "core.apk"
                    else -> "purrfectsnap-lite_${rootProject.ext["appVersionName"]}-${variant.name}.apk"
                }
            )
        }

        val integrityTaskName = "generate${variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}DexIntegrity"
        val capitalizedVariant = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val dexOutput = layout.buildDirectory.dir("intermediates/dex/${variant.name}")
        val integrityTask = tasks.register(integrityTaskName, GenerateDexIntegrityAsset::class.java) {
            tasks.findByName("minify${capitalizedVariant}WithR8")?.let { dependsOn(it) }
            dexFiles.from(
                fileTree(dexOutput) { include("**/*.dex") }
            )
            variantName.set(variant.name)
            r8DexDir.set(layout.buildDirectory.dir("intermediates/dex/${variant.name}/minify${capitalizedVariant}WithR8"))
            assetsDir.set(layout.projectDirectory.dir("src/main/assets"))
            outputDir.set(layout.buildDirectory.dir("generated/integrity/${variant.name}/assets"))
            pinnedSha256.set(providers.gradleProperty("psIntegrityPinnedSha256").orElse(""))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(integrityTask, GenerateDexIntegrityAsset::outputDir)
    }

}

dependencies {
    fun fullImplementation(dependencyNotation: Any) {
        compileOnly(dependencyNotation)
        for (flavorName in listOf("armv8", "armv7", "all")) {
            dependencies.add("${flavorName}Implementation", dependencyNotation)
        }
    }

    implementation(project(":core"))
    compileOnly(files("../core/libs/LSPosed-api-1.0-SNAPSHOT.jar"))
    implementation(project(":common"))
    implementation(project(":native"))
    implementation(libs.androidx.documentfile)
    implementation("androidx.browser:browser:1.8.0")
    implementation(libs.gson)
    implementation(libs.smart.exception.java)
    implementation(files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"))
    implementation(libs.osmdroid.android)
    implementation(libs.rhino)
    implementation(libs.androidx.activity.ktx)
    fullImplementation(platform(libs.androidx.compose.bom))
    fullImplementation(libs.bcprov.jdk18on)
    fullImplementation(libs.androidx.navigation.compose)
    fullImplementation(libs.androidx.material.icons.core)
    fullImplementation(libs.androidx.material.ripple)
    fullImplementation(libs.androidx.material.icons.extended)
    fullImplementation(libs.androidx.material3)
    fullImplementation(libs.coil.compose)
    fullImplementation(libs.coil.video)
    fullImplementation(libs.colorpicker.compose)
    fullImplementation(libs.androidx.ui.tooling.preview)
    properties["debug_flavor"]?.let {
        debugImplementation(libs.androidx.ui.tooling)
    }
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)
    // Patching pipeline dependencies
    implementation(files("libs/apkzlib.jar"))
    implementation(files("libs/ManifestEditor-1.0.2.jar"))
    implementation(libs.apksig)
    implementation(libs.dexlib2)
    implementation(libs.jsoup)
    implementation("com.google.auto.value:auto-value-annotations:1.10.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.json:json:20240303")
    implementation(libs.fetch) {
        exclude(group = "androidx.room", module = "room-runtime")
    }
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // --- COMPOSE: explicit modern UI/Foundation for widthIn/wrapContentWidth ----
    fullImplementation(libs.foundation)
    fullImplementation(libs.ui)
    fullImplementation(libs.foundation.layout)

    // Animated navigation + transitions (Accompanist)
    fullImplementation(libs.accompanist.navigation.animation)
}

afterEvaluate {
    properties["debug_flavor"]?.toString()
        ?.let { flavor -> tasks.findByName("install${flavor.replaceFirstChar { it.uppercase() }}Debug") }
        ?.doLast {
            runCatching {
                val packageName = properties["debug_package_name"]?.toString() ?: return@runCatching
                val devicesProcess = ProcessBuilder("adb", "devices")
                    .redirectErrorStream(true)
                    .start()
                val devices = devicesProcess.inputStream.bufferedReader().useLines { lines ->
                    lines.drop(1)
                        .mapNotNull { line ->
                            line.split("\t").firstOrNull()?.takeIf { it.isNotEmpty() }
                        }
                        .toList()
                }
                devicesProcess.waitFor()

                runBlocking {
                    devices.forEach { device ->
                        launch {
                            ProcessBuilder("adb", "-s", device, "shell", "am", "force-stop", packageName)
                                .redirectErrorStream(true)
                                .start()
                                .apply { waitFor() }
                            delay(500)
                            ProcessBuilder("adb", "-s", device, "shell", "am", "start", packageName)
                                .redirectErrorStream(true)
                                .start()
                                .apply { waitFor() }
                        }
                    }
                }
            }
        }
}

properties["debug_flavor"]?.let {
    configurations.all {
        exclude(group = "androidx.profileinstaller", "profileinstaller")
    }
}

import java.io.File
import java.util.Locale
import java.util.Properties
import java.util.zip.CRC32
import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

data class CargoTarget(
    val triple: String,
    val abi: String,
    val toolchainPrefix: String,
    val apiLevel: Int
) {
    val envSuffix: String = triple.replace('-', '_')
    val envSuffixUpper: String = envSuffix.uppercase(Locale.ROOT)
    val taskSuffix: String = abi.split('-', '_').joinToString("") { fragment ->
        fragment.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
    }
}

plugins {
    alias(libs.plugins.androidLibrary)
}

val nativeBuildHash = rootProject.ext.get("buildHash").toString()
val nativeLibFileName = "lib${nativeBuildHash}.so"
val nativeApplicationId = rootProject.ext["applicationId"].toString()
val nativePackagePath = nativeApplicationId.replace('.', '/')
val nativeBuildInfoPackage = "$nativeApplicationId.nativelib"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val desiredNdkVersion = (findProperty("androidNdkVersion") as? String)
    ?: "28.2.13676358"

val sdkDirPath = localProperties.getProperty("sdk.dir")?.trimEnd('/', '\\')
    ?: System.getenv("ANDROID_SDK_ROOT")?.trimEnd('/', '\\')
    ?: System.getenv("ANDROID_HOME")?.trimEnd('/', '\\')

val ndkHomePath = (System.getenv("ANDROID_NDK_HOME")?.trimEnd('/', '\\')
    ?: sdkDirPath?.let { "$it${File.separator}ndk${File.separator}$desiredNdkVersion" }
    ?: error("Unable to locate the Android NDK. Set ANDROID_NDK_HOME or define sdk.dir in local.properties"))
    .replace("\\:", ":")
val ndkHome = File(ndkHomePath)
require(ndkHome.exists()) { "Configured NDK directory $ndkHome does not exist" }

val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
val osArch = System.getProperty("os.arch").lowercase(Locale.ROOT)
val preferredHostTag = when {
    osName.contains("windows") -> "windows-x86_64"
    osName.contains("mac") && osArch.contains("arm") -> "darwin-arm64"
    osName.contains("mac") -> "darwin-x86_64"
    else -> "linux-x86_64"
}

val prebuiltRoot = File(ndkHome, "toolchains/llvm/prebuilt")
require(prebuiltRoot.exists()) { "Unable to locate NDK prebuilts under $prebuiltRoot" }
val hostTagCandidates = mutableListOf(preferredHostTag)
if (preferredHostTag == "darwin-arm64") {
    hostTagCandidates.add("darwin-x86_64")
}
if (preferredHostTag.startsWith("darwin")) {
    prebuiltRoot.listFiles()
        ?.filter { it.isDirectory && it.name.startsWith("darwin-") }
        ?.forEach { hostTagCandidates.add(it.name) }
}
val hostTag = hostTagCandidates
    .firstOrNull { tag -> File(prebuiltRoot, "$tag/bin").exists() }
    ?: error("Unable to locate NDK toolchain bin directory for $preferredHostTag under $prebuiltRoot")

val toolchainBin = File(prebuiltRoot, "$hostTag/bin")

val isWindowsHost = hostTag.startsWith("windows")
val clangSuffix = if (isWindowsHost) ".cmd" else ""
val llvmArExecutable = File(toolchainBin, if (isWindowsHost) "llvm-ar.exe" else "llvm-ar")
require(llvmArExecutable.exists()) { "Unable to find llvm-ar executable at ${llvmArExecutable.absolutePath}" }
val toolchainPath = listOf(toolchainBin.absolutePath, System.getenv("PATH") ?: "")
    .filter { it.isNotBlank() }
    .joinToString(File.pathSeparator)

fun File.toWslPath(): String {
    val normalized = absolutePath.replace("\\", "/")
    return if (normalized.length >= 2 && normalized[1] == ':') {
        val drive = normalized[0].lowercaseChar()
        "/mnt/$drive${normalized.substring(2)}"
    } else normalized
}

fun File.toUnixLikePath(): String = absolutePath.replace("\\", "/")

// In this environment, WSL doesn't mount all Windows drives (e.g. /mnt/d may be missing).
// When using WSL's bash.exe, stage sources into a C:-backed temp directory and build from there.
val wslStagingDir = File(System.getProperty("java.io.tmpdir"), "purrfectsnap-wsl-native").apply { mkdirs() }

val explicitBash = System.getenv("BASH_PATH")?.takeIf { it.isNotBlank() }?.let { File(it) }
val bashCandidates = mutableListOf<File>()
explicitBash?.let { bashCandidates.add(it) }
if (isWindowsHost) {
    val systemRoot = System.getenv("WINDIR") ?: "C:\\Windows"
    bashCandidates.add(File(systemRoot, "System32/bash.exe"))
    System.getenv("ProgramFiles")?.let {
        bashCandidates.add(File(it, "Git/bin/bash.exe"))
        bashCandidates.add(File(it, "Git/usr/bin/bash.exe"))
    }
    System.getenv("ProgramFiles(x86)")?.let {
        bashCandidates.add(File(it, "Git/bin/bash.exe"))
    }
}
val resolvedBash = bashCandidates.firstOrNull { it.exists() }
val bashExecutablePath = resolvedBash?.absolutePath ?: "bash"
val requiresWslPath = resolvedBash?.absolutePath?.contains("system32\\bash.exe", ignoreCase = true) == true

val nativeAbisProp = (findProperty("nativeAbis") as? String)
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("NATIVE_ABIS")
    ?: "arm64-v8a,armeabi-v7a"
var enabledNativeAbis = nativeAbisProp
    .split(',', ';')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()
    .ifEmpty { setOf("arm64-v8a", "armeabi-v7a") }

val requestedTasks = gradle.startParameter.taskNames.joinToString(" ")
val wantsArmv7 = requestedTasks.contains("armv7", ignoreCase = true)
val wantsArmv8 = requestedTasks.contains("armv8", ignoreCase = true)
if (wantsArmv7 && !wantsArmv8) {
    enabledNativeAbis = setOf("armeabi-v7a")
} else if (wantsArmv8 && !wantsArmv7) {
    enabledNativeAbis = setOf("arm64-v8a")
} else if (wantsArmv7 && wantsArmv8) {
    enabledNativeAbis = enabledNativeAbis + setOf("armeabi-v7a", "arm64-v8a")
}

val cargoTargets = listOf(
    CargoTarget(
        triple = "aarch64-linux-android",
        abi = "arm64-v8a",
        toolchainPrefix = "aarch64-linux-android",
        apiLevel = 28,
    ),
    CargoTarget(
        triple = "armv7-linux-androideabi",
        abi = "armeabi-v7a",
        toolchainPrefix = "armv7a-linux-androideabi",
        apiLevel = 28,
    ),
).filter { enabledNativeAbis.contains(it.abi) }
require(cargoTargets.isNotEmpty()) {
    "No native ABIs enabled. Check the nativeAbis/NATIVE_ABIS configuration. Current value: $nativeAbisProp"
}

val defaultRustToolchain = if (hostTag.startsWith("windows")) {
    "stable-x86_64-pc-windows-gnu"
} else {
    "stable"
}

val rustToolchain = (findProperty("rustToolchain") as? String)
    ?: System.getenv("RUST_TOOLCHAIN")
    ?: defaultRustToolchain

val omvllVersion = (findProperty("omvllVersion") as? String)
    ?: System.getenv("OMVLL_VERSION")
    ?: "1.4.1"

val omvllLinuxAsset = (findProperty("omvllLinuxAsset") as? String)
    ?: System.getenv("OMVLL_LINUX_ASSET")
    ?: "omvll_v1-4-1_linux_2025-10-01T09.33.59.tar.gz"

val omvllArchiveUrl = (findProperty("omvllArchiveUrl") as? String)
    ?.takeIf { it.isNotBlank() }

fun clangExecutableFor(target: CargoTarget): File {
    val executable = File(toolchainBin, "${target.toolchainPrefix}${target.apiLevel}-clang$clangSuffix")
    require(executable.exists()) { "Unable to find clang executable at ${executable.absolutePath}" }
    return executable
}

fun clangPlusPlusExecutableFor(target: CargoTarget): File {
    val executable = File(toolchainBin, "${target.toolchainPrefix}${target.apiLevel}-clang++$clangSuffix")
    require(executable.exists()) { "Unable to find clang++ executable at ${executable.absolutePath}" }
    return executable
}

// Register rustup tasks
val rustupTasks = cargoTargets.map { target ->
    tasks.register<Exec>("rustup${target.taskSuffix}") {
        workingDir = file("rust")
        commandLine("rustup", "target", "add", "--toolchain", rustToolchain, target.triple)
    }
}

// Ensure rustup task ordering
rustupTasks.forEachIndexed { index, task ->
    if (index > 0) {
        task.configure {
            mustRunAfter(rustupTasks[index - 1])
        }
    }
}

// Register sync & cargo build tasks
val syncTasks = cargoTargets.mapIndexed { index, target ->
    val rustupTask = rustupTasks[index]

    val stageWslTask = tasks.register<Sync>("stageWslNative${target.taskSuffix}") {
        enabled = requiresWslPath
        from(project.layout.projectDirectory.asFile) {
            into("native")
            include("build-native.sh")
            // Ensure LF endings inside WSL (CRLF breaks `set -euo pipefail`).
            filter<FixCrLfFilter>("eol" to FixCrLfFilter.CrLf.newInstance("lf"))
        }
        from(project.layout.projectDirectory.asFile) {
            into("native")
            include("omvll_config.py")
            include("rust/**")
            exclude("rust/target/**")
        }
        from(rootProject.layout.projectDirectory.asFile) {
            include("config/config.json")
        }
        into(wslStagingDir)
    }

    val cargoTask = tasks.register<Exec>("cargoBuild${target.taskSuffix}") {
        group = "build"
        dependsOn(rustupTask)
        dependsOn(stageWslTask)
        workingDir = project.layout.projectDirectory.dir("rust").asFile
        val buildScript = project.layout.projectDirectory.file("build-native.sh").asFile
        val scriptCommand = if (requiresWslPath) {
            val wslDir = File(wslStagingDir, "native").toWslPath()
            val escapedDir = wslDir.replace("'", "'\"'\"'")
            val command = "cd '$escapedDir' && bash './${buildScript.name}' ${target.triple}"
            listOf(
                bashExecutablePath,
                "-lc",
                command
            )
        } else {
            val normalizedPath = buildScript.toUnixLikePath()
            listOf(
                bashExecutablePath,
                normalizedPath,
                target.triple
            )
        }
        inputs.file(buildScript)
        commandLine = scriptCommand
        val ndkPath = if (requiresWslPath) ndkHome.toWslPath() else ndkHome.absolutePath
        environment("ANDROID_NDK_HOME", ndkPath)
        environment(
            "OMVLL_CONFIG",
            (if (requiresWslPath) File(wslStagingDir, "native/omvll_config.py") else project.layout.projectDirectory.file("omvll_config.py").asFile).let {
                if (requiresWslPath) it.toWslPath() else it.absolutePath
            }
        )
        environment("RUST_TOOLCHAIN", rustToolchain)
        environment("OMVLL_VERSION", omvllVersion)
        environment("OMVLL_LINUX_ASSET", omvllLinuxAsset)
        omvllArchiveUrl?.let { environment("OMVLL_ARCHIVE_URL", it) }
    }

    tasks.register("syncNative${target.taskSuffix}") {
        dependsOn(cargoTask)
        val outputLibName = nativeLibFileName
        val wslCandidate = File(wslStagingDir, "native/rust/target/${target.triple}/release/libpurrfectsnap.so")
        val localCandidate = layout.projectDirectory.file("rust/target/${target.triple}/release/libpurrfectsnap.so").asFile
        val outputDir = layout.buildDirectory.dir("rustJniLibs/android/${target.abi}")
        inputs.property("outputLibName", outputLibName)
        inputs.files(wslCandidate, localCandidate)
        outputs.dir(outputDir)
        outputs.upToDateWhen { false }
        val checksumsDir = layout.buildDirectory.dir("checksums")
        doLast {
            val sourceFile = listOf(wslCandidate, localCandidate).firstOrNull { it.exists() }
                ?: error(
                    "Native library not found for ${target.abi}. Looked in: " +
                        "${wslCandidate.absolutePath}, ${localCandidate.absolutePath}"
                )

            val abiDir = outputDir.get().asFile
            abiDir.mkdirs()

            project.copy {
                from(sourceFile)
                into(abiDir)
                rename { outputLibName }
            }
            project.copy {
                from(sourceFile)
                into(abiDir)
                rename { "libpurrfectsnap.so" }
            }

            val file = File(abiDir, outputLibName)
            val crc = CRC32()
            crc.update(file.readBytes())
            val checksumsDirFile = checksumsDir.get().asFile
            checksumsDirFile.mkdirs()
            File(checksumsDirFile, target.abi).writeText(crc.value.toString())
        }
    }
}

// Ensure checksums dir exists at configuration time so Gradle 9 input validation passes
// (it is populated by syncTasks' doLast)
layout.buildDirectory.dir("checksums").get().asFile.mkdirs()

val generateChecksumsFile = tasks.register("generateChecksumsFile") {
    dependsOn(syncTasks)
    val generatedDir = layout.buildDirectory.dir("generated/source/checksums/kotlin")
    val checksumsFile = generatedDir.get().asFile.resolve("Checksums.kt")
    val checksumsDir = layout.buildDirectory.dir("checksums").get().asFile
    inputs.dir(checksumsDir)
    outputs.file(checksumsFile)

    doLast {
        val checksums = checksumsDir.listFiles()?.associate {
            it.name to it.readText().toLong()
        } ?: emptyMap()

        checksumsFile.parentFile.mkdirs()
        checksumsFile.writeText(
            """
            package cock.crest.purrfectsnap.lite.nativelib

            object Checksums {
                val checksums = mapOf(
                    ${checksums.entries.joinToString(",\n") { (abi, checksum) -> "\"$abi\" to ${checksum}L" }}
                )
            }
            """.trimIndent()
        )
    }
}

android {
    namespace = rootProject.ext["applicationId"].toString() + ".nativelib"
    compileSdk = 36

    ndkVersion = desiredNdkVersion

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("build/rustJniLibs/android")
            java.srcDir(layout.buildDirectory.dir("generated/source/checksums/kotlin"))
            java.srcDir(layout.buildDirectory.dir("generated/source/buildInfo/kotlin"))
        }
    }
}

val generateNativeBuildInfo = tasks.register("generateNativeBuildInfo") {
    val outputFile = layout.buildDirectory.file("generated/source/buildInfo/kotlin/$nativePackagePath/nativelib/NativeBuildInfo.kt")

    inputs.property("nativeName", nativeBuildHash)
    inputs.property("modulePackageName", nativeApplicationId)
    outputs.file(outputFile)

    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package $nativeBuildInfoPackage

            object NativeBuildInfo {
                const val NATIVE_NAME = "$nativeBuildHash"
                const val MODULE_PACKAGE_NAME = "$nativeApplicationId"
            }
            """.trimIndent()
        )
    }
}

tasks.matching { it.name.startsWith("pre") && it.name.endsWith("Build") }.configureEach {
    syncTasks.forEach { dependsOn(it) }
    dependsOn(generateChecksumsFile)
    dependsOn(generateNativeBuildInfo)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(generateChecksumsFile)
    dependsOn(generateNativeBuildInfo)
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("com.google.code.gson:gson:2.10.1")
}

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.util.Properties
import java.util.zip.ZipInputStream

fun ensureAndroidSdk(rootDir: File) {
    val localProperties = File(rootDir, "local.properties")
    val props = Properties()
    if (localProperties.exists()) {
        localProperties.inputStream().use { props.load(it) }
    }

    var sdkPath = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: props.getProperty("sdk.dir")

    val potentialSdkDir = File(rootDir, ".gradle/android-sdk")
    if (sdkPath == null && potentialSdkDir.exists()) {
        sdkPath = potentialSdkDir.absolutePath
        props["sdk.dir"] = sdkPath
        localProperties.outputStream().use { props.store(it, null) }
    }

    if (sdkPath != null && File(sdkPath).exists()) {
        println("Android SDK found at $sdkPath, skipping provisioning.")
        return
    }

    println("Android SDK not found. Provisioning a local version...")

    val sdkDir = File(rootDir, ".gradle/android-sdk").apply { mkdirs() }
    val os = System.getProperty("os.name").lowercase()
    val toolUrl = when {
        os.contains("win") -> "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
        os.contains("mac") -> "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
        else -> "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    }

    val cmdlineToolsZip = File(sdkDir, "cmdline-tools.zip")
    if (!cmdlineToolsZip.exists()) {
        println("Downloading Android command-line tools...")
        URI(toolUrl).toURL().openStream().use { Files.copy(it, cmdlineToolsZip.toPath()) }
    }

    val cmdlineToolsDir = File(sdkDir, "cmdline-tools/latest")
    if (!cmdlineToolsDir.exists()) {
        println("Unzipping command-line tools...")
        cmdlineToolsDir.mkdirs()
        ZipInputStream(cmdlineToolsZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(cmdlineToolsDir, entry.name.substringAfter('/'))
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile.mkdirs()
                    FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    val sdkManager = File(cmdlineToolsDir, "bin/sdkmanager" + if (os.contains("win")) ".bat" else "")
    sdkManager.setExecutable(true)

    fun runSdkManager(args: String) {
        println("Running sdkmanager $args")
        val process = ProcessBuilder(sdkManager.absolutePath, *args.split(" ").toTypedArray())
            .directory(sdkDir)
            .redirectErrorStream(true)
            .start()

        // Pipe "y" to accept licenses
        process.outputStream.bufferedWriter().apply {
            write("y\n".repeat(20))
            flush()
            close()
        }

        process.inputStream.bufferedReader().forEachLine { println(it) }
        process.waitFor()
    }

    runSdkManager("--licenses")
    // Align SDK/NDK to module config (compileSdk 36, build-tools 36.0.0, NDK 28.2)
    val desiredNdkVersion = "28.2.13676358"
    runSdkManager("platform-tools platforms;android-36 build-tools;36.0.0 ndk;$desiredNdkVersion")

    val ndkDir = File(sdkDir, "ndk/$desiredNdkVersion")
    props["sdk.dir"] = sdkDir.absolutePath
    localProperties.outputStream().use { props.store(it, null) }

    System.setProperty("android.home", sdkDir.absolutePath)
    System.setProperty("android.sdk.root", sdkDir.absolutePath)

    println("Android SDK and NDK provisioned successfully.")
}

ensureAndroidSdk(rootDir)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://jitpack.io") }
    }
}



rootProject.name = "PurrfectSnap Lite"
include(":common")
include(":core")
include(":valdi")
include(":app")
include(":mapper")
include(":manager")
include(":native")

project(":manager").projectDir = file("mapper")

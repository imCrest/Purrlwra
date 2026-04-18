package cock.crest.purrfectsnap.lite.mapper.tests

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.mapper.ClassMapper
import org.junit.Test
import java.io.File


class TestMappings {
    @Test
    fun testMappings() {
        val classMapper = ClassMapper()

        val gson = GsonBuilder().setPrettyPrinting().create()
        val apkPath = System.getenv("SNAPCHAT_APK")
        if (apkPath == null) {
            println("Skipping test: SNAPCHAT_APK environment variable is not set.")
            return
        }
        val apkFile = File(apkPath)
        classMapper.loadApk(apkFile.absolutePath)
        runBlocking {
            val result = classMapper.run()
            println("Mappings: ${gson.toJson(result)}")
        }
    }
}

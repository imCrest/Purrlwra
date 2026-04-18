package cock.crest.purrfectsnap.lite.setup.patch

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import okhttp3.OkHttpClient
import okhttp3.Request

class AutoPatchServer(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.MINUTES)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("User-Agent", "PurrfectSnap")
                    .build()
            )
        }
        .build()
) {
    data class LatestApk(
        val tagName: String,
        val apkName: String,
        val downloadUrl: String,
    )

    fun fetchLatestSnapchatApk(): LatestApk? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/particle-box/download-snap/releases/latest")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = response.body?.string() ?: return null
            val release = JsonParser.parseString(json).asJsonObject
            val tagName = release.getAsJsonPrimitive("tag_name")?.asString ?: "latest"

            val assets = release.getAsJsonArray("assets") ?: return null
            val apkAssets = assets.mapNotNull { element ->
                val asset = element.asJsonObject
                val name = asset.getAsJsonPrimitive("name")?.asString ?: return@mapNotNull null
                val downloadUrl = asset.getAsJsonPrimitive("browser_download_url")?.asString ?: return@mapNotNull null
                if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                name to downloadUrl
            }

            val nonPrimary = apkAssets.filterNot { it.first.contains("snapchat", ignoreCase = true) }
            val selectionPool = if (nonPrimary.isNotEmpty()) nonPrimary else apkAssets
            val selected = selectionPool.random(Random.Default)

            return LatestApk(
                tagName = tagName,
                apkName = selected.first,
                downloadUrl = selected.second,
            )
        }
    }
}

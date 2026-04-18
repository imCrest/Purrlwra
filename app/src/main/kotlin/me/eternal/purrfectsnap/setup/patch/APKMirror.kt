package cock.crest.purrfectsnap.lite.setup.patch

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

data class DownloadItem(
    val title: String,
    val releaseDate: String,
    val downloadPage: String
) {
    val shortTitle: String = title.substringBefore("(").trim()
    val hash: String = (title + releaseDate + downloadPage).hashCode().absoluteValue.toString(16)
    val isBeta: Boolean = title.contains("Beta", ignoreCase = true)
}

class APKMirror {
    val okhttpClient = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.HOURS)
        .connectTimeout(1, TimeUnit.HOURS)
        .readTimeout(1, TimeUnit.HOURS)
        .writeTimeout(1, TimeUnit.HOURS)
        .addInterceptor {
            it.proceed(
                it.request().newBuilder()
                    .addHeader("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0")
                    .build()
            )
        }
        .build()

    companion object {
        private const val BASE_URL = "https://www.apkmirror.com"
        private const val FETCH_BUILD_URL =
            "$BASE_URL/apk/snap-inc/snapchat/variant-%7B%22arches_slug%22%3A%5B%22arm64-v8a%22%2C%22armeabi-v7a%22%5D%2C%22dpis_slug%22%3A%5B%22nodpi%22%5D%7D/page/{page}/"
    }

    fun fetchDownloadLink(downloadPageUri: String): String? {
        try {
            okhttpClient.newCall(
                Request.Builder()
                    .url("$BASE_URL$downloadPageUri")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val finalDownloadPageUri =
                    Jsoup.parse(bodyString).getElementsByClass("downloadButton").first()?.attr("href")
                        ?: return null

                okhttpClient.newCall(
                    Request.Builder()
                        .url("$BASE_URL$finalDownloadPageUri")
                        .build()
                ).execute().use { response2 ->
                    if (!response2.isSuccessful) return null
                    val bodyString2 = response2.body?.string() ?: return null
                    val document = Jsoup.parse(bodyString2)
                    val downloadLink = document.getElementById("download-link")?.attr("href") ?: return null
                    return BASE_URL + downloadLink
                }
            }
        } catch (e: UnknownHostException) {
            throw DNSBlockedException(e)
        }
    }

    fun fetchSnapchatVersions(page: Int = 1): List<DownloadItem>? {
        try {
            val versions = mutableListOf<DownloadItem>()
            okhttpClient.newCall(
                Request.Builder()
                    .url(FETCH_BUILD_URL.replace("{page}", page.toString()))
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val document = Jsoup.parse(bodyString)
                document.getElementById("primary")?.getElementsByClass("appRow")?.forEach { app ->
                    val title = app.getElementsByTag("h5").first()?.attr("title") ?: return@forEach
                    val releaseDate = app.getElementsByClass("dateyear_utc").attr("data-utcdate") ?: return@forEach
                    val downloadPage = app.getElementsByClass("downloadLink").first()?.attr("href") ?: return@forEach

                    versions.add(DownloadItem(title, releaseDate, downloadPage))
                }
            }
            return versions
        } catch (e: UnknownHostException) {
            throw DNSBlockedException(e)
        }
    }
}

class DNSBlockedException(e: Throwable) : Exception(e)

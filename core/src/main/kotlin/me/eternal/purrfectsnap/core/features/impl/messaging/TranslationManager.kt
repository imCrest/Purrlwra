package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import cock.crest.purrfectsnap.lite.core.ModContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TranslationManager(private val context: ModContext) {
    private val translationCache = ConcurrentHashMap<String, CachedTranslation>()
    private val executor = Executors.newFixedThreadPool(3)
    private var isBlocked = false
    private var lastRequestTime = 0L
    
    data class CachedTranslation(
        val translation: String,
        val timestamp: Long,
        val sourceLang: String,
        val targetLang: String
    )
    
    companion object {
        private const val CACHE_DURATION = 24 * 60 * 60 * 1000L
        private const val MAX_CACHE_SIZE = 1000
        private const val MIN_REQUEST_INTERVAL = 1000L
        private const val MAX_RETRIES = 3
    }
    
    suspend fun translateText(
        text: String,
        sourceLang: String,
        targetLang: String,
        forceRefresh: Boolean = false
    ): String? {
        if (text.isBlank()) return null
        
        val cacheKey = "${text}_${sourceLang}_${targetLang}"
        
        if (!forceRefresh) {
            translationCache[cacheKey]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION) {
                    return cached.translation
                }
            }
        }
        
        if (isBlocked) {
            context.log.warn("Translation service is blocked, returning cached result if available")
            return translationCache[cacheKey]?.translation
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRequestTime < MIN_REQUEST_INTERVAL) {
            delay(MIN_REQUEST_INTERVAL - (currentTime - lastRequestTime))
        }
        
        return try {
            val translation = performTranslationWithRetry(text, sourceLang, targetLang)
            if (translation != null) {
                if (translationCache.size >= MAX_CACHE_SIZE) {
                    cleanupCache()
                }
                translationCache[cacheKey] = CachedTranslation(
                    translation = translation,
                    timestamp = System.currentTimeMillis(),
                    sourceLang = sourceLang,
                    targetLang = targetLang
                )
                lastRequestTime = System.currentTimeMillis()
            }
            translation
        } catch (e: Exception) {
            context.log.error("Translation failed for text: $text", e)
            null
        }
    }
    
    private suspend fun performTranslationWithRetry(
        text: String, 
        sourceLang: String, 
        targetLang: String
    ): String? {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRIES) {
            try {
                val translation = performTranslation(text, sourceLang, targetLang)
                if (translation != null) {
                    return translation
                }
            } catch (e: Exception) {
                lastException = e
                context.log.warn("Translation attempt $attempt failed: ${e.message}")
                
                if (e.message?.contains("sorry/index") == true || e.message?.contains("429") == true) {
                    isBlocked = true
                    context.log.error("Translation service is blocked by Google")
                    break
                }
                
                if (attempt < MAX_RETRIES) {
                    delay(attempt * 1000L)
                }
            }
        }
        
        if (lastException != null) {
            throw lastException
        }
        return null
    }
    
    private suspend fun performTranslation(text: String, sourceLang: String, targetLang: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
                connection.setRequestProperty("Connection", "keep-alive")
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == 429 || responseCode == 403) {
                    isBlocked = true
                    throw Exception("Translation service blocked (HTTP $responseCode)")
                }
                
                if (responseCode != 200) {
                    throw Exception("HTTP $responseCode: ${connection.responseMessage}")
                }
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseGoogleTranslateResponse(response)
            } catch (e: Exception) {
                context.log.error("Translation API call failed", e)
                null
            }
        }
    }
    
    private fun parseGoogleTranslateResponse(response: String): String? {
        return try {
            val startIndex = response.indexOf("[[[\"")
            if (startIndex == -1) return null
            
            val endIndex = response.indexOf("\",\"", startIndex)
            if (endIndex == -1) return null
            
            response.substring(startIndex + 4, endIndex)
        } catch (e: Exception) {
            context.log.error("Failed to parse translation response", e)
            null
        }
    }
    
    private fun cleanupCache() {
        val currentTime = System.currentTimeMillis()
        translationCache.entries.removeIf { (_, cached) ->
            currentTime - cached.timestamp > CACHE_DURATION
        }
    }
    
    fun clearCache() {
        translationCache.clear()
    }
    
    fun resetBlockedStatus() {
        isBlocked = false
    }
    
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "size" to translationCache.size,
            "maxSize" to MAX_CACHE_SIZE,
            "cacheDuration" to CACHE_DURATION,
            "isBlocked" to isBlocked
        )
    }
    
    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
} 
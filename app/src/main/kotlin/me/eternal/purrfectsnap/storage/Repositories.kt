package cock.crest.purrfectsnap.lite.storage

import android.content.ContentValues
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import cock.crest.purrfectsnap.lite.common.util.ktx.getStringOrNull


fun AppDatabase.getRepositories(type: String): List<String> {
    return runBlocking(executor.asCoroutineDispatcher()) {
        database.rawQuery("SELECT url FROM repositories WHERE type = ?", arrayOf(type)).use { cursor ->
            val repos = mutableListOf<String>()
            while (cursor.moveToNext()) {
                repos.add(cursor.getStringOrNull("url") ?: continue)
            }
            repos
        }
    }
}

fun AppDatabase.removeRepo(type: String, url: String) {
    runBlocking(executor.asCoroutineDispatcher()) {
        database.delete("repositories", "url = ? AND type = ?", arrayOf(url, type))
    }
}

fun AppDatabase.addRepo(type: String, url: String) {
    runBlocking(executor.asCoroutineDispatcher()) {
        database.insert("repositories", null, ContentValues().apply {
            put("url", url)
            put("type", type)
        })
    }
}

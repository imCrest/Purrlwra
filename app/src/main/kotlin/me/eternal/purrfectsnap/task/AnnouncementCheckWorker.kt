package cock.crest.purrfectsnap.lite.task

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cock.crest.purrfectsnap.lite.R
import cock.crest.purrfectsnap.lite.ui.manager.MainActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

class AnnouncementCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val url = inputData.getString("announcements_url") ?: return Result.failure()
            val body = fetchBody(url)?.trim().orEmpty()
            if (body.isEmpty()) return Result.success()

            val hash = sha256(body)
            val prefs = appContext.getSharedPreferences("prefs", 0)
            val lastHash = prefs.getString("announcements_last_hash", null)
            if (lastHash == null) {
                prefs.edit().putString("announcements_last_hash", hash).apply()
                return Result.success()
            }

            if (lastHash != hash) {
                prefs.edit().putString("announcements_last_hash", hash).apply()
                showAnnouncementNotification()
            }
            Result.success()
        }.getOrElse {
            Result.failure()
        }
    }

    private fun fetchBody(url: String): String? {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun showAnnouncementNotification() {
        val channelId = "purrfectsnap_announcements"
        val name = inputData.getString("channel_name") ?: "Announcements"
        val descriptionText = inputData.getString("channel_description") ?: "Notifications for PurrfectSnap announcements"
        val title = inputData.getString("notification_title") ?: "New announcement available"
        val text = inputData.getString("notification_text") ?: "Tap to open and read."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("route", "home")
            putExtra("show_announcements", true)
        }
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(appContext.resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        with(NotificationManagerCompat.from(appContext)) {
            notify(2, builder.build())
        }
    }
}

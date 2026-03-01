package com.mailautomation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GmailMonitorService : Service() {

    companion object {
        const val TAG = "GmailMonitorService"
        const val CHANNEL_ID = "gmail_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 Minuten

        @Volatile
        var isRunning = false
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Initialisierung..."))
        isRunning = true

        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndSendDrafts()
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Prüfen der Entwürfe", e)
                    updateNotification("Fehler: ${e.message ?: "Unbekannter Fehler"}")
                }
                delay(CHECK_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        monitoringJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service gestoppt")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkAndSendDrafts() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString(MainActivity.KEY_EMAIL, "") ?: ""
        val password = prefs.getString(MainActivity.KEY_PASSWORD, "") ?: ""
        val imapHost = prefs.getString(MainActivity.KEY_IMAP_HOST, "imap.gmail.com") ?: "imap.gmail.com"
        val imapPort = prefs.getString(MainActivity.KEY_IMAP_PORT, "993")?.toIntOrNull() ?: 993
        val smtpHost = prefs.getString(MainActivity.KEY_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        val smtpPort = prefs.getString(MainActivity.KEY_SMTP_PORT, "587")?.toIntOrNull() ?: 587

        if (email.isEmpty() || password.isEmpty()) {
            Log.w(TAG, "Keine E-Mail-Einstellungen gefunden")
            updateNotification("Keine Einstellungen – bitte App öffnen")
            return
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
        updateNotification("Prüfe Entwürfe... ($timeStr)")
        saveLastCheckTime(timeStr)

        val emailHelper = EmailHelper(imapHost, imapPort, smtpHost, smtpPort, email, password)
        val result = emailHelper.checkAndSendDrafts()

        val status = when {
            result.sent > 0 && result.errors == 0 ->
                "${result.sent} Entwurf/Entwürfe gesendet – nächste Prüfung in 5 Min."
            result.sent > 0 ->
                "${result.sent} gesendet, ${result.errors} Fehler – nächste Prüfung in 5 Min."
            result.errors > 0 ->
                "Senden fehlgeschlagen (${result.errors} Fehler) – nächste Prüfung in 5 Min."
            else ->
                "Keine Entwürfe gefunden – nächste Prüfung in 5 Min."
        }
        updateNotification(status)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "E-Mail Entwurf Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Überwacht E-Mail-Entwürfe und sendet diese automatisch"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("E-Mail Entwurf Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun saveLastCheckTime(timeStr: String) {
        getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_check", timeStr)
            .apply()
    }
}

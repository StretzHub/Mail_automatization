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
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
        val accountName = intent?.getStringExtra("account_name") ?: ""

        startForeground(NOTIFICATION_ID, buildNotification("Initialisierung..."))
        isRunning = true

        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkAndSendDrafts(accountName)
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

    private suspend fun checkAndSendDrafts(accountName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this@GmailMonitorService)
        if (account == null) {
            Log.w(TAG, "Kein angemeldetes Google-Konto gefunden")
            updateNotification("Kein Konto – bitte App öffnen und anmelden")
            return
        }

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
        updateNotification("Prüfe Entwürfe... ($timeStr)")

        saveLastCheckTime(timeStr)

        val gmailHelper = GmailHelper(this@GmailMonitorService, account)
        val drafts = gmailHelper.listDrafts()

        if (drafts.isEmpty()) {
            Log.d(TAG, "Keine Entwürfe gefunden")
            updateNotification("Keine Entwürfe gefunden – nächste Prüfung in 5 Min.")
            return
        }

        Log.d(TAG, "Gefundene Entwürfe: ${drafts.size}")
        var sentCount = 0
        var errorCount = 0

        for (draft in drafts) {
            try {
                gmailHelper.sendDraft(draft.id)
                sentCount++
                Log.d(TAG, "Entwurf gesendet: ${draft.id}")
            } catch (e: Exception) {
                errorCount++
                Log.e(TAG, "Fehler beim Senden von Entwurf ${draft.id}", e)
            }
        }

        val status = when {
            sentCount > 0 && errorCount == 0 -> "$sentCount Entwurf/Entwürfe gesendet – nächste Prüfung in 5 Min."
            sentCount > 0 -> "$sentCount gesendet, $errorCount Fehler – nächste Prüfung in 5 Min."
            else -> "Senden fehlgeschlagen ($errorCount Fehler) – nächste Prüfung in 5 Min."
        }
        updateNotification(status)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gmail Entwurf Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Überwacht Gmail-Entwürfe und sendet diese automatisch"
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
            .setContentTitle("Gmail Entwurf Monitor")
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

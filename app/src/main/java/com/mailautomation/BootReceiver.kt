package com.mailautomation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Gerät gestartet / App aktualisiert – prüfe ob Monitoring gestartet werden soll")

            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val email = prefs.getString(MainActivity.KEY_EMAIL, "") ?: ""
            val password = prefs.getString(MainActivity.KEY_PASSWORD, "") ?: ""

            if (email.isNotEmpty() && password.isNotEmpty()) {
                Log.d(TAG, "Starte E-Mail Monitor Service für: $email")
                val serviceIntent = Intent(context, GmailMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Keine gespeicherten Einstellungen – Service wird nicht gestartet")
            }
        }
    }
}

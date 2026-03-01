package com.mailautomation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Gerät gestartet / App aktualisiert – prüfe ob Monitoring gestartet werden soll")

            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                Log.d(TAG, "Starte GmailMonitorService für: ${account.email}")
                val serviceIntent = Intent(context, GmailMonitorService::class.java).apply {
                    putExtra("account_name", account.email)
                }
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Kein angemeldetes Konto – Service wird nicht gestartet")
            }
        }
    }
}

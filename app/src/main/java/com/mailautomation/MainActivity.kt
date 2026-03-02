package com.mailautomation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var imapHostEditText: EditText
    private lateinit var imapPortEditText: EditText
    private lateinit var smtpHostEditText: EditText
    private lateinit var smtpPortEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var lastCheckTextView: TextView
    private lateinit var startStopButton: Button
    private lateinit var intervalSpinner: Spinner
    private lateinit var logTextView: TextView
    private lateinit var clearLogButton: Button
    private lateinit var mainScrollView: ScrollView

    companion object {
        const val TAG = "MainActivity"
        const val PREFS_NAME = "email_settings"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_IMAP_HOST = "imap_host"
        const val KEY_IMAP_PORT = "imap_port"
        const val KEY_SMTP_HOST = "smtp_host"
        const val KEY_SMTP_PORT = "smtp_port"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val NOTIFICATION_EMAIL = "dominikstretz@googlemail.com"

        val INTERVAL_OPTIONS = intArrayOf(1, 5, 10, 15, 30, 60, 90, 120)
        val INTERVAL_LABELS = arrayOf(
            "1 Minute", "5 Minuten", "10 Minuten", "15 Minuten",
            "30 Minuten", "60 Minuten", "90 Minuten", "120 Minuten"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailEditText      = findViewById(R.id.emailEditText)
        passwordEditText   = findViewById(R.id.passwordEditText)
        imapHostEditText   = findViewById(R.id.imapHostEditText)
        imapPortEditText   = findViewById(R.id.imapPortEditText)
        smtpHostEditText   = findViewById(R.id.smtpHostEditText)
        smtpPortEditText   = findViewById(R.id.smtpPortEditText)
        statusTextView     = findViewById(R.id.statusTextView)
        lastCheckTextView  = findViewById(R.id.lastCheckTextView)
        startStopButton    = findViewById(R.id.startStopButton)
        intervalSpinner    = findViewById(R.id.intervalSpinner)
        logTextView        = findViewById(R.id.logTextView)
        clearLogButton     = findViewById(R.id.clearLogButton)
        mainScrollView     = findViewById(R.id.mainScrollView)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, INTERVAL_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        intervalSpinner.adapter = adapter

        loadSettings()

        startStopButton.setOnClickListener {
            if (GmailMonitorService.isRunning) stopMonitoring() else startMonitoring()
        }

        clearLogButton.setOnClickListener {
            AppLogger.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        // Register live-log listener: called from background threads, must dispatch to UI
        AppLogger.listener = {
            runOnUiThread { refreshLog() }
        }
        refreshLog()
        updateStatus()
        val lastCheck = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("last_check", "Noch nie geprüft")
        lastCheckTextView.text = "Letzte Prüfung: $lastCheck"
    }

    override fun onPause() {
        super.onPause()
        AppLogger.listener = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val outRect = Rect()
                focused.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshLog() {
        val text = AppLogger.getLog()
        logTextView.text = text
        // Scroll to bottom so latest entry is visible
        mainScrollView.post { mainScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        emailEditText.setText(prefs.getString(KEY_EMAIL, ""))
        passwordEditText.setText(prefs.getString(KEY_PASSWORD, ""))
        imapHostEditText.setText(prefs.getString(KEY_IMAP_HOST, "imap.gmail.com"))
        imapPortEditText.setText(prefs.getString(KEY_IMAP_PORT, "993"))
        smtpHostEditText.setText(prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com"))
        smtpPortEditText.setText(prefs.getString(KEY_SMTP_PORT, "587"))

        val savedInterval = prefs.getInt(KEY_INTERVAL_MINUTES, 5)
        val idx = INTERVAL_OPTIONS.indexOfFirst { it == savedInterval }.coerceAtLeast(0)
        intervalSpinner.setSelection(idx)
    }

    private fun saveSettings(): Boolean {
        val email    = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Bitte E-Mail-Adresse und Passwort eingeben", Toast.LENGTH_LONG).show()
            return false
        }

        val selectedInterval = INTERVAL_OPTIONS[intervalSpinner.selectedItemPosition]

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(KEY_EMAIL,     email)
            putString(KEY_PASSWORD,  password)
            putString(KEY_IMAP_HOST, imapHostEditText.text.toString().trim().ifEmpty { "imap.gmail.com" })
            putString(KEY_IMAP_PORT, imapPortEditText.text.toString().trim().ifEmpty { "993" })
            putString(KEY_SMTP_HOST, smtpHostEditText.text.toString().trim().ifEmpty { "smtp.gmail.com" })
            putString(KEY_SMTP_PORT, smtpPortEditText.text.toString().trim().ifEmpty { "587" })
            putInt(KEY_INTERVAL_MINUTES, selectedInterval)
            apply()
        }
        return true
    }

    private fun startMonitoring() {
        if (!saveSettings()) return
        val intent = Intent(this, GmailMonitorService::class.java)
        startForegroundService(intent)
        updateStatus()
        Toast.makeText(this, "Überwachung gestartet", Toast.LENGTH_SHORT).show()
        sendMonitoringNotification(started = true)
    }

    private fun stopMonitoring() {
        val intent = Intent(this, GmailMonitorService::class.java)
        stopService(intent)
        updateStatus()
        Toast.makeText(this, "Überwachung gestoppt", Toast.LENGTH_SHORT).show()
        sendMonitoringNotification(started = false)
    }

    private fun updateStatus() {
        val intervalMin = INTERVAL_OPTIONS[intervalSpinner.selectedItemPosition]
        if (GmailMonitorService.isRunning) {
            statusTextView.text = "Status: Aktiv – prüft alle $intervalMin Minuten"
            startStopButton.text = "Überwachung stoppen"
            startStopButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            startStopButton.setTextColor(Color.WHITE)
        } else {
            statusTextView.text = "Status: Inaktiv"
            startStopButton.text = "Speichern & Überwachung starten"
            startStopButton.setBackgroundColor(Color.parseColor("#F44336"))
            startStopButton.setTextColor(Color.WHITE)
        }
    }

    private fun sendMonitoringNotification(started: Boolean) {
        val prefs    = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val email    = prefs.getString(KEY_EMAIL,    "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        val imapHost = prefs.getString(KEY_IMAP_HOST, "imap.gmail.com") ?: "imap.gmail.com"
        val imapPort = prefs.getString(KEY_IMAP_PORT, "993")?.toIntOrNull() ?: 993
        val smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        val smtpPort = prefs.getString(KEY_SMTP_PORT, "587")?.toIntOrNull() ?: 587

        if (email.isEmpty() || password.isEmpty()) return

        val subject: String
        val body: String
        if (started) {
            val intervalMin = INTERVAL_OPTIONS[intervalSpinner.selectedItemPosition]
            subject = "E-Mail Monitor gestartet"
            body    = "Die Entwurfsüberwachung wurde eingeschaltet.\nKonto: $email\nPrüfintervall: $intervalMin Minuten"
        } else {
            subject = "E-Mail Monitor gestoppt"
            body    = "Die Entwurfsüberwachung wurde ausgeschaltet.\nKonto: $email"
        }

        Thread {
            try {
                val helper = EmailHelper(imapHost, imapPort, smtpHost, smtpPort, email, password)
                helper.sendSimpleEmail(NOTIFICATION_EMAIL, subject, body)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Senden der Benachrichtigungs-Mail", e)
            }
        }.start()
    }
}

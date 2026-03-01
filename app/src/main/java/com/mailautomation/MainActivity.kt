package com.mailautomation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

    companion object {
        const val PREFS_NAME = "email_settings"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_IMAP_HOST = "imap_host"
        const val KEY_IMAP_PORT = "imap_port"
        const val KEY_SMTP_HOST = "smtp_host"
        const val KEY_SMTP_PORT = "smtp_port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        imapHostEditText = findViewById(R.id.imapHostEditText)
        imapPortEditText = findViewById(R.id.imapPortEditText)
        smtpHostEditText = findViewById(R.id.smtpHostEditText)
        smtpPortEditText = findViewById(R.id.smtpPortEditText)
        statusTextView = findViewById(R.id.statusTextView)
        lastCheckTextView = findViewById(R.id.lastCheckTextView)
        startStopButton = findViewById(R.id.startStopButton)

        loadSettings()

        startStopButton.setOnClickListener {
            if (GmailMonitorService.isRunning) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        val lastCheck = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("last_check", "Noch nie geprüft")
        lastCheckTextView.text = "Letzte Prüfung: $lastCheck"
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        emailEditText.setText(prefs.getString(KEY_EMAIL, ""))
        passwordEditText.setText(prefs.getString(KEY_PASSWORD, ""))
        imapHostEditText.setText(prefs.getString(KEY_IMAP_HOST, "imap.gmail.com"))
        imapPortEditText.setText(prefs.getString(KEY_IMAP_PORT, "993"))
        smtpHostEditText.setText(prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com"))
        smtpPortEditText.setText(prefs.getString(KEY_SMTP_PORT, "587"))
    }

    private fun saveSettings(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Bitte E-Mail-Adresse und Passwort eingeben", Toast.LENGTH_LONG).show()
            return false
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putString(KEY_IMAP_HOST, imapHostEditText.text.toString().trim().ifEmpty { "imap.gmail.com" })
            putString(KEY_IMAP_PORT, imapPortEditText.text.toString().trim().ifEmpty { "993" })
            putString(KEY_SMTP_HOST, smtpHostEditText.text.toString().trim().ifEmpty { "smtp.gmail.com" })
            putString(KEY_SMTP_PORT, smtpPortEditText.text.toString().trim().ifEmpty { "587" })
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
    }

    private fun stopMonitoring() {
        val intent = Intent(this, GmailMonitorService::class.java)
        stopService(intent)
        updateStatus()
        Toast.makeText(this, "Überwachung gestoppt", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        if (GmailMonitorService.isRunning) {
            statusTextView.text = "Status: Aktiv – prüft alle 5 Minuten"
            startStopButton.text = "Überwachung stoppen"
        } else {
            statusTextView.text = "Status: Inaktiv"
            startStopButton.text = "Speichern & Überwachung starten"
        }
    }
}

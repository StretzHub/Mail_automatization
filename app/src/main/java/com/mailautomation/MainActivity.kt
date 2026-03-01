package com.mailautomation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

class MainActivity : Activity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var statusTextView: TextView
    private lateinit var accountTextView: TextView
    private lateinit var signInButton: Button
    private lateinit var startStopButton: Button
    private lateinit var lastCheckTextView: TextView

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        accountTextView = findViewById(R.id.accountTextView)
        signInButton = findViewById(R.id.signInButton)
        startStopButton = findViewById(R.id.startStopButton)
        lastCheckTextView = findViewById(R.id.lastCheckTextView)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(GmailScopes.GMAIL_COMPOSE),
                Scope(GmailScopes.GMAIL_READONLY)
            )
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        startStopButton.setOnClickListener {
            toggleMonitoring()
        }
    }

    override fun onStart() {
        super.onStart()
        val account = GoogleSignIn.getLastSignedInAccount(this)
        updateUI(account)
    }

    override fun onResume() {
        super.onResume()
        updateUI(GoogleSignIn.getLastSignedInAccount(this))
        val lastCheck = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("last_check", "Noch nie geprüft")
        lastCheckTextView.text = "Letzte Prüfung: $lastCheck"
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signOut() {
        stopMonitoring()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
            Toast.makeText(this, "Abgemeldet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                updateUI(account)
                Toast.makeText(this, "Anmeldung erfolgreich: ${account.email}", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                statusTextView.text = "Anmeldung fehlgeschlagen (Code: ${e.statusCode})"
                Toast.makeText(this, "Fehler: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleMonitoring() {
        if (GmailMonitorService.isRunning) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            signIn()
            return
        }
        val intent = Intent(this, GmailMonitorService::class.java).apply {
            putExtra("account_name", account.email)
        }
        startForegroundService(intent)
        updateUI(account)
        Toast.makeText(this, "Überwachung gestartet", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, GmailMonitorService::class.java)
        stopService(intent)
        updateUI(GoogleSignIn.getLastSignedInAccount(this))
        Toast.makeText(this, "Überwachung gestoppt", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            accountTextView.text = "Konto: ${account.email}"
            signInButton.text = "Google-Konto abmelden"
            signInButton.setOnClickListener { signOut() }
            startStopButton.isEnabled = true

            if (GmailMonitorService.isRunning) {
                startStopButton.text = "Überwachung stoppen"
                statusTextView.text = "Status: Aktiv – prüft alle 5 Minuten"
            } else {
                startStopButton.text = "Überwachung starten"
                statusTextView.text = "Status: Inaktiv"
            }
        } else {
            accountTextView.text = "Kein Google-Konto angemeldet"
            signInButton.text = "Mit Google anmelden"
            signInButton.setOnClickListener { signIn() }
            startStopButton.isEnabled = false
            startStopButton.text = "Überwachung starten"
            statusTextView.text = "Status: Bitte zuerst anmelden"
        }
    }
}

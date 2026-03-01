package com.mailautomation

import android.util.Log
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.MimeMessage

class EmailHelper(
    private val imapHost: String,
    private val imapPort: Int,
    private val smtpHost: String,
    private val smtpPort: Int,
    private val username: String,
    private val password: String
) {
    companion object {
        const val TAG = "EmailHelper"
        val DRAFT_FOLDER_NAMES = listOf(
            "[Gmail]/Drafts",
            "[Google Mail]/Drafts",
            "[Google Mail]/Entwürfe",
            "[Gmail]/Entwürfe",
            "Drafts",
            "INBOX.Drafts",
            "Draft"
        )
    }

    data class SendResult(val sent: Int, val errors: Int)

    fun checkAndSendDrafts(): SendResult {
        val imapProps = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", imapHost)
            put("mail.imaps.port", imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "20000")
        }

        val imapSession = Session.getInstance(imapProps)
        val store: Store = imapSession.getStore("imaps")
        store.connect(imapHost, imapPort, username, password)

        try {
            val draftsFolder = findDraftsFolder(store)
                ?: return SendResult(0, 0).also {
                    Log.w(TAG, "Kein Entwurfsordner gefunden")
                }

            draftsFolder.open(Folder.READ_WRITE)
            try {
                val messages = draftsFolder.messages
                if (messages.isEmpty()) {
                    Log.d(TAG, "Keine Entwürfe vorhanden")
                    return SendResult(0, 0)
                }

                var sent = 0
                var errors = 0

                for (message in messages) {
                    try {
                        sendViaSMTP(message)
                        message.setFlag(Flags.Flag.DELETED, true)
                        sent++
                        Log.d(TAG, "Entwurf gesendet: ${message.subject}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Senden: ${message.subject}", e)
                        errors++
                    }
                }

                draftsFolder.expunge()
                return SendResult(sent, errors)
            } finally {
                if (draftsFolder.isOpen) draftsFolder.close(true)
            }
        } finally {
            store.close()
        }
    }

    private fun findDraftsFolder(store: Store): Folder? {
        for (name in DRAFT_FOLDER_NAMES) {
            try {
                val folder = store.getFolder(name)
                if (folder.exists()) {
                    Log.d(TAG, "Entwurfsordner gefunden: $name")
                    return folder
                }
            } catch (e: Exception) {
                // try next name
            }
        }
        return null
    }

    private fun sendViaSMTP(message: Message) {
        val smtpProps = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "20000")
        }

        val smtpSession = Session.getInstance(smtpProps, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(username, password)
        })

        val newMessage = MimeMessage(smtpSession, (message as MimeMessage).inputStream)
        Transport.send(newMessage)
    }

    fun testConnection(): Boolean {
        return try {
            val imapProps = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", imapHost)
                put("mail.imaps.port", imapPort.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "10000")
                put("mail.imaps.timeout", "10000")
            }
            val session = Session.getInstance(imapProps)
            val store = session.getStore("imaps")
            store.connect(imapHost, imapPort, username, password)
            store.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Verbindungstest fehlgeschlagen", e)
            false
        }
    }
}

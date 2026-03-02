package com.mailautomation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.InternetAddress
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

    private fun buildSmtpProps(): Properties = Properties().apply {
        put("mail.smtp.host", smtpHost)
        put("mail.smtp.port", smtpPort.toString())
        put("mail.smtp.auth", "true")
        if (smtpPort == 465) {
            put("mail.smtp.ssl.enable", "true")
            AppLogger.d(TAG, "SMTP: SSL-Modus (Port 465)")
        } else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            AppLogger.d(TAG, "SMTP: STARTTLS-Modus (Port $smtpPort)")
        }
        put("mail.smtp.ssl.protocols", "TLSv1.2")
        put("mail.smtp.connectiontimeout", "20000")
        put("mail.smtp.timeout", "20000")
    }

    fun checkAndSendDrafts(): SendResult {
        AppLogger.d(TAG, "IMAP-Verbindung zu $imapHost:$imapPort …")
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
        try {
            store.connect(imapHost, imapPort, username, password)
            AppLogger.d(TAG, "IMAP-Login erfolgreich als $username")
        } catch (e: Exception) {
            AppLogger.e(TAG, "IMAP-Login fehlgeschlagen", e)
            throw e
        }

        try {
            val draftsFolder = findDraftsFolder(store)
            if (draftsFolder == null) {
                AppLogger.w(TAG, "Kein Entwurfsordner gefunden! Geprüfte Namen: ${DRAFT_FOLDER_NAMES.joinToString()}")
                return SendResult(0, 0)
            }

            draftsFolder.open(Folder.READ_WRITE)
            try {
                val messages = draftsFolder.messages
                AppLogger.d(TAG, "${messages.size} Nachricht(en) im Entwurfsordner")
                if (messages.isEmpty()) return SendResult(0, 0)

                var sent = 0
                var errors = 0

                for (message in messages) {
                    val subject = try { message.subject ?: "(kein Betreff)" } catch (e: Exception) { "(unbekannt)" }
                    val recipients = try {
                        val r = message.allRecipients
                        if (r.isNullOrEmpty()) "(keine)" else r.joinToString(", ") { it.toString() }
                    } catch (e: Exception) { "(unlesbar)" }
                    AppLogger.d(TAG, "Verarbeite Entwurf: Betreff='$subject', An='$recipients'")
                    try {
                        sendViaSMTP(message)
                        message.setFlag(Flags.Flag.DELETED, true)
                        sent++
                        AppLogger.d(TAG, "✓ Gesendet: '$subject'")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "✗ Fehler beim Senden: '$subject'", e)
                        errors++
                    }
                }

                draftsFolder.expunge()
                AppLogger.d(TAG, "Ergebnis: $sent gesendet, $errors Fehler")
                return SendResult(sent, errors)
            } finally {
                if (draftsFolder.isOpen) draftsFolder.close(true)
            }
        } finally {
            store.close()
        }
    }

    private fun findDraftsFolder(store: Store): Folder? {
        AppLogger.d(TAG, "Suche Entwurfsordner …")
        for (name in DRAFT_FOLDER_NAMES) {
            try {
                val folder = store.getFolder(name)
                if (folder.exists()) {
                    AppLogger.d(TAG, "Entwurfsordner gefunden: '$name'")
                    return folder
                } else {
                    AppLogger.d(TAG, "  '$name' – nicht vorhanden")
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "  '$name' – Fehler: ${e.message}")
            }
        }
        return null
    }

    private fun sendViaSMTP(message: Message) {
        AppLogger.d(TAG, "Verbinde mit SMTP $smtpHost:$smtpPort …")
        val smtpProps = buildSmtpProps()
        val smtpSession = Session.getInstance(smtpProps, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(username, password)
        })

        // Serialize the full RFC-2822 message (headers + body) via writeTo,
        // then re-parse it in the SMTP session to preserve all headers correctly.
        val baos = ByteArrayOutputStream()
        (message as MimeMessage).writeTo(baos)
        val newMessage = MimeMessage(smtpSession, ByteArrayInputStream(baos.toByteArray()))

        val recipients = newMessage.allRecipients
        if (recipients == null || recipients.isEmpty()) {
            throw MessagingException("Entwurf hat keine Empfänger (To/CC/BCC leer)")
        }
        AppLogger.d(TAG, "Sende an: ${recipients.joinToString(", ") { it.toString() }}")

        Transport.send(newMessage)
        AppLogger.d(TAG, "SMTP-Transport erfolgreich")
    }

    fun sendSimpleEmail(to: String, subject: String, body: String) {
        AppLogger.d(TAG, "Sende Benachrichtigungs-Mail an $to …")
        val smtpProps = buildSmtpProps()
        val session = Session.getInstance(smtpProps, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(username, password)
        })

        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(username))
        msg.addRecipient(Message.RecipientType.TO, InternetAddress(to))
        msg.subject = subject
        msg.setText(body, "UTF-8")
        Transport.send(msg)
        AppLogger.d(TAG, "Benachrichtigungs-Mail gesendet: $subject")
    }
}

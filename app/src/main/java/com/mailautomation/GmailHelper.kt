package com.mailautomation

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Draft

class GmailHelper(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    companion object {
        const val TAG = "GmailHelper"
        const val USER_ID = "me"
        const val APP_NAME = "Gmail Entwurf Monitor"
    }

    private val gmail: Gmail by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(GmailScopes.GMAIL_COMPOSE, GmailScopes.GMAIL_READONLY)
        ).apply {
            selectedAccount = account.account
        }

        Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    /**
     * Lists all drafts in the user's mailbox.
     */
    fun listDrafts(): List<Draft> {
        return try {
            val response = gmail.users().drafts().list(USER_ID).execute()
            response.drafts ?: emptyList()
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Authentifizierungsfehler beim Abrufen der Entwürfe", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Abrufen der Entwürfe", e)
            emptyList()
        }
    }

    /**
     * Sends a draft email by its ID.
     * The draft is fetched first (to get the full content), then sent.
     */
    fun sendDraft(draftId: String) {
        val draft = gmail.users().drafts().get(USER_ID, draftId).execute()
        gmail.users().drafts().send(USER_ID, draft).execute()
        Log.d(TAG, "Entwurf $draftId erfolgreich gesendet")
    }
}

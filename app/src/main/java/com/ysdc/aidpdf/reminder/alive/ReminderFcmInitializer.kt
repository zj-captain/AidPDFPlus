package com.ysdc.aidpdf.reminder.alive

import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.ysdc.aidpdf.BuildConfig
import com.ysdc.aidpdf.store.hasInitFcmTopic

object ReminderFcmInitializer {

    private const val TOPIC = "AidPDF"

    fun subscribeIfNeeded() {
        runCatching {
            Firebase.messaging.subscribeToTopic(TOPIC).addOnSuccessListener {
                hasInitFcmTopic = true
            }
        }
    }
}

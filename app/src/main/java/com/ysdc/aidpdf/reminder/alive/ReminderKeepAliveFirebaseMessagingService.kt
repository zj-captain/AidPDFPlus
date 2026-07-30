package com.ysdc.aidpdf.reminder.alive

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class ReminderKeepAliveFirebaseMessagingService : FirebaseMessagingService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.priority != RemoteMessage.PRIORITY_HIGH) {
            super.onMessageReceived(message)
            return
        }
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).launch {
            delay(300L)
            ReminderKeepAliveStarter.wake(applicationContext)
        }
    }
}

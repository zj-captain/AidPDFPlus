package com.ysdc.aidpdf.reminder.alive

import android.content.Context
import android.content.Intent
import androidx.core.app.BJobIntentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderKeepAliveService : BJobIntentService() {

    override fun onHandleWork(intent: Intent) {
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()).launch {
            runCatching { ReminderKeepAliveStarter.wake(applicationContext) }
        }
    }

    companion object {
        private const val JOB_ID = 24_501

        fun start(context: Context) {
            enqueueWork(
                context.applicationContext,
                ReminderKeepAliveService::class.java,
                JOB_ID,
                Intent(context, ReminderKeepAliveService::class.java)
            )
        }
    }
}

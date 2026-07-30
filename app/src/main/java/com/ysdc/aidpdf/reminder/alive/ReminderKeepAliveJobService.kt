package com.ysdc.aidpdf.reminder.alive

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

@SuppressLint("SpecifyJobSchedulerIdRange")
class ReminderKeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        ReminderKeepAliveStarter.wake(this, scheduleJob = false)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 24_500
        private const val PERIOD_MILLIS = 15 * 60_000L
        private const val EXTRA_SOURCE = "source"

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val pendingJob = scheduler.getPendingJob(JOB_ID)
                if (
                    pendingJob?.service?.className == ReminderKeepAliveJobService::class.java.name &&
                    pendingJob.extras.getString(EXTRA_SOURCE) == "keep_alive"
                ) return@runCatching
                scheduler.schedule(
                    JobInfo.Builder(
                        JOB_ID,
                        ComponentName(appContext, ReminderKeepAliveJobService::class.java)
                    )
                        .setPersisted(true)
                        .setPeriodic(PERIOD_MILLIS)
                        .setExtras(PersistableBundle().apply { putString(EXTRA_SOURCE, "keep_alive") })
                        .build()
                )
            }
        }
    }
}

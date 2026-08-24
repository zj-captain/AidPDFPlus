package com.ysdc.aidpdf.reminder.alive

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import com.ysdc.aidpdf.reminder.alarm.ReminderAlarmScheduler
import com.ysdc.aidpdf.reminder.front.ReminderBarManager
import com.ysdc.aidpdf.reminder.model.ReminderTrigger
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter.trigger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReminderKeepAliveReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {
//        Log.e("TAG", "onReceive: 66666")
        val safeContext = context ?: return
        val action = intent?.action ?: return
        runCatching {
            val state = PhoneStateManager.resolve(context)

            when (action) {
                Intent.ACTION_SCREEN_ON -> PhoneStateManager.onScreenOn(context, state)
                Intent.ACTION_SCREEN_OFF -> PhoneStateManager.onScreenOff(context, state)
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_UNLOCKED -> PhoneStateManager.onUserUnlocked(context, state)
            }
        }
        ReminderKeepAliveStarter.wake(safeContext)
    }
}

/**
 * 将锁屏/解锁相关判断和状态流转集中在这里处理。
 */
object PhoneStateManager {

    data class State(
        val isScreenOn: Boolean,
        val isKeyguardLocked: Boolean,
        val isUserUnlocked: Boolean,
    ) {
        val isReallyLocked: Boolean
            get() = isKeyguardLocked || !isUserUnlocked
    }

    fun resolve(context: Context): State {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }

        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        val isUserUnlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            userManager.isUserUnlocked
        } else {
            true
        }

        return State(
            isScreenOn = isScreenOn,
            isKeyguardLocked = isKeyguardLocked,
            isUserUnlocked = isUserUnlocked,
        )
    }

    fun onScreenOn(appContext: Context, state: State) {
        runCatching { ReminderBarManager.startIfAllowed(appContext) }
        runCatching { ReminderTriggerCenter.start(appContext) }
        runCatching { ReminderAlarmScheduler.scheduleNext(appContext) }
    }

    fun onScreenOff(appContext: Context, state: State) {
        runCatching { ReminderBarManager.startIfAllowed(appContext) }
        runCatching { ReminderTriggerCenter.start(appContext) }
        runCatching { ReminderAlarmScheduler.scheduleNext(appContext) }
    }

    fun onUserUnlocked(context: Context, state: State) {
        if (state.isReallyLocked) {
            return
        }

        LocalMainScope.launch {
            runCatching {
                delay(1500)
                trigger(ReminderTrigger.UNLOCK)
            }
        }
        /*runCatching {
            NotificationTool.startLaunchService(context.applicationContext)
        }*/
    }
}

val LocalMainScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> }) }


package com.ysdc.aidpdf.reminder.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.reminder.ReminderIntents
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.reminder.alive.ReminderKeepAliveStarter
import com.ysdc.aidpdf.reminder.model.ReminderMessage
import com.ysdc.aidpdf.reminder.model.ReminderSource

class ReminderOverlayWindow(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: View? = null

    fun show(message: ReminderMessage, firstDisplay: Boolean): Boolean {
        if (!appContext.canDrawOverlays()) return false
        removeCurrent()
        val parent = FrameLayout(appContext)
        val view = LayoutInflater.from(appContext).inflate(R.layout.layout_reminder_overlay, parent, false)
        bind(view, message, firstDisplay)
        return runCatching {
            windowManager.addView(view, layoutParams())
            currentView = view
            ReminderKeepAliveStarter.wake(appContext)
            ReminderEventTracker.reportChannelSent(message.trigger, ReminderSource.FLOATING)
            ReminderEventTracker.reportFloatingView()
            true
        }.getOrDefault(false)
    }

    fun hide() {
        removeCurrent()
    }

    fun isShowing(): Boolean = currentView != null

    @SuppressLint("ClickableViewAccessibility")
    private fun bind(view: View, message: ReminderMessage, firstDisplay: Boolean) {
        view.findViewById<ImageView>(R.id.reminder_image).setImageResource(message.content.imageRes)
        view.findViewById<TextView>(R.id.reminder_message).text = message.content.text
        val action = view.findViewById<TextView>(R.id.reminder_action).apply {
            text = message.content.button
        }
        val close = view.findViewById<View>(R.id.reminder_close)
        var interactionHandled = false

        fun finishInteraction(jump: Boolean) {
            if (interactionHandled) return
            interactionHandled = true
            if (jump) {
                runCatching {
                    appContext.startActivity(
                        ReminderIntents.openIntent(
                            appContext,
                            message.content.target,
                            message.trigger,
                            source = ReminderSource.FLOATING
                        )
                    )
                }
            }
            hide()
        }

        fun open() = finishInteraction(jump = true)

        fun closeOrJump() = finishInteraction(
            jump = ReminderOverlayPolicy.shouldJumpFromClose(firstDisplay)
        )

        val jumpOnTouch = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) open()
            true
        }
        val closeOnTouch = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) closeOrJump()
            true
        }
        view.setOnTouchListener(jumpOnTouch)
        action.setOnTouchListener(jumpOnTouch)
        close.setOnTouchListener(closeOnTouch)
        view.setOnClickListener { open() }
        action.setOnClickListener { open() }
        close.setOnClickListener { closeOrJump() }
    }

    private fun removeCurrent() {
        val view = currentView ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        currentView = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val density = appContext.resources.displayMetrics.density
        val horizontalMargin = (10 * density).toInt()
        return WindowManager.LayoutParams(
            appContext.resources.displayMetrics.widthPixels - horizontalMargin * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt()
        }
    }
}

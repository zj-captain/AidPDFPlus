package com.ysdc.aidpdf.ui.permission

import android.content.DialogInterface
import android.os.Bundle
import com.ysdc.aidpdf.databinding.DialogNotificationPermissionBinding
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.TrackingEventNames
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class NotificationPermissionDialogFragment : BaseDialogFragment<DialogNotificationPermissionBinding>(
    DialogNotificationPermissionBinding::inflate
) {

    var onOpenSettings: (() -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    override val dialogWidthRatio: Float = 0.88f
    override val canceledOnTouchOutside: Boolean = false

    override fun setupViews(savedInstanceState: Bundle?) {
        AidEventHub.track(TrackingEventNames.CUSTOM_NOTIFICATION_POPUP_VIEW)
    }

    override fun bindActions() {
        binding.allowButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.CUSTOM_NOTIFICATION_POPUP_PASS)
            onOpenSettings?.invoke()
            dismissAllowingStateLoss()
        }
        binding.notNowButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.CUSTOM_NOTIFICATION_POPUP_SKIP)
            dismissAllowingStateLoss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onClosed?.invoke()
    }
}

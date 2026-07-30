package com.ysdc.aidpdf.ui.permission

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.ysdc.aidpdf.databinding.DialogOverlayPermissionBinding
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.TrackingEventNames
import com.ysdc.aidpdf.ui.basic.BaseBottomDialogFragment

class OverlayPermissionDialogFragment : BaseBottomDialogFragment<DialogOverlayPermissionBinding>(
    DialogOverlayPermissionBinding::inflate
) {

    var onGrant: (() -> Unit)? = null
    var onClosed: (() -> Unit)? = null
    private var buttonAnimator: AnimatorSet? = null

    override val canceledOnTouchOutside: Boolean = false

    override fun bindActions() {
        binding.closeButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_SKIP)
            dismissAllowingStateLoss()
        }
        binding.allowButton.setOnClickListener {
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_PASS)
            onGrant?.invoke()
            dismissAllowingStateLoss()
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_POPUP_VIEW)
        val scaleX = ObjectAnimator.ofFloat(binding.allowButton, View.SCALE_X, 1f, 1.04f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(binding.allowButton, View.SCALE_Y, 1f, 1.04f).apply {
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        buttonAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 520L
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onClosed?.invoke()
    }

    override fun onDestroyView() {
        buttonAnimator?.cancel()
        buttonAnimator = null
        super.onDestroyView()
    }
}

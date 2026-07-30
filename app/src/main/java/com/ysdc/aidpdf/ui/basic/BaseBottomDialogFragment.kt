package com.ysdc.aidpdf.ui.basic

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.viewbinding.ViewBinding

abstract class BaseBottomDialogFragment<VB : ViewBinding>(
    bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : BaseDialogFragment<VB>(bindingFactory) {

    protected open val bottomDimAmount: Float = 0.45f

    override val dialogWidthRatio: Float = 1f

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setDimAmount(bottomDimAmount)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            decorView.setPadding(0, 0, 0, 0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
            }
        }
    }
}

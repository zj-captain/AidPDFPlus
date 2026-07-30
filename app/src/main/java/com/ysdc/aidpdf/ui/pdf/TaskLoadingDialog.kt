package com.ysdc.aidpdf.ui.pdf

import android.os.Bundle
import com.ysdc.aidpdf.databinding.DialogTaskLoadingBinding
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class TaskLoadingDialog : BaseDialogFragment<DialogTaskLoadingBinding>(DialogTaskLoadingBinding::inflate) {

    override val dialogWidthRatio: Float = 0.78f
    override val canceledOnTouchOutside: Boolean = false

    override fun setupViews(savedInstanceState: Bundle?) {
        isCancelable = false
        binding.loadingText.setText(requireArguments().getInt(ARG_MESSAGE_RES))
    }

    companion object {
        private const val ARG_MESSAGE_RES = "message_res"

        fun newInstance(messageRes: Int): TaskLoadingDialog {
            return TaskLoadingDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MESSAGE_RES, messageRes)
                }
            }
        }
    }
}

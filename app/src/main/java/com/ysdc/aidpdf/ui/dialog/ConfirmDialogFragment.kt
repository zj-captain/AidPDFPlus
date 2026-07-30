package com.ysdc.aidpdf.ui.dialog

import android.os.Bundle
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.DialogConfirmBinding
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class ConfirmDialogFragment : BaseDialogFragment<DialogConfirmBinding>(DialogConfirmBinding::inflate) {

    var onConfirm: (() -> Boolean)? = null

    override val dialogWidthRatio: Float = 0.8f

    override fun setupViews(savedInstanceState: Bundle?) {
        val args = requireArguments()
        binding.titleText.setText(args.getInt(ARG_TITLE_RES))
        binding.messageText.setText(args.getInt(ARG_MESSAGE_RES))
        binding.cancelButton.setText(args.getInt(ARG_CANCEL_LABEL_RES, R.string.cancel))
        binding.confirmButton.setText(args.getInt(ARG_CONFIRM_LABEL_RES, R.string.confirm))
    }

    override fun bindActions() {
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.confirmButton.setOnClickListener {
            if (onConfirm?.invoke() != false) {
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_MESSAGE_RES = "message_res"
        private const val ARG_CONFIRM_LABEL_RES = "confirm_label_res"
        private const val ARG_CANCEL_LABEL_RES = "cancel_label_res"

        fun newInstance(
            titleRes: Int,
            messageRes: Int,
            confirmLabelRes: Int = R.string.confirm,
            cancelLabelRes: Int = R.string.cancel
        ): ConfirmDialogFragment {
            return ConfirmDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleRes)
                    putInt(ARG_MESSAGE_RES, messageRes)
                    putInt(ARG_CONFIRM_LABEL_RES, confirmLabelRes)
                    putInt(ARG_CANCEL_LABEL_RES, cancelLabelRes)
                }
            }
        }
    }
}

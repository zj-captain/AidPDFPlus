package com.ysdc.aidpdf.ui.dialog

import android.os.Bundle
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.DialogMessageBinding
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class MessageDialogFragment : BaseDialogFragment<DialogMessageBinding>(DialogMessageBinding::inflate) {

    var onConfirm: (() -> Unit)? = null

    override val dialogWidthRatio: Float = 0.8f

    override fun setupViews(savedInstanceState: Bundle?) {
        val args = requireArguments()
        binding.titleText.setText(args.getInt(ARG_TITLE_RES))
        binding.messageText.setText(args.getInt(ARG_MESSAGE_RES))
        binding.confirmButton.setText(args.getInt(ARG_CONFIRM_LABEL_RES, R.string.confirm))
    }

    override fun bindActions() {
        binding.confirmButton.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_MESSAGE_RES = "message_res"
        private const val ARG_CONFIRM_LABEL_RES = "confirm_label_res"

        fun newInstance(
            titleRes: Int,
            messageRes: Int,
            confirmLabelRes: Int = R.string.confirm
        ): MessageDialogFragment {
            return MessageDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleRes)
                    putInt(ARG_MESSAGE_RES, messageRes)
                    putInt(ARG_CONFIRM_LABEL_RES, confirmLabelRes)
                }
            }
        }
    }
}

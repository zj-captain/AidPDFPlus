package com.ysdc.aidpdf.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.DialogTextInputBinding
import com.ysdc.aidpdf.ui.basic.BaseDialogFragment

class TextInputDialogFragment : BaseDialogFragment<DialogTextInputBinding>(DialogTextInputBinding::inflate) {

    var onCancelClick: (() -> Unit)? = null
    var onSuccessDismiss: ((String) -> Unit)? = null
    var onConfirm: ((String, (Boolean) -> Unit) -> Unit)? = null

    private var confirmedText: String? = null

    override val dialogWidthRatio: Float = 0.8f

    override fun setupViews(savedInstanceState: Bundle?) {
        val args = requireArguments()
        val message = args.getString(ARG_MESSAGE).orEmpty()
        binding.titleText.text = args.getString(ARG_TITLE).orEmpty()
        binding.messageText.text = message
        binding.messageText.isVisible = message.isNotBlank()
        binding.inputText.setText(args.getString(ARG_DEFAULT_VALUE).orEmpty())
        binding.inputText.hint = args.getString(ARG_HINT).orEmpty()
        binding.inputText.setSelectAllOnFocus(true)
        if (args.getBoolean(ARG_PASSWORD)) {
            binding.inputText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.cancelButton.setText(args.getInt(ARG_CANCEL_LABEL_RES, R.string.cancel))
        binding.confirmButton.setText(args.getInt(ARG_CONFIRM_LABEL_RES, R.string.confirm))
    }

    override fun bindActions() {
        binding.cancelButton.setOnClickListener {
            dismiss()
            onCancelClick?.invoke()
        }
        binding.confirmButton.setOnClickListener {
            val text = binding.inputText.text?.toString().orEmpty()
            setConfirmEnabled(false)
            onConfirm?.invoke(text) { accepted ->
                if (view == null) return@invoke
                if (accepted) {
                    confirmedText = text
                    dismissAllowingStateLoss()
                } else {
                    setConfirmEnabled(true)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.inputText.requestFocus()
        binding.inputText.post {
            context?.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(binding.inputText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        onCancelClick?.invoke()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        confirmedText?.let { onSuccessDismiss?.invoke(it) }
        confirmedText = null
        super.onDismiss(dialog)
    }

    private fun setConfirmEnabled(enabled: Boolean) {
        binding.confirmButton.isEnabled = enabled
        binding.confirmButton.alpha = if (enabled) 1f else 0.45f
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DEFAULT_VALUE = "default_value"
        private const val ARG_HINT = "hint"
        private const val ARG_MESSAGE = "message"
        private const val ARG_PASSWORD = "password"
        private const val ARG_CONFIRM_LABEL_RES = "confirm_label_res"
        private const val ARG_CANCEL_LABEL_RES = "cancel_label_res"

        fun newInstance(
            title: String,
            defaultValue: String,
            hint: String,
            message: String?,
            password: Boolean,
            confirmLabelRes: Int = R.string.confirm,
            cancelLabelRes: Int = R.string.cancel
        ): TextInputDialogFragment {
            return TextInputDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_DEFAULT_VALUE, defaultValue)
                    putString(ARG_HINT, hint)
                    putString(ARG_MESSAGE, message)
                    putBoolean(ARG_PASSWORD, password)
                    putInt(ARG_CONFIRM_LABEL_RES, confirmLabelRes)
                    putInt(ARG_CANCEL_LABEL_RES, cancelLabelRes)
                }
            }
        }
    }
}

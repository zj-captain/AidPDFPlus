package com.ysdc.aidpdf.ui.pdf

import androidx.fragment.app.FragmentActivity
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ui.dialog.TextInputDialogFragment

object InputDialogs {

    fun fileName(
        activity: FragmentActivity,
        title: String,
        defaultValue: String,
        hint: String = activity.getString(R.string.file_name),
        message: String? = null,
        onCancel: () -> Unit = {},
        onSuccessDismiss: ((String) -> Unit)? = null,
        onConfirm: (String) -> Boolean
    ) {
        showTextInput(
            activity = activity,
            title = title,
            defaultValue = defaultValue,
            hint = hint,
            message = message,
            password = false,
            onCancel = onCancel,
            onSuccessDismiss = onSuccessDismiss,
            onConfirm = onConfirm
        )
    }

    fun password(
        activity: FragmentActivity,
        title: String,
        hint: String,
        message: String? = null,
        onCancel: () -> Unit = {},
        onConfirm: (String) -> Boolean
    ) {
        showTextInput(
            activity = activity,
            title = title,
            defaultValue = "",
            hint = hint,
            message = message,
            password = true,
            onCancel = onCancel,
            onSuccessDismiss = null,
            onConfirm = onConfirm
        )
    }

    fun passwordAsync(
        activity: FragmentActivity,
        title: String,
        hint: String,
        message: String? = null,
        onCancel: () -> Unit = {},
        onConfirm: (String, (Boolean) -> Unit) -> Unit
    ) {
        showTextInputAsync(
            activity = activity,
            title = title,
            defaultValue = "",
            hint = hint,
            message = message,
            password = true,
            onCancel = onCancel,
            onSuccessDismiss = null,
            onConfirm = onConfirm
        )
    }

    private fun showTextInput(
        activity: FragmentActivity,
        title: String,
        defaultValue: String,
        hint: String,
        message: String?,
        password: Boolean,
        onCancel: () -> Unit,
        onSuccessDismiss: ((String) -> Unit)?,
        onConfirm: (String) -> Boolean
    ) {
        showTextInputAsync(
            activity = activity,
            title = title,
            defaultValue = defaultValue,
            hint = hint,
            message = message,
            password = password,
            onCancel = onCancel,
            onSuccessDismiss = onSuccessDismiss
        ) { text, handled ->
            handled(onConfirm(text))
        }
    }

    private fun showTextInputAsync(
        activity: FragmentActivity,
        title: String,
        defaultValue: String,
        hint: String,
        message: String?,
        password: Boolean,
        onCancel: () -> Unit,
        onSuccessDismiss: ((String) -> Unit)?,
        onConfirm: (String, (Boolean) -> Unit) -> Unit
    ) {
        TextInputDialogFragment.newInstance(
            title = title,
            defaultValue = defaultValue,
            hint = hint,
            message = message,
            password = password
        ).apply {
            onCancelClick = onCancel
            this.onSuccessDismiss = onSuccessDismiss
            this.onConfirm = onConfirm
        }.show(activity.supportFragmentManager, "text_input_dialog")
    }
}

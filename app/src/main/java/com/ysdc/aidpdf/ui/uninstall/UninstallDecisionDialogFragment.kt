package com.ysdc.aidpdf.ui.uninstall

import com.ysdc.aidpdf.databinding.DialogUninstallDecisionBinding
import com.ysdc.aidpdf.ui.basic.BaseBottomDialogFragment

class UninstallDecisionDialogFragment : BaseBottomDialogFragment<DialogUninstallDecisionBinding>(
    DialogUninstallDecisionBinding::inflate
) {

    var onUninstall: (() -> Unit)? = null
    var onContinueUsing: (() -> Unit)? = null

    override fun bindActions() {
        binding.uninstallButton.setOnClickListener {
            dismissAllowingStateLoss()
            onUninstall?.invoke()
        }
        binding.continueButton.setOnClickListener {
            dismissAllowingStateLoss()
            onContinueUsing?.invoke()
        }
    }

    companion object {
        fun newInstance(): UninstallDecisionDialogFragment {
            return UninstallDecisionDialogFragment()
        }
    }
}

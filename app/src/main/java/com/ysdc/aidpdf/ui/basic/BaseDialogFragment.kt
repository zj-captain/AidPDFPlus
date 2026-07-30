package com.ysdc.aidpdf.ui.basic

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding>(private val bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB) : DialogFragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: error("Dialog binding is no longer active.")

    protected open val dialogWidthRatio: Float = 0.8f
    protected open val canceledOnTouchOutside: Boolean = true

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(canceledOnTouchOutside)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = bindingFactory(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
        bindActions()
        loadContent()
    }

    protected open fun setupViews(savedInstanceState: Bundle?) = Unit
    protected open fun bindActions() = Unit
    protected open fun loadContent() = Unit

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * dialogWidthRatio).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

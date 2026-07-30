package com.ysdc.aidpdf.ui.basic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<VB : ViewBinding>(private val bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB) : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: error("ViewBinding is available only after onCreateView and before onDestroyView.")

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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

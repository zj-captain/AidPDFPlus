package com.ysdc.aidpdf.ui.basic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding>(private val bindingFactory: (LayoutInflater) -> VB) : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindingFactory(layoutInflater)
        setContentView(binding.root)
        normalizeDensity()
        updateSystemBars()
        setupViews(savedInstanceState)
        bindActions()
        loadContent()
    }

    protected open fun setupViews(savedInstanceState: Bundle?) = Unit
    protected open fun bindActions() = Unit
    protected open fun loadContent() = Unit
    protected open fun hideNavigationBar(): Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        updateSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun normalizeDensity() {
        resources.displayMetrics.apply {
            density = heightPixels / 765f
            densityDpi = (density * 160).toInt()
            scaledDensity = density
        }
    }

    private fun updateSystemBars() {
        runCatching {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideNavigationBar()) {
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    protected fun applyEdgeToEdge(topTarget: View? = null, bottomTarget: View? = null, useTopInset: Boolean = true, useBottomInset: Boolean = true) {
        runCatching {
            enableEdgeToEdge()
            updateSystemBars()
            val listenerView = window.decorView
            val topView = topTarget ?: listenerView
            val bottomView = bottomTarget ?: listenerView
            ViewCompat.setOnApplyWindowInsetsListener(listenerView) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val topPadding = if (useTopInset) bars.top else 0
                val bottomPadding = if (useBottomInset) bars.bottom else 0
                if (topView === bottomView) {
                    topView.setPadding(0, topPadding, 0, bottomPadding)
                } else {
                    topView.setPadding(0, topPadding, 0, topView.paddingBottom)
                    bottomView.setPadding(0, bottomView.paddingTop, 0, bottomPadding)
                }
                insets
            }
        }
    }

    protected inline fun <reified T : Activity> openActivity(
        finishCurrent: Boolean = false,
        noinline extras: Intent.() -> Unit = {}
    ) {
        val intent = Intent(this, T::class.java).apply(extras)
        startActivity(intent)
        if (finishCurrent) finish()
    }
}

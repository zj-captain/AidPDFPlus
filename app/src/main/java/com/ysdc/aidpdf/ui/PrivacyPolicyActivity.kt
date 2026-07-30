package com.ysdc.aidpdf.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.core.view.isVisible
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.databinding.ActivityPrivacyPolicyBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity

class PrivacyPolicyActivity : BaseActivity<ActivityPrivacyPolicyBinding>(ActivityPrivacyPolicyBinding::inflate) {

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { handleBack() }
        setupPolicyPage()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { handleBack() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPolicyPage() {
        binding.policyWebView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    binding.titleText.text = title?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.privacy_policy)
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.loadProgress.progress = newProgress
                    binding.loadProgress.isVisible = newProgress < 100
                }
            }
            loadUrl(PRIVACY_POLICY_URL)
        }
    }

    private fun handleBack() {
        if (binding.policyWebView.canGoBack()) {
            binding.policyWebView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        binding.policyWebView.apply {
            stopLoading()
            webChromeClient = null
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private companion object {
        private const val PRIVACY_POLICY_URL = "https://www.baidu.com"
    }
}

package com.ysdc.aidpdf.ui.uninstall

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.google.NativeAdSize
import com.ysdc.aidpdf.databinding.ActivityUninstallProblemBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity

class UninstallProblemActivity : BaseActivity<ActivityUninstallProblemBinding>(ActivityUninstallProblemBinding::inflate) {

    private var nativeAdLease: AdLease? = null
    private var leaving = false

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { openHomeWithAd() }
        InterstitialAdGate.prepareBackMain(this)
        InterstitialAdGate.prepare(this, AdScene.UninstallFirstInterstitial)
        InterstitialAdGate.prepare(this, AdScene.UninstallSecondInterstitial)
        NativeAdGate.prepare(this, AdScene.UninstallFirstNative)
        NativeAdGate.prepare(this, AdScene.UninstallSecondNative)
        showNativeAd()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.root, binding.root)
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { openHomeWithAd() }
        binding.sortingCard.setOnClickListener { openHomeWithAd() }
        binding.sortingAction.setOnClickListener { openHomeWithAd() }
        binding.speedCard.setOnClickListener { openHomeWithAd() }
        binding.speedAction.setOnClickListener { openHomeWithAd() }
        binding.keepButton.setOnClickListener { openHomeWithAd() }
        binding.uninstallButton.setOnClickListener {
            openReasonWithAd()
        }
    }

    override fun onDestroy() {
        nativeAdLease?.release()
        nativeAdLease = null
        super.onDestroy()
    }

    private fun openHomeWithAd() {
        if (leaving) return
        leaving = true
        InterstitialAdGate.showForBackMainThenContinue(this) {
            openHome()
        }
    }

    private fun openReasonWithAd() {
        if (leaving) return
        leaving = true
        InterstitialAdGate.showForUninstallThenContinue(
            activity = this,
            scene = AdScene.UninstallFirstInterstitial
        ) {
            startActivity(Intent(this, UninstallReasonActivity::class.java))
        }
    }

    private fun openHome() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun showNativeAd() {
        NativeAdGate.showWhenReady(
            activity = this,
            parent = binding.uninstallNativeAdContainer,
            scene = AdScene.UninstallFirstNative,
            size = NativeAdSize.Tiny,
            onShown = { lease ->
                nativeAdLease?.release()
                nativeAdLease = lease
            }
        )
    }
}

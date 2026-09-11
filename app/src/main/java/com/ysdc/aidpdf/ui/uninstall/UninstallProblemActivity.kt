package com.ysdc.aidpdf.ui.uninstall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.databinding.ActivityUninstallProblemBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity

class UninstallProblemActivity : BaseActivity<ActivityUninstallProblemBinding>(ActivityUninstallProblemBinding::inflate) {

    private var nativeAdHandle: AdDisplayHandle? = null
    private var leaving = false

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { openHomeWithAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.ResultNative, this)
        Ads.load(AdsScene.MainNative, this)
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
        nativeAdHandle?.destroy()
        nativeAdHandle = null
        super.onDestroy()
    }

    private fun openHomeWithAd() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            openHome()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { openHome() },
            onFailed = { openHome() }
        )
    }

    private fun openReasonWithAd() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            startActivity(Intent(this, UninstallReasonActivity::class.java))
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { startActivity(Intent(this, UninstallReasonActivity::class.java)) },
            onFailed = { startActivity(Intent(this, UninstallReasonActivity::class.java)) }
        )
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
        nativeAdHandle?.destroy()
        nativeAdHandle = Ads.showNative(
            scene = AdsScene.ResultNative,
            activity = this,
            parent = binding.uninstallNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Tiny),
            onShown = {
                Log.d("UninstallProblemActivity", "卸载页原生广告展示成功")
                binding.uninstallNativeAdContainer.isVisible = true
                binding.uninstallNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("UninstallProblemActivity", "卸载页原生广告曝光")
            },
            onFailed = {
                Log.w("UninstallProblemActivity", "卸载页原生广告展示失败：message=${it.message}")
                binding.uninstallNativeAdContainer.removeAllViews()
                binding.uninstallNativeAdContainer.isVisible = false
                binding.uninstallNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }
}

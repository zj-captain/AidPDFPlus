package com.ysdc.aidpdf.ui.uninstall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.ysdc.aidpdf.App
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.databinding.ActivityUninstallReasonBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity

class UninstallReasonActivity : BaseActivity<ActivityUninstallReasonBinding>(ActivityUninstallReasonBinding::inflate) {

    private val selectedReasons = mutableSetOf<UninstallReason>()
    private var nativeAdHandle: AdDisplayHandle? = null
    private var leaving = false

    private val appDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        (application as? App)?.clearHotStartSkip()
        openHome()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { openHomeWithAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
        renderReasonSelection()
        showNativeAd()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.root, binding.root)
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.conversionReasonRow.setOnClickListener { toggleReason(UninstallReason.CONVERSION) }
        binding.storageReasonRow.setOnClickListener { toggleReason(UninstallReason.STORAGE) }
        binding.complexReasonRow.setOnClickListener { toggleReason(UninstallReason.COMPLEX) }
        binding.reconsiderButton.setOnClickListener { openHomeWithAd() }
        binding.uninstallButton.setOnClickListener { showUninstallDecision() }
    }

    override fun onDestroy() {
        nativeAdHandle?.destroy()
        nativeAdHandle = null
        super.onDestroy()
    }

    private fun toggleReason(reason: UninstallReason) {
        if (selectedReasons.contains(reason)) {
            selectedReasons.remove(reason)
        } else {
            selectedReasons.add(reason)
        }
        renderReasonSelection()
    }

    private fun renderReasonSelection() {
        reasonRows().forEach { row ->
            row.check.setImageResource(
                if (selectedReasons.contains(row.reason)) {
                    R.drawable.ic_language_checked
                } else {
                    R.drawable.ic_language_unchecked
                }
            )
        }
    }

    private fun reasonRows(): List<ReasonRow> {
        return listOf(
            ReasonRow(UninstallReason.CONVERSION, binding.conversionReasonCheck),
            ReasonRow(UninstallReason.STORAGE, binding.storageReasonCheck),
            ReasonRow(UninstallReason.COMPLEX, binding.complexReasonCheck)
        )
    }

    private fun showUninstallDecision() {
        if (supportFragmentManager.findFragmentByTag(UNINSTALL_DECISION_DIALOG_TAG) != null) return
        UninstallDecisionDialogFragment.newInstance().apply {
            onUninstall = { openAppDetailsSettingsWithAd() }
            onContinueUsing = { openHomeWithAd() }
        }.show(supportFragmentManager, UNINSTALL_DECISION_DIALOG_TAG)
    }

    private fun openAppDetailsSettingsWithAd() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            openAppDetailsSettings()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { openAppDetailsSettings() },
            onFailed = { openAppDetailsSettings() }
        )
    }

    private fun openAppDetailsSettings() {
        runCatching {
            (application as? App)?.skipNextHotStart()
            appDetailsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }.onFailure {
            (application as? App)?.clearHotStartSkip()
            openHome()
        }
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
            scene = AdsScene.MainNative,
            activity = this,
            parent = binding.uninstallNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Tiny),
            onShown = {
                Log.d("UninstallReasonActivity", "卸载原因页原生广告展示成功")
                binding.uninstallNativeAdContainer.isVisible = true
                binding.uninstallNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("UninstallReasonActivity", "卸载原因页原生广告曝光")
            },
            onFailed = {
                Log.w("UninstallReasonActivity", "卸载原因页原生广告展示失败：message=${it.message}")
                binding.uninstallNativeAdContainer.removeAllViews()
                binding.uninstallNativeAdContainer.isVisible = false
                binding.uninstallNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }

    private data class ReasonRow(
        val reason: UninstallReason,
        val check: ImageView
    )

    private enum class UninstallReason {
        CONVERSION,
        STORAGE,
        COMPLEX
    }

    private companion object {
        private const val UNINSTALL_DECISION_DIALOG_TAG = "uninstall_decision_dialog"
    }
}

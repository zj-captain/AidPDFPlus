package com.ysdc.aidpdf.ui.uninstall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.ysdc.aidpdf.App
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.google.NativeAdSize
import com.ysdc.aidpdf.databinding.ActivityUninstallReasonBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity

class UninstallReasonActivity : BaseActivity<ActivityUninstallReasonBinding>(ActivityUninstallReasonBinding::inflate) {

    private val selectedReasons = mutableSetOf<UninstallReason>()
    private var nativeAdLease: AdLease? = null
    private var leaving = false

    private val appDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        (application as? App)?.clearHotStartSkip()
        openHome()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { openHomeWithAd() }
        InterstitialAdGate.prepareBackMain(this)
        InterstitialAdGate.prepare(this, AdScene.UninstallSecondInterstitial)
        NativeAdGate.prepare(this, AdScene.UninstallSecondNative)
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
        nativeAdLease?.release()
        nativeAdLease = null
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
        InterstitialAdGate.showForUninstallThenContinue(
            activity = this,
            scene = AdScene.UninstallSecondInterstitial
        ) {
            openAppDetailsSettings()
        }
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
        InterstitialAdGate.showForBackMainThenContinue(this) {
            openHome()
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
            scene = AdScene.UninstallSecondNative,
            size = NativeAdSize.Tiny,
            onShown = { lease ->
                nativeAdLease?.release()
                nativeAdLease = lease
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

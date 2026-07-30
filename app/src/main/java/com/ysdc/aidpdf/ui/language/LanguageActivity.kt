package com.ysdc.aidpdf.ui.language

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdTrackingScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.google.NativeAdSize
import com.ysdc.aidpdf.databinding.ActivityLanguageBinding
import com.ysdc.aidpdf.store.hasSavedLanguageTag
import com.ysdc.aidpdf.store.languageTag
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.guide.OnboardingActivity

class LanguageActivity : BaseActivity<ActivityLanguageBinding>(ActivityLanguageBinding::inflate) {

    private lateinit var languageAdapter: LanguageOptionAdapter
    private var nativeAdLease: AdLease? = null
    private var leaving = false
    private val fromFirstRun: Boolean
        get() = intent.getBooleanExtra(EXTRA_FIRST_RUN_FLOW, false)

    override fun hideNavigationBar(): Boolean = true

    override fun setupViews(savedInstanceState: Bundle?) {
        val selectedTag = if (hasSavedLanguageTag) {
            languageTag
        } else {
            AppLanguages.defaultForSystem().tag
        }
        val languages = AppLanguages.ordered(selectedTag)
        languageAdapter = LanguageOptionAdapter(languages, selectedTag)
        binding.languageList.adapter = languageAdapter
        (binding.languageList as RecyclerView).itemAnimator = null
        binding.backButton.isVisible = fromFirstRun.not()
        binding.nextButton.setText(
            if (fromFirstRun) R.string.language_action_next else R.string.language_action_ok
        )
        prepareAds()
        showNativeAd()
        onBackPressedDispatcher.addCallback(this) {
            if (fromFirstRun.not()) {
                finish()
            }
        }
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.nextButton.setOnClickListener { applySelection() }
    }

    override fun onDestroy() {
        nativeAdLease?.release()
        nativeAdLease = null
        super.onDestroy()
    }

    private fun applySelection() {
        if (leaving) return
        leaving = true
        InterstitialAdGate.showForClickThenContinue(
            activity = this,
            scene = AdScene.TopInterstitial,
            trackingScene = AdTrackingScene.LANGUAGE_INTERSTITIAL
        ) {
            applySelectionAfterAd()
        }
    }

    private fun applySelectionAfterAd() {
        val selected = languageAdapter.selectedTag
        languageTag = selected
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
        if (fromFirstRun) {
            openActivity<OnboardingActivity>(finishCurrent = true)
        } else {
            openActivity<MainActivity>(finishCurrent = true) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    private fun prepareAds() {
        InterstitialAdGate.prepare(this, AdScene.TopInterstitial)
        NativeAdGate.prepare(this, AdScene.MainNative)
        if (fromFirstRun) {
            InterstitialAdGate.prepare(this, AdScene.BottomInterstitial)
            NativeAdGate.prepare(this, AdScene.ResultNative)
        } else {
            InterstitialAdGate.prepareStartupInventory(this)
            NativeAdGate.prepare(this, AdScene.MainNative)
        }
    }

    private fun showNativeAd() {
        NativeAdGate.showWhenReady(
            activity = this,
            parent = binding.languageNativeAdContainer,
            scene = AdScene.MainNative,
            size = NativeAdSize.Large,
            trackingScene = AdTrackingScene.LANGUAGE_NATIVE,
            onShown = { lease ->
                nativeAdLease?.release()
                nativeAdLease = lease
            }
        )
    }

    companion object {
        private const val EXTRA_FIRST_RUN_FLOW = "extra_first_run_flow"

        fun firstRunIntent(context: Context): Intent {
            return Intent(context, LanguageActivity::class.java).putExtra(EXTRA_FIRST_RUN_FLOW, true)
        }
    }
}

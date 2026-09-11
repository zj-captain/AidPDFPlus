package com.ysdc.aidpdf.ui.language

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.utils.AdTrackingScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.databinding.ActivityLanguageBinding
import com.ysdc.aidpdf.store.hasSavedLanguageTag
import com.ysdc.aidpdf.store.languageTag
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.guide.OnboardingActivity

class LanguageActivity : BaseActivity<ActivityLanguageBinding>(ActivityLanguageBinding::inflate) {

    private lateinit var languageAdapter: LanguageOptionAdapter
    private var nativeAdHandle: AdDisplayHandle? = null
//    private var leaving = false
    private val fromFirstRun: Boolean
        get() = intent.getBooleanExtra(EXTRA_FIRST_RUN_FLOW, false)

    override fun hideNavigationBar(): Boolean = true

    private var secondCount = 5

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
        /*onBackPressedDispatcher.addCallback(this) {
            *//*if (fromFirstRun.not()) {
                finish()
            }*//*
        }*/
        if (fromFirstRun && !BlockUtils.shouldBlockAds(this)) {
            updateSecondView(binding.nextButton, secondCount) {
                binding.nextButton.callOnClick()
            }
        }
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { finish() }
        binding.nextButton.setOnClickListener {
            timer?.cancel()
            timer = null
            applySelection()
        }
    }

    private var timer: CountDownTimer? = null

    @SuppressLint("SetTextI18n")
    private fun updateSecondView(textView: TextView, second: Int, nextAction: () -> Unit) {
        timer = object : CountDownTimer(second * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                textView.text =
                    "${resources.getString(R.string.language_action_next)}(${remaining}S)"
            }

            override fun onFinish() {
                textView.text = resources.getString(R.string.language_action_next)
                nextAction()
            }
        }.start()
    }

    override fun onStop() {
        super.onStop()
        timer?.cancel()
        timer = null
    }

    override fun onDestroy() {
        nativeAdHandle?.destroy()
        nativeAdHandle = null
        super.onDestroy()
    }

    private fun applySelection() {
//        if (leaving) return
//        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            applySelectionAfterAd()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { applySelectionAfterAd() },
            onFailed = { applySelectionAfterAd() },
            trackingScene = AdTrackingScene.LANGUAGE_INTERSTITIAL
        )
    }

    private fun applySelectionAfterAd() {
        val selected = languageAdapter.selectedTag
        languageTag = selected
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
        if (isFinishing || isDestroyed) return
        if (fromFirstRun) {
            openActivity<OnboardingActivity>(finishCurrent = true)
        } else {
            openActivity<MainActivity>(finishCurrent = true) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    private fun prepareAds() {
        // 插屏固定用 ResultInterstitial 池，原生固定用 MainNative 池。
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
        if (fromFirstRun) {
            // 首次引导流程：预加载后续页面会用的广告位。
            Ads.load(AdsScene.BackInterstitial, this)
            Ads.load(AdsScene.ResultNative, this)
        } else {
            // 非首次：按启动清单预加载首页所需广告位。
            Ads.load(AdsScene.BackInterstitial, this)
            Ads.load(AdsScene.ResultNative, this)
        }
    }

    private fun showNativeAd() {
        nativeAdHandle?.destroy()
        nativeAdHandle = Ads.showNative(
            scene = AdsScene.MainNative,
            activity = this,
            parent = binding.languageNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Large),
            trackingScene = AdTrackingScene.LANGUAGE_NATIVE,
            onShown = {
                Log.d("LanguageActivity", "语言页原生广告展示成功")
                binding.languageNativeAdContainer.isVisible = true
                binding.languageNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("LanguageActivity", "语言页原生广告曝光")
            },
            onFailed = {
                Log.w("LanguageActivity", "语言页原生广告展示失败：message=${it.message}")
                binding.languageNativeAdContainer.removeAllViews()
                binding.languageNativeAdContainer.isVisible = false
                binding.languageNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }

    companion object {
        private const val EXTRA_FIRST_RUN_FLOW = "extra_first_run_flow"

        fun firstRunIntent(context: Context): Intent {
            return Intent(context, LanguageActivity::class.java).putExtra(
                EXTRA_FIRST_RUN_FLOW,
                true
            )
        }
    }
}

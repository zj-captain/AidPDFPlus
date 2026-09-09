package com.ysdc.aidpdf.ui.guide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.utils.AdTrackingScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.databinding.ActivityOnboardingBinding
import com.ysdc.aidpdf.databinding.ItemOnboardingImageBinding
import com.ysdc.aidpdf.reminder.ReminderEventTracker
import com.ysdc.aidpdf.store.isFirstRun
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionPromptPolicy
import kotlin.math.roundToInt

class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>(ActivityOnboardingBinding::inflate) {

    private var nativeAdHandle: AdDisplayHandle? = null
    private var leaving = false

    companion object{
        const val IS_FIRST_RUN = "is_first_run"
    }

    private val pages = listOf(
        OnboardingPage(
            imageRes = R.drawable.img_guide1,
            messageRes = R.string.onboarding_file_management
        ),
        OnboardingPage(
            imageRes = R.drawable.img_guide2,
            messageRes = R.string.onboarding_split_merge
        ),
        OnboardingPage(
            imageRes = R.drawable.img_guide3,
            messageRes = R.string.onboarding_file_security
        )
    )

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            renderPage(position)
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
//        onBackPressedDispatcher.addCallback(this){}
        binding.guidePager.adapter = OnboardingImageAdapter(pages)
        binding.guidePager.offscreenPageLimit = pages.size - 1
        binding.guidePager.isUserInputEnabled = true
        (binding.guidePager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
        binding.guidePager.registerOnPageChangeCallback(pageCallback)
        renderPage(binding.guidePager.currentItem)
        prepareAds()
        showNativeAd()
    }

    override fun bindActions() {
        binding.skipButton.setOnClickListener { finishGuideWithAd() }
        binding.primaryButton.setOnClickListener {
            val nextPage = binding.guidePager.currentItem + 1
            if (nextPage < pages.size) {
                binding.guidePager.setCurrentItem(nextPage, true)
            } else {
                finishGuideWithAd()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyEdgeToEdge(binding.guideRoot, binding.guideRoot)
    }

    override fun onDestroy() {
        binding.guidePager.unregisterOnPageChangeCallback(pageCallback)
        binding.guidePager.adapter = null
        nativeAdHandle?.destroy()
        nativeAdHandle = null
        super.onDestroy()
    }

    private fun renderPage(position: Int) {
        val page = pages.getOrNull(position) ?: return
        binding.messageText.setText(page.messageRes)
        binding.primaryButton.setText(
            if (position == pages.lastIndex) {
                R.string.onboarding_action_start
            } else {
                R.string.onboarding_action_next
            }
        )
        updateIndicators(position)
    }

    private fun updateIndicators(activePosition: Int) {
        val dots = listOf(binding.dotOne, binding.dotTwo, binding.dotThree)
        dots.forEachIndexed { index, dot ->
            val active = index == activePosition
            dot.setBackgroundResource(
                if (active) R.drawable.bg_onboarding_dot_active else R.drawable.bg_onboarding_dot_inactive
            )
            dot.layoutParams = dot.layoutParams.apply {
                width = if (active) 18.dp else 5.dp
                height = 5.dp
            }
        }
    }

    private fun finishGuideWithAd() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            finishGuide()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { finishGuide() },
            onFailed = { finishGuide() },
            trackingScene = AdTrackingScene.GUIDE_INTERSTITIAL
        )
    }

    private fun finishGuide() {
        /*if (isFirstRun){
            isFirstRun = false
            if (shouldShowOverlayPermissionPage()) {
                startActivity(Intent(this, OverlayPermissionActivity::class.java).apply {
                    putExtra(OverlayPermissionActivity.EXTRA_FIRST_RUN_FLOW, isFirstRun)
                    putExtra(OverlayPermissionActivity.EXTRA_LAUNCH_FLOW, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }else{
                openActivity<MainActivity>(finishCurrent = true) {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            }
        }else{
            openActivity<MainActivity>(finishCurrent = true) {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }*/
        openActivity<MainActivity>(finishCurrent = true) {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun shouldShowOverlayPermissionPage(): Boolean {
        return OverlayPermissionPromptPolicy.shouldShowLaunchPage(
            source = ReminderEventTracker.source(intent),
            launchedFromAppIcon = true,
            forceOpenAdLaunch = true,
            canDrawOverlays = canDrawOverlays(),
            adsBlocked = BlockUtils.shouldBlockAds(this).apply {
                Log.e(
                    "TAG",
                    "shouldShowOverlayPermissionPage: $this"
                ) }
        )
    }
    private fun prepareAds() {
        // 插屏固定用 BackInterstitial 池，原生固定用 ResultNative 池。
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultNative, this)
        // 启动清单：预加载首页所需广告位。
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
    }

    private fun showNativeAd() {
        nativeAdHandle?.destroy()
        nativeAdHandle = Ads.showNative(
            scene = AdsScene.ResultNative,
            activity = this,
            parent = binding.onboardingNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Medium),
            trackingScene = AdTrackingScene.GUIDE_NATIVE,
            onShown = {
                Log.d("OnboardingActivity", "引导页原生广告展示成功")
                binding.onboardingNativeAdContainer.isVisible = true
                binding.onboardingNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("OnboardingActivity", "引导页原生广告曝光")
            },
            onFailed = {
                Log.w("OnboardingActivity", "引导页原生广告展示失败：message=${it.message}")
                binding.onboardingNativeAdContainer.removeAllViews()
                binding.onboardingNativeAdContainer.isVisible = false
                binding.onboardingNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private data class OnboardingPage(
        @DrawableRes val imageRes: Int,
        @StringRes val messageRes: Int
    )

    private class OnboardingImageAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<OnboardingImageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val binding = ItemOnboardingImageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount(): Int = pages.size

        private class PageViewHolder(
            private val binding: ItemOnboardingImageBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(page: OnboardingPage) {
                binding.guideImage.setImageResource(page.imageRes)
            }
        }
    }
}

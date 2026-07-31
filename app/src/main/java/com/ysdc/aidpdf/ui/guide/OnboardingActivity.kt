package com.ysdc.aidpdf.ui.guide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.config.AdTrackingScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.google.NativeAdSize
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

    private var nativeAdLease: AdLease? = null
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
        onBackPressedDispatcher.addCallback(this){}
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
        nativeAdLease?.release()
        nativeAdLease = null
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
        InterstitialAdGate.showForClickThenContinue(
            activity = this,
            scene = AdScene.BottomInterstitial,
            trackingScene = AdTrackingScene.GUIDE_INTERSTITIAL
        ) {
            finishGuide()
        }
    }

    private fun finishGuide() {
        if (isFirstRun){
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
        InterstitialAdGate.prepare(this, AdScene.BottomInterstitial)
        NativeAdGate.prepare(this, AdScene.ResultNative)
        InterstitialAdGate.prepareStartupInventory(this)
        NativeAdGate.prepare(this, AdScene.MainNative)
    }

    private fun showNativeAd() {
        NativeAdGate.showWhenReady(
            activity = this,
            parent = binding.onboardingNativeAdContainer,
            scene = AdScene.ResultNative,
            size = NativeAdSize.Medium,
            trackingScene = AdTrackingScene.GUIDE_NATIVE,
            onShown = { lease ->
                nativeAdLease?.release()
                nativeAdLease = lease
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

package com.ysdc.aidpdf.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.ysdc.aidpdf.App
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsPlatform
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.BannerRenderRequest
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.ads.utils.AdTrackingScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.core.permission.canDrawOverlays
import com.ysdc.aidpdf.core.permission.canPostNotifications
import com.ysdc.aidpdf.core.permission.notificationSettingsIntent
import com.ysdc.aidpdf.data.document.DocumentKind
import com.ysdc.aidpdf.data.document.DocumentLibrary
import com.ysdc.aidpdf.data.document.HomeSection
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.data.document.replaceRenamedDocument
import com.ysdc.aidpdf.databinding.ActivityMainBinding
import com.ysdc.aidpdf.reminder.model.ReminderTarget
import com.ysdc.aidpdf.reminder.store.ReminderNavigationStore
import com.ysdc.aidpdf.reminder.task.ReminderTriggerCenter
import com.ysdc.aidpdf.store.isFirstJudgeShowCustomNotify
import com.ysdc.aidpdf.store.lastRateShowTime
import com.ysdc.aidpdf.store.rateValue
import com.ysdc.aidpdf.store.requestSysNotificationCount
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.dialog.ConfirmDialogFragment
import com.ysdc.aidpdf.ui.dialog.TextInputDialogFragment
import com.ysdc.aidpdf.ui.document.DocumentDetailActivity
import com.ysdc.aidpdf.ui.document.DocumentPagerAdapter
import com.ysdc.aidpdf.ui.pdf.CreatedPdfStore
import com.ysdc.aidpdf.ui.pdf.InputDialogs
import com.ysdc.aidpdf.ui.pdf.PdfCreateResultActivity
import com.ysdc.aidpdf.ui.pdf.PdfTaskDelay
import com.ysdc.aidpdf.ui.pdf.PdfToolActivity
import com.ysdc.aidpdf.ui.pdf.PdfToolActivity.PdfToolMode
import com.ysdc.aidpdf.ui.pdf.TaskLoadingDialog
import com.ysdc.aidpdf.ui.permission.NotificationPermissionDialogFragment
import com.ysdc.aidpdf.ui.permission.OverlayPermissionCheckActivity
import com.ysdc.aidpdf.ui.permission.OverlayPermissionDialogFragment
import com.ysdc.aidpdf.ui.permission.OverlayPermissionPromptPolicy
import com.ysdc.aidpdf.ui.reader.OfficePreviewActivity
import com.ysdc.aidpdf.ui.reader.PdfPreviewActivity
import com.ysdc.aidpdf.tracking.AidEventHub
import com.ysdc.aidpdf.tracking.TrackingEventNames
import com.ysdc.aidpdf.ui.dialog.RateUsDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    private val documentKinds = DocumentKind.filters
    private val documentLibrary by lazy { DocumentLibrary(this) }
    private var bannerAdHandle: AdDisplayHandle? = null
    private val pagerAdapters by lazy {
        listOf(HomeSection.HOME, HomeSection.RECENT, HomeSection.FAVORITES).associateWith {
            DocumentPagerAdapter(
                onOpen = ::openDocument,
                onFavorite = ::toggleFavorite,
                onMore = ::showMoreSheet,
                onGrantPermission = { requestDocumentPermission() }
            )
        }
    }
    private val pageChangeCallbacks = mutableMapOf<HomeSection, ViewPager2.OnPageChangeCallback>()

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDocuments(forcePermissionCheck = true)
        openPendingAfterPermission()
    }

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        clearExternalFlowSkip()
        when (result.resultCode) {
            RESULT_OK -> handleScannerResult(result.data)
            RESULT_CANCELED -> Toast.makeText(this, R.string.create_pdf_cancelled, Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(this, R.string.create_pdf_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val externalActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        clearExternalFlowSkip()
    }

    private val documentSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        clearExternalFlowSkip()
        refreshDocuments(forcePermissionCheck = false)
        openPendingAfterPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) AidEventHub.track(TrackingEventNames.SYSTEM_NOTIFICATION_ALLOW)
        systemNotificationPromptHandled = true
        scheduleHomePermissionChecks()
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        openingOverlaySettings = false
        clearExternalFlowSkip()
        if (canDrawOverlays()) {
            AidEventHub.track(TrackingEventNames.FLOATING_NOTIFICATION_ALLOW)
        }
        scheduleHomePermissionChecks()
    }

    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        openingNotificationSettings = false
        clearExternalFlowSkip()
        if (canPostNotifications()) {
            AidEventHub.track(TrackingEventNames.SYSTEM_NOTIFICATION_ALLOW)
        }
        scheduleHomePermissionChecks()
    }

    private var activeSection = HomeSection.HOME
    private var activeKind = DocumentKind.PDF
    private val activeKindBySection = mutableMapOf(
        HomeSection.HOME to DocumentKind.PDF,
        HomeSection.RECENT to DocumentKind.PDF,
        HomeSection.FAVORITES to DocumentKind.PDF
    )
    private var homeDocuments = emptyList<LocalDocument>()
    private var recentDocuments = emptyList<LocalDocument>()
    private var favoriteDocuments = emptyList<LocalDocument>()
    private var permissionWatcher: Job? = null
    private var pendingAfterPermissionIntent: Intent? = null
    private var filterIndicatorPositioned = false
    private var sectionChromeReady = false
    private var sectionNativeAdHandle: AdDisplayHandle? = null
    private var openingOverlaySettings = false
    private var openingNotificationSettings = false
    private var skipOverlayPermissionPromptForLaunch = false
    private var overlayPermissionPromptHandled = false
    private var systemNotificationPromptHandled = false
    private var permissionCheckRetryScheduled = false
    private val permissionCheckRunnable = Runnable { continueHomePermissionChecks() }

    override fun hideNavigationBar(): Boolean = false

    override fun setupViews(savedInstanceState: Bundle?) {
        AidEventHub.track(TrackingEventNames.HOMEPAGE_VIEW)
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
        Ads.load(AdsScene.ResultNative, this)
        setupDocumentPager(HomeSection.HOME, binding.homeDocumentPager)
        setupDocumentPager(HomeSection.RECENT, binding.recentDocumentPager)
        setupDocumentPager(HomeSection.FAVORITES, binding.favoritesDocumentPager)
        binding.filterBar.doOnLayout { moveFilterIndicator(animate = false) }
        selectSection(HomeSection.HOME)
        openPendingReminderTarget()
    }

    override fun bindActions() {
        bindFilter(binding.pdfFilter, DocumentKind.PDF)
        bindFilter(binding.wordFilter, DocumentKind.WORD)
        bindFilter(binding.excelFilter, DocumentKind.EXCEL)
        bindFilter(binding.pptFilter, DocumentKind.PPT)
        binding.homeTab.setOnClickListener { selectSectionFromBottom(HomeSection.HOME) }
        binding.recentTab.setOnClickListener { selectSectionFromBottom(HomeSection.RECENT) }
        binding.favoritesTab.setOnClickListener { selectSectionFromBottom(HomeSection.FAVORITES) }
        binding.settingsTab.setOnClickListener { selectSectionFromBottom(HomeSection.SETTING) }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.createPdfButton.setOnClickListener { startCreatePdfFlow() }
        binding.scanToPdfTool.setOnClickListener { startCreatePdfFlow() }
        binding.mergeTool.setOnClickListener { openPdfTool(PdfToolMode.MERGE) }
        binding.splitTool.setOnClickListener { openPdfTool(PdfToolMode.SPLIT) }
        binding.lockTool.setOnClickListener { openPdfTool(PdfToolMode.LOCK) }
        binding.unlockTool.setOnClickListener { openPdfTool(PdfToolMode.UNLOCK) }
    }

    override fun loadContent() {
        refreshDocuments(forcePermissionCheck = true)
    }

    override fun onResume() {
        super.onResume()
        refreshDocuments(forcePermissionCheck = false)
        openPendingAfterPermission()
        showSectionNative()
        showMainBanner()
        scheduleHomePermissionChecks()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        skipOverlayPermissionPromptForLaunch = false
        ReminderNavigationStore.capture(intent)
        openPendingReminderTarget()
    }

    override fun onStop() {
        bannerAdHandle?.destroy()
        bannerAdHandle = null
        hideSectionNative()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        //无效触发场景
//        ReminderTriggerCenter.onUserLeaveHint()
    }

    override fun onDestroy() {
        permissionWatcher?.cancel()
        permissionWatcher = null
        binding.root.removeCallbacks(permissionCheckRunnable)
        documentPagers().forEach { (section, pager) ->
            pageChangeCallbacks[section]?.let { pager.unregisterOnPageChangeCallback(it) }
            pager.adapter = null
        }
        pageChangeCallbacks.clear()
        hideSectionNative()
        bannerAdHandle?.destroy()
        bannerAdHandle = null
        super.onDestroy()
    }

    private fun setupDocumentPager(section: HomeSection, pager: ViewPager2) {
        pager.adapter = pagerAdapters.getValue(section)
        pager.offscreenPageLimit = documentKinds.size - 1
        (pager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val kind = documentKinds.getOrNull(position) ?: return
                if (activeSection != section) {
                    activeKindBySection[section] = kind
                    return
                }
                if (activeKind == kind) return
                val applyKind = {
                    val currentKind = documentKinds.getOrNull(pager.currentItem) ?: kind
                    activeKind = currentKind
                    activeKindBySection[section] = currentKind
                    updateFilterState()
                }
                if (BlockUtils.shouldBlockAds(this@MainActivity)) {
                    applyKind()
                    return
                }
                Ads.showFullScreen(
                    scene = AdsScene.ResultInterstitial,
                    activity = this@MainActivity,
                    onClosed = { applyKind() },
                    onFailed = { applyKind() }
                )
            }
        }
        pageChangeCallbacks[section] = callback
        pager.registerOnPageChangeCallback(callback)
    }

    private fun bindFilter(view: TextView, kind: DocumentKind) {
        view.setOnClickListener {
            selectKindFromTop(kind)
        }
    }

    private fun selectKindFromTop(kind: DocumentKind) {
        if (activeSection == HomeSection.SETTING) return
        val page = documentKinds.indexOf(kind)
        if (page < 0) return
        val pager = documentPagerFor(activeSection) ?: return
        if (activeKind == kind && pager.currentItem == page) return
        val applyKind = {
            activeKind = kind
            activeKindBySection[activeSection] = kind
            updateFilterState()
            if (pager.currentItem != page) {
                pager.setCurrentItem(page, true)
            }
        }
        if (BlockUtils.shouldBlockAds(this)) {
            applyKind()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { applyKind() },
            onFailed = { applyKind() }
        )
    }

    private fun refreshDocuments(forcePermissionCheck: Boolean) {
        if (forcePermissionCheck && canReadDocuments().not()) {
            renderPermissionState()
            return
        }
        if (canReadDocuments().not()) {
            renderPermissionState()
            return
        }
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                DocumentSnapshot(
                    home = documentLibrary.homeDocuments(),
                    recent = documentLibrary.recentDocuments(),
                    favorites = documentLibrary.favoriteDocuments()
                )
            }
            homeDocuments = data.home
            recentDocuments = data.recent
            favoriteDocuments = data.favorites
            renderCurrentSection()
        }
    }

    private fun selectSection(section: HomeSection) {
        if (sectionChromeReady && activeSection == section) return
        activeSection = section
        if (section != HomeSection.SETTING) {
            activeKind = activeKindBySection[section] ?: DocumentKind.PDF
        }
        updateSectionChrome()
        syncDocumentPagerToSection()
        renderCurrentSection()
        if (section == HomeSection.HOME) {
            scheduleHomePermissionChecks()
        }
    }

    private fun openPendingReminderTarget() {
        val navigation = ReminderNavigationStore.consume() ?: return
        skipOverlayPermissionPromptForLaunch =
            OverlayPermissionPromptPolicy.skipForReminder(navigation.source)
        val kind = when (ReminderTarget.fromValue(navigation.target)) {
            ReminderTarget.PDF -> DocumentKind.PDF
            ReminderTarget.WORD -> DocumentKind.WORD
            ReminderTarget.EXCEL -> DocumentKind.EXCEL
            ReminderTarget.PPT -> DocumentKind.PPT
            null -> return
        }
        activeKindBySection[HomeSection.HOME] = kind
        if (activeSection != HomeSection.HOME) {
            selectSection(HomeSection.HOME)
            return
        }
        activeKind = kind
        updateSectionChrome()
        syncDocumentPagerToSection()
        renderCurrentSection()
    }

    private fun scheduleHomePermissionChecks() {
        if (openingOverlaySettings || openingNotificationSettings) return
        binding.root.removeCallbacks(permissionCheckRunnable)
        binding.root.post(permissionCheckRunnable)
    }

    private fun continueHomePermissionChecks() {
        if (openingOverlaySettings || openingNotificationSettings) return
        if (activeSection != HomeSection.HOME) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (hasBlockingDialog()) {
            retryHomePermissionChecks()
            return
        }
        if (requestNotificationPermissionIfNeeded()) return
        if (showNotificationGuideIfNeeded()) return
        checkRateUsDialogShow()
    }

    private fun showOverlayPermissionIfNeeded(): Boolean {
        if (skipOverlayPermissionPromptForLaunch) return false
        if (BlockUtils.shouldBlockAds(this)) return false
        if (canDrawOverlays()) return false
        if (overlayPermissionPromptHandled) return false
        if (supportFragmentManager.isStateSaved) return false
        overlayPermissionPromptHandled = true
        OverlayPermissionDialogFragment().apply {
            onGrant = { openOverlayPermissionSettings() }
            onClosed = {
                if (!openingOverlaySettings) {
                    scheduleHomePermissionChecks()
                }
            }
        }.show(supportFragmentManager, TAG_OVERLAY_PERMISSION)
        return true
    }

    private fun openOverlayPermissionSettings() {
        openingOverlaySettings = true
        runCatching {
            overlaySettingsLauncher.launch(
                Intent(this, OverlayPermissionCheckActivity::class.java).apply {
                    putExtra(OverlayPermissionCheckActivity.EXTRA_RETURN_TO_HOME, true)
                }
            )
        }.onFailure {
            openingOverlaySettings = false
            clearExternalFlowSkip()
            scheduleHomePermissionChecks()
        }
    }

    private fun requestNotificationPermissionIfNeeded(): Boolean {
        if (canPostNotifications()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (systemNotificationPromptHandled) return false
        if (requestSysNotificationCount > 1) return false
        AidEventHub.track(TrackingEventNames.SYSTEM_NOTIFICATION_POPUP_VIEW)
        requestSysNotificationCount++
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    private fun showNotificationGuideIfNeeded(): Boolean {
        if (canPostNotifications() || notificationGuideShownThisProcess) return false
        if (isFirstJudgeShowCustomNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isFirstJudgeShowCustomNotify = false
            return false
        }
        if (supportFragmentManager.isStateSaved) return false
        notificationGuideShownThisProcess = true
        NotificationPermissionDialogFragment().apply {
            onOpenSettings = { openNotificationSettings() }
            onClosed = {
                if (!openingNotificationSettings) {
                    scheduleHomePermissionChecks()
                }
            }
        }.show(supportFragmentManager, TAG_NOTIFICATION_PERMISSION)
        return true
    }

    private fun openNotificationSettings() {
        openingNotificationSettings = true
        markExternalFlowSkip()
        runCatching {
            notificationSettingsLauncher.launch(notificationSettingsIntent())
            lifecycleScope.launch {
                var haveNotifyPermission = canPostNotifications()
                while (!haveNotifyPermission) {
                    delay(400.milliseconds)
                    haveNotifyPermission = canPostNotifications()
                }
                val intent = Intent(this@MainActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.putExtra("customNotify", true)
                startActivity(intent)
            }
        }.onFailure {
            openingNotificationSettings = false
            clearExternalFlowSkip()
            scheduleHomePermissionChecks()
        }
    }

    private fun hasBlockingDialog(): Boolean {
        return supportFragmentManager.fragments.any { fragment ->
            fragment is DialogFragment && fragment.dialog?.isShowing == true
        }
    }

    private fun retryHomePermissionChecks() {
        if (permissionCheckRetryScheduled) return
        permissionCheckRetryScheduled = true
        binding.root.postDelayed({
            permissionCheckRetryScheduled = false
            continueHomePermissionChecks()
        }, HOME_PERMISSION_RETRY_MILLIS)
    }

    private fun selectSectionFromBottom(section: HomeSection) {
        if (sectionChromeReady && activeSection == section) return
        val applySection = {
            selectSection(section)
            refreshSectionNative()
            syncMainBannerVisibility()
        }
        if (BlockUtils.shouldBlockAds(this)) {
            applySection()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { applySection() },
            onFailed = { applySection() }
        )
    }

    private fun updateSectionChrome() {
        val title = when (activeSection) {
            HomeSection.HOME -> getString(R.string.app_name)
            HomeSection.RECENT -> getString(R.string.tab_recent)
            HomeSection.FAVORITES -> getString(R.string.tab_favorites)
            HomeSection.SETTING -> getString(R.string.tab_tools)
        }
        binding.titleText.text = title

        val showDocuments = activeSection != HomeSection.SETTING
        binding.filterBar.isVisible = showDocuments
        binding.filterIndicator.isVisible = showDocuments
        binding.homeDocumentPager.isVisible = showDocuments && activeSection == HomeSection.HOME
        binding.recentDocumentPager.isVisible = showDocuments && activeSection == HomeSection.RECENT
        binding.favoritesDocumentPager.isVisible =
            showDocuments && activeSection == HomeSection.FAVORITES
        binding.createPdfButton.isVisible = activeSection == HomeSection.HOME && canReadDocuments()
        binding.settingsContent.isVisible = activeSection == HomeSection.SETTING
        binding.settingsButton.isVisible = activeSection == HomeSection.SETTING

        setTabState(
            active = activeSection == HomeSection.HOME,
            icon = binding.homeTabIcon,
            label = binding.homeTabText,
            activeIcon = R.drawable.img_home_black,
            inactiveIcon = R.drawable.img_home_gray
        )
        setTabState(
            active = activeSection == HomeSection.RECENT,
            icon = binding.recentTabIcon,
            label = binding.recentTabText,
            activeIcon = R.drawable.img_recent_black,
            inactiveIcon = R.drawable.img_recent_gray
        )
        setTabState(
            active = activeSection == HomeSection.FAVORITES,
            icon = binding.favoritesTabIcon,
            label = binding.favoritesTabText,
            activeIcon = R.drawable.img_favorites_black,
            inactiveIcon = R.drawable.img_favorites_gray
        )
        setTabState(
            active = activeSection == HomeSection.SETTING,
            icon = binding.settingsTabIcon,
            label = binding.settingsTabText,
            activeIcon = R.drawable.img_tool_black,
            inactiveIcon = R.drawable.img_tool_grey
        )
        updateFilterState(animateIndicator = false)
        sectionChromeReady = true
    }

    private fun showMainBanner() {
        if (!canShowMainBanner()) return
        bannerAdHandle?.destroy()
        bannerAdHandle = Ads.showBanner(
            scene = AdsScene.MainBanner,
            platform = AdsPlatform.AdMob,
            activity = this,
            parent = binding.bannerAdContainer,
            request = BannerRenderRequest(),
            onImpression = {
                Log.d("MainActivity", "首页 Banner 广告曝光")
                syncMainBannerVisibility()
            },
            onFailed = {
                Log.w("MainActivity", "首页 Banner 广告展示失败：message=${it.message}")
                binding.bannerAdContainer.removeAllViews()
                binding.bannerAdContainer.isVisible = false
            }
        )
    }

    private fun canShowMainBanner(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        return binding.bannerAdContainer.isAttachedToWindow
    }

    private fun syncMainBannerVisibility() {
        binding.bannerAdContainer.isVisible = binding.bannerAdContainer.childCount > 0
    }

    private fun showSectionNative() {
        val parent = if (activeSection == HomeSection.SETTING) {
            binding.toolsNativeAdContainer
        } else {
            binding.mainNativeAdContainer
        }
        val scene = if (activeSection == HomeSection.SETTING) {
            AdsScene.ResultNative
        } else {
            AdsScene.MainNative
        }
        val style = if (activeSection == HomeSection.SETTING) {
            NativeAdStyle.Medium
        } else {
            NativeAdStyle.Tiny
        }
        if (parent.isVisible && parent.childCount > 0) return
        parent.isVisible = false
        sectionNativeAdHandle?.destroy()
        sectionNativeAdHandle = Ads.showNative(
            scene = scene,
            activity = this,
            parent = parent,
            request = NativeRenderRequest(style = style),
            onShown = {
                Log.d("MainActivity", "首页分区原生广告展示成功")
                parent.isVisible = true
                parent.updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            },
            onImpression = {
                Log.d("MainActivity", "首页分区原生广告曝光")
            },
            onFailed = {
                Log.w("MainActivity", "首页分区原生广告展示失败：message=${it.message}")
                parent.removeAllViews()
                parent.isVisible = false
                parent.updateLayoutParams { height = 0 }
            }
        )
    }

    private fun refreshSectionNative() {
        hideSectionNative()
        showSectionNative()
    }

    private fun hideSectionNative() {
        sectionNativeAdHandle?.destroy()
        sectionNativeAdHandle = null
        listOf(binding.mainNativeAdContainer, binding.toolsNativeAdContainer).forEach { parent ->
            parent.removeAllViews()
            parent.isVisible = false
        }
    }

    private fun setTabState(
        active: Boolean,
        icon: ImageView,
        label: TextView,
        activeIcon: Int,
        inactiveIcon: Int
    ) {
        icon.setImageResource(if (active) activeIcon else inactiveIcon)
        label.setTextColor(getColor(if (active) R.color.text_primary else R.color.tab_inactive))
        label.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun updateFilterState(animateIndicator: Boolean = true) {
        val filters = listOf(
            binding.pdfFilter to DocumentKind.PDF,
            binding.wordFilter to DocumentKind.WORD,
            binding.excelFilter to DocumentKind.EXCEL,
            binding.pptFilter to DocumentKind.PPT
        )
        filters.forEach { (view, kind) ->
            val active = kind == activeKind
            view.setTextColor(getColor(if (active) R.color.color_primary else R.color.chip_inactive))
            view.setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        moveFilterIndicator(animate = animateIndicator && filterIndicatorPositioned)
    }

    private fun moveFilterIndicator(animate: Boolean) {
        val target = when (activeKind) {
            DocumentKind.PDF -> binding.pdfFilter
            DocumentKind.WORD -> binding.wordFilter
            DocumentKind.EXCEL -> binding.excelFilter
            DocumentKind.PPT -> binding.pptFilter
        }
        val indicatorWidth = binding.filterIndicator.width
            .takeIf { it > 0 }
            ?: binding.filterIndicator.layoutParams.width.takeIf { it > 0 }
            ?: return
        if (binding.filterBar.width <= 0 || target.width <= 0) {
            binding.filterBar.doOnLayout { moveFilterIndicator(animate = false) }
            return
        }

        val translation = target.left + target.width / 2f - indicatorWidth / 2f
        if (animate) {
            binding.filterIndicator.animate()
                .translationX(translation)
                .setDuration(120L)
                .start()
        } else {
            binding.filterIndicator.animate().cancel()
            binding.filterIndicator.translationX = translation
        }
        filterIndicatorPositioned = true
    }

    private fun renderCurrentSection() {
        if (activeSection == HomeSection.SETTING) {
            return
        }
        if (canReadDocuments().not()) {
            renderPermissionState()
            return
        }

        binding.settingsContent.isVisible = false
        binding.homeDocumentPager.isVisible = activeSection == HomeSection.HOME
        binding.recentDocumentPager.isVisible = activeSection == HomeSection.RECENT
        binding.favoritesDocumentPager.isVisible = activeSection == HomeSection.FAVORITES
        binding.createPdfButton.isVisible = activeSection == HomeSection.HOME
        submitDocumentSections(hasPermission = true)
    }

    private fun renderPermissionState() {
        if (activeSection == HomeSection.SETTING) return
        binding.settingsContent.isVisible = false
        binding.homeDocumentPager.isVisible = activeSection == HomeSection.HOME
        binding.recentDocumentPager.isVisible = activeSection == HomeSection.RECENT
        binding.favoritesDocumentPager.isVisible = activeSection == HomeSection.FAVORITES
        binding.createPdfButton.isVisible = false
        submitDocumentSections(hasPermission = false)
    }

    private fun submitDocumentSections(hasPermission: Boolean) {
        pagerAdapters.getValue(HomeSection.HOME).submitSection(
            section = HomeSection.HOME,
            documents = if (hasPermission) homeDocuments else emptyList(),
            hasPermission = hasPermission
        )
        pagerAdapters.getValue(HomeSection.RECENT).submitSection(
            section = HomeSection.RECENT,
            documents = if (hasPermission) recentDocuments else emptyList(),
            hasPermission = hasPermission
        )
        pagerAdapters.getValue(HomeSection.FAVORITES).submitSection(
            section = HomeSection.FAVORITES,
            documents = if (hasPermission) favoriteDocuments else emptyList(),
            hasPermission = hasPermission
        )
    }

    private fun syncDocumentPagerToSection() {
        if (activeSection == HomeSection.SETTING) return
        val page = documentKinds.indexOf(activeKind)
        val pager = documentPagerFor(activeSection) ?: return
        if (page >= 0 && pager.currentItem != page) {
            pager.setCurrentItem(page, false)
        }
    }

    private fun documentPagerFor(section: HomeSection): ViewPager2? {
        return when (section) {
            HomeSection.HOME -> binding.homeDocumentPager
            HomeSection.RECENT -> binding.recentDocumentPager
            HomeSection.FAVORITES -> binding.favoritesDocumentPager
            HomeSection.SETTING -> null
        }
    }

    private fun documentPagers(): List<Pair<HomeSection, ViewPager2>> {
        return listOf(
            HomeSection.HOME to binding.homeDocumentPager,
            HomeSection.RECENT to binding.recentDocumentPager,
            HomeSection.FAVORITES to binding.favoritesDocumentPager
        )
    }

    private fun openDocument(document: LocalDocument) {
        if (BlockUtils.shouldBlockAds(this)) {
            openDocumentAfterAd(document)
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { openDocumentAfterAd(document) },
            onFailed = { openDocumentAfterAd(document) }
        )
    }

    private fun openDocumentAfterAd(document: LocalDocument) {
        val opened = documentLibrary.markOpened(document)
        applyOpenedDocument(opened)
        refreshDocuments(forcePermissionCheck = false)
        if (opened.kind == DocumentKind.PDF) {
            startActivity(PdfPreviewActivity.intent(this, opened))
            return
        }
        startActivity(OfficePreviewActivity.intent(this, opened))
    }

    private fun openWithSystemViewer(document: LocalDocument) {
        runCatching {
            val uri =
                FileProvider.getUriForFile(this, "$packageName.fileProvider", File(document.path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, document.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            markExternalFlowSkip()
            externalActivityLauncher.launch(intent)
        }.onFailure { error ->
            clearExternalFlowSkip()
            if (error is ActivityNotFoundException) {
                Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFavorite(document: LocalDocument) {
        val updated = documentLibrary.toggleFavorite(document)
        applyFavoriteChange(updated)
        Toast.makeText(
            this,
            if (updated.favorite) R.string.favorite_added else R.string.favorite_removed,
            Toast.LENGTH_SHORT
        ).show()
        refreshDocuments(forcePermissionCheck = false)
    }

    private fun applyOpenedDocument(opened: LocalDocument) {
        homeDocuments = homeDocuments.map { document ->
            if (document.path == opened.path) {
                document.copy(
                    openedAt = opened.openedAt,
                    favorite = opened.favorite,
                    favoriteAt = opened.favoriteAt
                )
            } else {
                document
            }
        }
        recentDocuments = (listOf(opened) + recentDocuments.filterNot { it.path == opened.path })
            .sortedByDescending { it.openedAt }
        favoriteDocuments = favoriteDocuments.map { document ->
            if (document.path == opened.path) {
                document.copy(openedAt = opened.openedAt)
            } else {
                document
            }
        }
        if (canReadDocuments()) {
            submitDocumentSections(hasPermission = true)
        }
    }

    private fun applyFavoriteChange(updated: LocalDocument) {
        homeDocuments = homeDocuments.mapFavoriteState(updated)
        recentDocuments = recentDocuments.mapFavoriteState(updated)
        favoriteDocuments = if (updated.favorite) {
            (listOf(updated) + favoriteDocuments.filterNot { it.path == updated.path })
                .sortedByDescending { it.favoriteAt }
        } else {
            favoriteDocuments.filterNot { it.path == updated.path }
        }
        if (canReadDocuments()) {
            submitDocumentSections(hasPermission = true)
        }
    }

    private fun List<LocalDocument>.mapFavoriteState(updated: LocalDocument): List<LocalDocument> {
        return map { document ->
            if (document.path == updated.path) {
                document.copy(
                    openedAt = updated.openedAt.takeIf { it > 0L } ?: document.openedAt,
                    favorite = updated.favorite,
                    favoriteAt = updated.favoriteAt
                )
            } else {
                document
            }
        }
    }

    private fun applyRenamedDocument(previousPath: String, renamed: LocalDocument) {
        homeDocuments = homeDocuments.replaceRenamedDocument(previousPath, renamed)
        recentDocuments = recentDocuments.replaceRenamedDocument(previousPath, renamed)
        favoriteDocuments = favoriteDocuments.replaceRenamedDocument(previousPath, renamed)
        if (canReadDocuments()) {
            submitDocumentSections(hasPermission = true)
        }
    }

    private fun showMoreSheet(document: LocalDocument) {
        val dialog = BottomSheetDialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp(), 14.dp(), 16.dp(), 10.dp())
            setBackgroundResource(R.drawable.bg_sheet_panel)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 128.dp())
        }
        panel.addView(actionItem(R.drawable.img_share, R.string.share) {
            dialog.dismiss()
            shareDocument(document)
        })
        panel.addView(actionItem(R.drawable.img_information, R.string.details) {
            dialog.dismiss()
            startActivity(DocumentDetailActivity.intent(this, document))
        })
        panel.addView(actionItem(R.drawable.img_rename, R.string.rename) {
            dialog.dismiss()
            showRenameDialog(document)
        })
        panel.addView(actionItem(R.drawable.img_delete, R.string.delete) {
            dialog.dismiss()
            showDeleteDialog(document)
        })
        dialog.setContentView(panel)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.background = ColorDrawable(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun openPdfTool(mode: PdfToolMode, preselectedPath: String? = null) {
        Ads.load(AdsScene.ResultNative, this)
        val intent = PdfToolActivity.intent(this, mode, preselectedPath)
        if (canReadDocuments()) {
            startActivity(intent)
        } else {
            requestDocumentPermission(intent)
        }
    }

    private fun startCreatePdfFlow() {
        if (canReadDocuments().not()) {
            requestDocumentPermission()
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                markExternalFlowSkip()
                createPdfLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.create_pdf_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScannerResult(data: Intent?) {
        val pdfUri = GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf?.uri
        if (pdfUri == null) {
            Toast.makeText(this, R.string.create_pdf_failed, Toast.LENGTH_SHORT).show()
            return
        }
        InputDialogs.fileName(
            activity = this,
            title = getString(R.string.create_pdf),
            defaultValue = CreatedPdfStore.defaultName(),
            hint = getString(R.string.file_name),
            onCancel = { CreatedPdfStore.deleteTemporary(this, pdfUri) },
            onSuccessDismiss = { name -> saveScannedPdf(pdfUri, name) }
        ) { name ->
            if (name.isBlank()) {
                Toast.makeText(this, R.string.file_name_empty, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    private fun saveScannedPdf(pdfUri: Uri, name: String) {
        val loadingDialog = TaskLoadingDialog.newInstance(R.string.loading)
        loadingDialog.show(supportFragmentManager, "create_pdf_loading")
        val loadingStartedAt = PdfTaskDelay.startedAt()
        lifecycleScope.launch {
            val document = CreatedPdfStore.save(this@MainActivity, pdfUri, name)
            PdfTaskDelay.waitUntilSatisfied(loadingStartedAt)
            CreatedPdfStore.deleteTemporary(this@MainActivity, pdfUri)
            if (document == null) {
                loadingDialog.dismissAllowingStateLoss()
                Toast.makeText(this@MainActivity, R.string.create_pdf_failed, Toast.LENGTH_SHORT)
                    .show()
                return@launch
            }
            refreshDocuments(forcePermissionCheck = false)
            finishProcessingWithAd(loadingDialog, loadingStartedAt) {
                startActivity(PdfCreateResultActivity.intent(this@MainActivity, document))
            }
        }
    }

    private fun finishProcessingWithAd(
        loadingDialog: TaskLoadingDialog,
        loadingStartedAt: Long,
        next: () -> Unit
    ) {
        // 广告展示前先关闭加载弹窗，广告关闭/失败后继续跳转。
        loadingDialog.dismissAllowingStateLoss()
        if (BlockUtils.shouldBlockAds(this)) {
            next()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { next() },
            onFailed = { next() },
            trackingScene = AdTrackingScene.SCAN_INTERSTITIAL
        )
    }

    private fun actionItem(iconRes: Int, labelRes: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            setOnClickListener { onClick() }
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
            })
            addView(TextView(context).apply {
                setText(labelRes)
                setTextColor(getColor(R.color.text_primary))
                textSize = 12f
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 6.dp()
                }
            })
        }
    }

    private fun showRenameDialog(document: LocalDocument) {
        TextInputDialogFragment.newInstance(
            title = getString(R.string.rename).uppercase(),
            defaultValue = document.name.substringBeforeLast('.', document.name),
            hint = getString(R.string.file_name),
            message = null,
            password = false,
            confirmLabelRes = R.string.save
        ).apply {
            onConfirm = { text, handled ->
                val result = documentLibrary.rename(document, text)
                if (result.document == null) {
                    Toast.makeText(
                        this@MainActivity,
                        result.errorRes ?: R.string.rename_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    handled(false)
                } else {
                    applyRenamedDocument(document.path, result.document)
                    handled(true)
                }
            }
        }.show(supportFragmentManager, "rename_dialog")
    }

    private fun showDeleteDialog(document: LocalDocument) {
        val titleRes =
            if (activeSection == HomeSection.RECENT) R.string.remove_recent_title else R.string.delete_title
        val messageRes =
            if (activeSection == HomeSection.RECENT) R.string.remove_recent_message else R.string.delete_message
        ConfirmDialogFragment.newInstance(
            titleRes = titleRes,
            messageRes = messageRes,
            confirmLabelRes = R.string.delete
        ).apply {
            onConfirm = {
                if (activeSection == HomeSection.RECENT) {
                    documentLibrary.removeRecent(document)
                    recentDocuments = recentDocuments.filterNot { it.path == document.path }
                    if (canReadDocuments()) {
                        submitDocumentSections(hasPermission = true)
                    }
                    refreshDocuments(forcePermissionCheck = false)
                    true
                } else {
                    val deleted = documentLibrary.delete(document)
                    if (deleted) {
                        refreshDocuments(forcePermissionCheck = false)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.delete_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    deleted
                }
            }
        }.show(supportFragmentManager, "delete_dialog")
    }

    private fun shareDocument(document: LocalDocument) {
        runCatching {
            val uri =
                FileProvider.getUriForFile(this, "$packageName.fileProvider", File(document.path))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = document.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            markExternalFlowSkip()
            externalActivityLauncher.launch(Intent.createChooser(intent, getString(R.string.share)))
        }.onFailure {
            clearExternalFlowSkip()
            Toast.makeText(this, R.string.share_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun canReadDocuments(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions().all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun requestDocumentPermission(afterGrantedIntent: Intent? = null) {
        pendingAfterPermissionIntent = afterGrantedIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            markExternalFlowSkip()
            runCatching { documentSettingsLauncher.launch(intent) }.onFailure {
                clearExternalFlowSkip()
                markExternalFlowSkip()
                runCatching {
                    documentSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }.onFailure {
                    clearExternalFlowSkip()
                }
            }
            watchPermissionGrant()
        } else {
            legacyPermissionLauncher.launch(legacyPermissions())
        }
    }

    private fun watchPermissionGrant() {
        permissionWatcher?.cancel()
        permissionWatcher = lifecycleScope.launch(Dispatchers.IO) {
            while (canReadDocuments().not()) {
                delay(200L)
            }
            withContext(Dispatchers.Main) {
                clearExternalFlowSkip()
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                )
                refreshDocuments(forcePermissionCheck = false)
                binding.root.postDelayed({ openPendingAfterPermission() }, 220L)
            }
        }
    }

    private fun openPendingAfterPermission() {
        if (canReadDocuments().not()) return
        val pendingIntent = pendingAfterPermissionIntent ?: return
        pendingAfterPermissionIntent = null
        startActivity(pendingIntent)
    }

    private fun legacyPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private fun markExternalFlowSkip() {
        (application as? App)?.skipNextHotStart()
    }

    private fun clearExternalFlowSkip() {
        (application as? App)?.clearHotStartSkip()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private data class DocumentSnapshot(
        val home: List<LocalDocument>,
        val recent: List<LocalDocument>,
        val favorites: List<LocalDocument>
    )

    private var notificationGuideShownThisProcess = false

    private companion object {
        private const val TAG_OVERLAY_PERMISSION = "overlay_permission"
        private const val TAG_NOTIFICATION_PERMISSION = "notification_permission"
        private const val HOME_PERMISSION_RETRY_MILLIS = 300L
    }


    private fun checkRateUsDialogShow() {
        runCatching {
            if (BlockUtils.shouldBlockAds(this)) {
                return
            }
            if (rateValue == 0) {//没有评分的用户
                if (!lastRateShowTime.isToday()) {
                    RateUsDialogFragment().show(supportFragmentManager, "rate_dialog")
                }
            }
        }
    }

    //是否是当天
    fun Long.isToday(): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.timeInMillis = this
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

}

package com.ysdc.aidpdf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.ysdc.aidpdf.App
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.databinding.ActivitySettingsBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.language.LanguageActivity

class SettingsActivity : BaseActivity<ActivitySettingsBinding>(ActivitySettingsBinding::inflate) {

    private val externalActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        (application as? App)?.clearHotStartSkip()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { finishWithBackMainAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        binding.versionText.text = getString(R.string.app_version, currentVersionName())
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.shareAppRow.setOnClickListener { shareApp() }
        binding.languageRow.setOnClickListener { startActivity(Intent(this, LanguageActivity::class.java)) }
        binding.privacyRow.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }

    private fun finishWithBackMainAd() {
        if (isFinishing || isDestroyed) return
        if (BlockUtils.shouldBlockAds(this)) {
            finish()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { finish() },
            onFailed = { finish() }
        )
    }

    private fun shareApp() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name))
        }
        runCatching {
            (application as? App)?.skipNextHotStart()
            externalActivityLauncher.launch(Intent.createChooser(sendIntent, getString(R.string.share_app)))
        }.onFailure {
            (application as? App)?.clearHotStartSkip()
            Toast.makeText(this, R.string.share_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }

}

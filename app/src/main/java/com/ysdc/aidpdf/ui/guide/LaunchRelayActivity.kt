package com.ysdc.aidpdf.ui.guide

import android.content.Intent
import android.os.Bundle
import com.ysdc.aidpdf.databinding.ActivityLaunchLoadingBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity

class LaunchRelayActivity : BaseActivity<ActivityLaunchLoadingBinding>(ActivityLaunchLoadingBinding::inflate) {

    override fun setupViews(savedInstanceState: Bundle?) {
        forwardToLoading(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardToLoading(intent)
    }

    private fun forwardToLoading(source: Intent?) {
        startActivity(Intent(this, LaunchLoadingActivity::class.java).apply {
            source?.extras?.let(::putExtras)
            putExtra(LaunchLoadingActivity.EXTRA_FORCE_OPEN_AD, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }
}

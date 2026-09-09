package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.data.document.DocumentLibrary
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ActivityPdfCreateResultBinding
import com.ysdc.aidpdf.ui.MainActivity
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.reader.PdfPreviewActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfCreateResultActivity : BaseActivity<ActivityPdfCreateResultBinding>(ActivityPdfCreateResultBinding::inflate) {

    private var createdDocument: LocalDocument? = null
    private var resultNativeAdHandle: AdDisplayHandle? = null
    private var leaving = false

    override fun setupViews(savedInstanceState: Bundle?) {
        val document = intent.toDocumentOrNull()
        if (document == null || File(document.path).exists().not()) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        createdDocument = document
        onBackPressedDispatcher.addCallback(this) { goHomeWithAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        binding.fileName.text = document.name
        binding.fileMeta.text = documentMeta(document)
        showResultNative()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.openButton.setOnClickListener { openCreatedPdf() }
        binding.homeButton.setOnClickListener { goHomeWithAd() }
    }

    override fun onDestroy() {
        resultNativeAdHandle?.destroy()
        resultNativeAdHandle = null
        super.onDestroy()
    }

    private fun openCreatedPdf() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            openCreatedPdfAfterAd()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { openCreatedPdfAfterAd() },
            onFailed = { openCreatedPdfAfterAd() }
        )
    }

    private fun openCreatedPdfAfterAd() {
        val document = createdDocument ?: return
        DocumentLibrary(this).markOpened(document)
        startActivity(PdfPreviewActivity.intent(this, document))
        finish()
    }

    private fun goHomeWithAd() {
        if (leaving) return
        leaving = true
        if (BlockUtils.shouldBlockAds(this)) {
            goHome()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.BackInterstitial,
            activity = this,
            onClosed = { goHome() },
            onFailed = { goHome() }
        )
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }

    private fun showResultNative() {
        resultNativeAdHandle?.destroy()
        resultNativeAdHandle = Ads.showNative(
            scene = AdsScene.ResultNative,
            activity = this,
            parent = binding.resultNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Medium),
            onShown = {
                Log.d("PdfCreateResultActivity", "结果页原生广告展示成功")
                binding.resultNativeAdContainer.isVisible = true
                binding.resultNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("PdfCreateResultActivity", "结果页原生广告曝光")
            },
            onFailed = {
                Log.w("PdfCreateResultActivity", "结果页原生广告展示失败：message=${it.message}")
                binding.resultNativeAdContainer.removeAllViews()
                binding.resultNativeAdContainer.isVisible = false
                binding.resultNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }

    private fun documentMeta(document: LocalDocument): String {
        val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(document.modifiedAt))
        return "$date ${Formatter.formatFileSize(this, document.size)}"
    }

    private fun Intent.toDocumentOrNull(): LocalDocument? {
        val path = getStringExtra(EXTRA_PATH).orEmpty()
        if (path.isBlank()) return null
        val file = File(path)
        return LocalDocument(
            name = getStringExtra(EXTRA_NAME).orEmpty().ifBlank { file.name },
            path = path,
            mimeType = getStringExtra(EXTRA_MIME).orEmpty().ifBlank { "application/pdf" },
            size = file.length().takeIf { it > 0L } ?: getLongExtra(EXTRA_SIZE, 0L),
            modifiedAt = file.lastModified().takeIf { it > 0L } ?: getLongExtra(EXTRA_MODIFIED, 0L)
        )
    }

    companion object {
        private const val EXTRA_NAME = "pdf_result_name"
        private const val EXTRA_PATH = "pdf_result_path"
        private const val EXTRA_MIME = "pdf_result_mime"
        private const val EXTRA_SIZE = "pdf_result_size"
        private const val EXTRA_MODIFIED = "pdf_result_modified"

        fun intent(context: Context, document: LocalDocument): Intent {
            return Intent(context, PdfCreateResultActivity::class.java).apply {
                putExtra(EXTRA_NAME, document.name)
                putExtra(EXTRA_PATH, document.path)
                putExtra(EXTRA_MIME, document.mimeType)
                putExtra(EXTRA_SIZE, document.size)
                putExtra(EXTRA_MODIFIED, document.modifiedAt)
            }
        }
    }
}

package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.addCallback
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.config.AdScene
import com.ysdc.aidpdf.ad.core.AdLease
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.ad.gate.NativeAdGate
import com.ysdc.aidpdf.ad.google.NativeAdSize
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
    private var resultNativeAdLease: AdLease? = null

    override fun setupViews(savedInstanceState: Bundle?) {
        val document = intent.toDocumentOrNull()
        if (document == null || File(document.path).exists().not()) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        createdDocument = document
        onBackPressedDispatcher.addCallback(this) { goHomeWithAd() }
        InterstitialAdGate.prepareBackMain(this)
        InterstitialAdGate.prepare(this, AdScene.CheckInterstitial)
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
        resultNativeAdLease?.release()
        resultNativeAdLease = null
        super.onDestroy()
    }

    private fun openCreatedPdf() {
        InterstitialAdGate.showForClickThenContinue(
            activity = this,
            scene = AdScene.CheckInterstitial
        ) {
            openCreatedPdfAfterAd()
        }
    }

    private fun openCreatedPdfAfterAd() {
        val document = createdDocument ?: return
        DocumentLibrary(this).markOpened(document)
        startActivity(PdfPreviewActivity.intent(this, document))
        finish()
    }

    private fun goHomeWithAd() {
        InterstitialAdGate.showForBackMainThenContinue(this) {
            goHome()
        }
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
        NativeAdGate.showWhenReady(
            activity = this,
            parent = binding.resultNativeAdContainer,
            scene = AdScene.ResultNative,
            size = NativeAdSize.Medium,
            onShown = { lease ->
                resultNativeAdLease?.release()
                resultNativeAdLease = lease
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

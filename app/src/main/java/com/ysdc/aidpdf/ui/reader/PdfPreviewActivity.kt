package com.ysdc.aidpdf.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ActivityPdfPreviewBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity
import com.ysdc.aidpdf.ui.pdf.InputDialogs
import com.ysdc.aidpdf.ui.pdf.PdfPasswordInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPreviewActivity : BaseActivity<ActivityPdfPreviewBinding>(ActivityPdfPreviewBinding::inflate) {

    private lateinit var document: LocalDocument
    private var passwordPromptShown = false

    override fun setupViews(savedInstanceState: Bundle?) {
        document = intent.toDocument()
        if (File(document.path).exists().not()) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { finishWithBackMainAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        binding.titleText.text = document.name
        preparePasswordThenLoad()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
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

    private fun loadPdf(password: String?) {
        val configurator = binding.pdfView.fromFile(File(document.path))
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAnnotationRendering(true)
            .scrollHandle(DefaultScrollHandle(this))
            .enableAntialiasing(true)
            .spacing(3)
            .autoSpacing(false)
            .pageFitPolicy(FitPolicy.WIDTH)
            .fitEachPage(false)
            .pageSnap(false)
            .pageFling(false)
            .nightMode(false)
            .onError {
                if (passwordPromptShown) {
                    Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
                } else {
                    showPasswordDialog()
                }
            }
        if (password.isNullOrBlank().not()) {
            configurator.password(password)
        }
        configurator.load()
    }

    private fun preparePasswordThenLoad() {
        if (PdfPasswordInspector.needsPassword(document.path)) {
            showPasswordDialog()
        } else {
            loadPdf(password = null)
        }
    }

    private fun showPasswordDialog() {
        passwordPromptShown = true
        InputDialogs.passwordAsync(
            activity = this,
            title = getString(R.string.pdf_password_title),
            hint = getString(R.string.pdf_password_hint),
            onCancel = { finish() },
            onConfirm = { password, handled ->
                if (password.isBlank()) {
                    Toast.makeText(this, R.string.pdf_password_empty, Toast.LENGTH_SHORT).show()
                    handled(false)
                    return@passwordAsync
                }
                lifecycleScope.launch {
                    val valid = withContext(Dispatchers.IO) {
                        PdfPasswordInspector.verifyFast(this@PdfPreviewActivity, document.path, password)
                    }
                    if (valid) {
                        binding.pdfView.recycle()
                        loadPdf(password)
                        handled(true)
                    } else {
                        Toast.makeText(this@PdfPreviewActivity, R.string.pdf_password_incorrect, Toast.LENGTH_SHORT).show()
                        handled(false)
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        binding.pdfView.recycle()
        super.onDestroy()
    }

    private fun Intent.toDocument(): LocalDocument {
        return LocalDocument(
            name = getStringExtra(EXTRA_NAME).orEmpty(),
            path = getStringExtra(EXTRA_PATH).orEmpty(),
            mimeType = getStringExtra(EXTRA_MIME).orEmpty(),
            size = getLongExtra(EXTRA_SIZE, 0L),
            modifiedAt = getLongExtra(EXTRA_MODIFIED, 0L),
            openedAt = getLongExtra(EXTRA_OPENED, 0L),
            favorite = getBooleanExtra(EXTRA_FAVORITE, false),
            favoriteAt = getLongExtra(EXTRA_FAVORITE_AT, 0L)
        )
    }

    companion object {
        private const val EXTRA_NAME = "document_name"
        private const val EXTRA_PATH = "document_path"
        private const val EXTRA_MIME = "document_mime"
        private const val EXTRA_SIZE = "document_size"
        private const val EXTRA_MODIFIED = "document_modified"
        private const val EXTRA_OPENED = "document_opened"
        private const val EXTRA_FAVORITE = "document_favorite"
        private const val EXTRA_FAVORITE_AT = "document_favorite_at"

        fun intent(context: Context, document: LocalDocument): Intent {
            return Intent(context, PdfPreviewActivity::class.java).apply {
                putExtra(EXTRA_NAME, document.name)
                putExtra(EXTRA_PATH, document.path)
                putExtra(EXTRA_MIME, document.mimeType)
                putExtra(EXTRA_SIZE, document.size)
                putExtra(EXTRA_MODIFIED, document.modifiedAt)
                putExtra(EXTRA_OPENED, document.openedAt)
                putExtra(EXTRA_FAVORITE, document.favorite)
                putExtra(EXTRA_FAVORITE_AT, document.favoriteAt)
            }
        }
    }
}

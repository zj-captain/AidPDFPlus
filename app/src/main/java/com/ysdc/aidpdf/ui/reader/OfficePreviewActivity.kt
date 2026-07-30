package com.ysdc.aidpdf.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.FileProvider
import com.seapeak.docviewer.DocViewerFragment
import com.seapeak.docviewer.config.DocConfig
import com.seapeak.docviewer.config.DocType
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.data.document.DocumentKind
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ActivityOfficePreviewBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity
import java.io.File

class OfficePreviewActivity : BaseActivity<ActivityOfficePreviewBinding>(ActivityOfficePreviewBinding::inflate) {

    private lateinit var document: LocalDocument

    override fun setupViews(savedInstanceState: Bundle?) {
        document = intent.toDocument()
        if (File(document.path).exists().not()) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { finishWithBackMainAd() }
        InterstitialAdGate.prepareBackMain(this)
        binding.titleText.text = document.name
        showDocument()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun finishWithBackMainAd() {
        InterstitialAdGate.showForBackMainThenContinue(this) {
            finish()
        }
    }

    private fun showDocument() {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileProvider", File(document.path))
        val docType = when (document.kind) {
            DocumentKind.WORD -> DocType.WORD
            DocumentKind.EXCEL -> DocType.EXCEL
            DocumentKind.PPT -> DocType.PPT
            DocumentKind.PDF -> DocType.PDF
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.officeHost, DocViewerFragment(DocConfig(uri.toString(), docType)))
            .commit()
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
            return Intent(context, OfficePreviewActivity::class.java).apply {
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

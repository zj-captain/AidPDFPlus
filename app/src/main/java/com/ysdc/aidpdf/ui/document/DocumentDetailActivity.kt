package com.ysdc.aidpdf.ui.document

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.addCallback
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ad.gate.InterstitialAdGate
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ActivityDocumentDetailBinding
import com.ysdc.aidpdf.databinding.ViewDetailRowBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentDetailActivity : BaseActivity<ActivityDocumentDetailBinding>(ActivityDocumentDetailBinding::inflate) {

    private lateinit var document: LocalDocument

    override fun setupViews(savedInstanceState: Bundle?) {
        document = intent.toDocument()
        onBackPressedDispatcher.addCallback(this) { finishWithBackMainAd() }
        InterstitialAdGate.prepareBackMain(this)
        renderDetails()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.confirmButton.setOnClickListener { finishWithBackMainAd() }
    }

    private fun finishWithBackMainAd() {
        InterstitialAdGate.showForBackMainThenContinue(this) {
            finish()
        }
    }

    private fun renderDetails() {
        bindRow(
            row = binding.nameRow,
            iconRes = R.drawable.img_information,
            title = getString(R.string.file_name),
            value = document.name
        )
        bindRow(
            row = binding.sizeRow,
            iconRes = R.drawable.img_file_size,
            title = getString(R.string.file_size),
            value = Formatter.formatFileSize(this, document.size)
        )
        bindRow(
            row = binding.timeRow,
            iconRes = R.drawable.img_modify_time,
            title = getString(R.string.modified_time),
            value = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(document.modifiedAt))
        )
        bindRow(
            row = binding.pathRow,
            iconRes = R.drawable.img_file_location,
            title = getString(R.string.storage_path),
            value = document.path
        )
    }

    private fun bindRow(row: ViewDetailRowBinding, iconRes: Int, title: String, value: String) {
        row.rowIcon.setImageResource(iconRes)
        row.rowTitle.text = title
        row.rowValue.text = value
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
            return Intent(context, DocumentDetailActivity::class.java)
                .putDocument(document)
        }

        fun Intent.putDocument(document: LocalDocument): Intent {
            putExtra(EXTRA_NAME, document.name)
            putExtra(EXTRA_PATH, document.path)
            putExtra(EXTRA_MIME, document.mimeType)
            putExtra(EXTRA_SIZE, document.size)
            putExtra(EXTRA_MODIFIED, document.modifiedAt)
            putExtra(EXTRA_OPENED, document.openedAt)
            putExtra(EXTRA_FAVORITE, document.favorite)
            putExtra(EXTRA_FAVORITE_AT, document.favoriteAt)
            return this
        }
    }
}

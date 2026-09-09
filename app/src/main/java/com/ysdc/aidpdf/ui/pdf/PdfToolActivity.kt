package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ysdc.aidpdf.R
import com.ysdc.aidpdf.ads.Ads
import com.ysdc.aidpdf.ads.config.AdsScene
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import com.ysdc.aidpdf.ads.model.NativeAdStyle
import com.ysdc.aidpdf.ads.model.NativeRenderRequest
import com.ysdc.aidpdf.core.block.BlockUtils
import com.ysdc.aidpdf.data.document.DocumentKind
import com.ysdc.aidpdf.data.document.DocumentLibrary
import com.ysdc.aidpdf.data.document.LocalDocument
import com.ysdc.aidpdf.databinding.ActivityPdfToolBinding
import com.ysdc.aidpdf.ui.basic.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class PdfToolActivity : BaseActivity<ActivityPdfToolBinding>(ActivityPdfToolBinding::inflate) {

    private val mode: PdfToolMode by lazy { PdfToolMode.from(intent.getStringExtra(EXTRA_MODE)) }
    private val sourceAdapter by lazy { PdfSourceAdapter(mode == PdfToolMode.MERGE) { updateActionState() } }
    private val pageAdapter by lazy { PdfPageAdapter(lifecycleScope) { updateActionState() } }
    private val preselectedPath by lazy { intent.getStringExtra(EXTRA_PATH) }
    private var splitDocument: LocalDocument? = null
    private var splitPassword = ""
    private var scrolledToPreselected = false
    private var nativeAdHandle: AdDisplayHandle? = null

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this) { finishWithBackMainAd() }
        Ads.load(AdsScene.BackInterstitial, this)
        Ads.load(AdsScene.ResultInterstitial, this)
        Ads.load(AdsScene.MainNative, this)
        binding.titleText.setText(mode.titleRes)
        binding.actionButton.setText(mode.actionRes)
        binding.itemList.itemAnimator = null
        binding.itemList.adapter = sourceAdapter
        showNativeAd()
        loadPdfSources()
    }

    override fun bindActions() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.actionButton.setOnClickListener { handleAction() }
    }

    override fun onDestroy() {
        nativeAdHandle?.destroy()
        nativeAdHandle = null
        binding.pdfToolNativeAdContainer.removeAllViews()
        binding.pdfToolNativeAdContainer.isVisible = false
        super.onDestroy()
    }

    private fun showNativeAd() {
        nativeAdHandle?.destroy()
        nativeAdHandle = Ads.showNative(
            scene = AdsScene.MainNative,
            activity = this,
            parent = binding.pdfToolNativeAdContainer,
            request = NativeRenderRequest(style = NativeAdStyle.Tiny),
            onShown = {
                Log.d("PdfToolActivity", "工具页原生广告展示成功")
                binding.pdfToolNativeAdContainer.isVisible = true
                binding.pdfToolNativeAdContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            },
            onImpression = {
                Log.d("PdfToolActivity", "工具页原生广告曝光")
            },
            onFailed = {
                Log.w("PdfToolActivity", "工具页原生广告展示失败：message=${it.message}")
                binding.pdfToolNativeAdContainer.removeAllViews()
                binding.pdfToolNativeAdContainer.isVisible = false
                binding.pdfToolNativeAdContainer.updateLayoutParams { height = 0 }
            }
        )
    }

    private fun loadPdfSources() {
        splitDocument = null
        splitPassword = ""
        binding.hintText.setText(mode.hintRes)
        binding.actionButton.setText(mode.actionRes)
        binding.loadingText.setText(R.string.loading_documents)
        binding.loadingGroup.isVisible = true
        binding.emptyGroup.isVisible = false
        binding.itemList.isVisible = false
        binding.itemList.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                loadAcceptedPdfSources()
            }
            binding.loadingGroup.isVisible = false
            binding.itemList.adapter = sourceAdapter
            binding.itemList.isVisible = files.isNotEmpty()
            binding.emptyGroup.isVisible = files.isEmpty()
            binding.emptyText.setText(mode.emptyRes)
            sourceAdapter.submit(files, preselectedPath)
            scrollToPreselected(files)
        }
    }

    private suspend fun loadAcceptedPdfSources(): List<LocalDocument> {
        val pdfs = DocumentLibrary(this)
            .homeDocuments()
            .filter { it.kind == DocumentKind.PDF }
        if (mode == PdfToolMode.MERGE || mode == PdfToolMode.SPLIT) return pdfs
        return filterBySecurityState(pdfs)
    }

    private suspend fun filterBySecurityState(documents: List<LocalDocument>): List<LocalDocument> = coroutineScope {
        val gate = Semaphore(PDF_SECURITY_CHECK_PARALLELISM)
        documents
            .map { document ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        document.takeIf { mode.accepts(it) }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private fun scrollToPreselected(files: List<LocalDocument>) {
        val targetPath = preselectedPath?.takeIf { it.isNotBlank() } ?: return
        if (scrolledToPreselected) return
        val targetIndex = files.indexOfFirst { it.path == targetPath }
        if (targetIndex < 0) return
        scrolledToPreselected = true
        binding.itemList.post {
            val manager = binding.itemList.layoutManager as? LinearLayoutManager
            manager?.scrollToPositionWithOffset(targetIndex, 12)
                ?: binding.itemList.scrollToPosition(targetIndex)
        }
    }

    private fun updateActionState() {
        val selectedCount = if (binding.itemList.adapter == pageAdapter) {
            pageAdapter.selected().size
        } else {
            sourceAdapter.selected().size
        }
        when {
            mode == PdfToolMode.MERGE && binding.itemList.adapter == sourceAdapter -> {
                binding.selectionText.isVisible = true
                binding.selectionText.text = getString(
                    R.string.merge_selection_count,
                    selectedCount,
                    PdfSourceAdapter.MAX_SELECTED_COUNT
                )
            }
            mode == PdfToolMode.SPLIT && binding.itemList.adapter == pageAdapter -> {
                binding.selectionText.isVisible = true
                binding.selectionText.text = getString(R.string.split_selected_count, selectedCount, pageAdapter.totalCount())
            }
            else -> {
                binding.selectionText.isVisible = false
                binding.selectionText.text = ""
            }
        }
        val enabled = when (mode) {
            PdfToolMode.MERGE -> selectedCount >= 2
            PdfToolMode.SPLIT -> selectedCount >= 1
            PdfToolMode.LOCK, PdfToolMode.UNLOCK -> selectedCount == 1
        }
        binding.actionButton.isEnabled = enabled
        binding.actionButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun handleAction() {
        when (mode) {
            PdfToolMode.MERGE -> requestMergeName()
            PdfToolMode.SPLIT -> handleSplitStep()
            PdfToolMode.LOCK -> requestLockPassword()
            PdfToolMode.UNLOCK -> requestUnlockPassword()
        }
    }

    private fun requestMergeName() {
        val selected = sourceAdapter.selected()
        if (selected.size < 2) {
            Toast.makeText(this, R.string.select_two_pdf, Toast.LENGTH_SHORT).show()
            return
        }
        InputDialogs.fileName(
            activity = this,
            title = getString(R.string.merge_pdf),
            defaultValue = PdfWorkStore.defaultMergeName(),
            onSuccessDismiss = { name -> preparePasswordsForMerge(selected, name) }
        ) { name ->
            if (name.isBlank()) {
                Toast.makeText(this, R.string.file_name_empty, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    private fun preparePasswordsForMerge(files: List<LocalDocument>, outputName: String) {
        val passwords = linkedMapOf<String, String>()
        continueMergePasswordFlow(files, outputName, passwords, 0)
    }

    private fun continueMergePasswordFlow(
        files: List<LocalDocument>,
        outputName: String,
        passwords: MutableMap<String, String>,
        index: Int
    ) {
        if (index >= files.size) {
            runMerge(files, outputName, passwords)
            return
        }
        val file = files[index]
        lifecycleScope.launch {
            val locked = withContext(Dispatchers.IO) {
                PdfPasswordInspector.needsPassword(file.path)
            }
            if (locked.not()) {
                continueMergePasswordFlow(files, outputName, passwords, index + 1)
                return@launch
            }
            requestMergePassword(files, outputName, passwords, index)
        }
    }

    private fun requestMergePassword(
        files: List<LocalDocument>,
        outputName: String,
        passwords: MutableMap<String, String>,
        index: Int
    ) {
        val file = files[index]
        InputDialogs.passwordAsync(
            activity = this,
            title = getString(R.string.pdf_password_title),
            hint = getString(R.string.pdf_password_hint),
            message = getString(R.string.pdf_password_file_message, file.name),
            onCancel = {
                Toast.makeText(this, R.string.merge_pdf_failed, Toast.LENGTH_SHORT).show()
            }
        ) { password, handled ->
            if (password.isBlank()) {
                Toast.makeText(this, R.string.pdf_password_empty, Toast.LENGTH_SHORT).show()
                handled(false)
                return@passwordAsync
            }
            lifecycleScope.launch {
                val valid = withContext(Dispatchers.IO) {
                    PdfPasswordInspector.verifyFast(this@PdfToolActivity, file.path, password)
                }
                if (valid) {
                    passwords[file.path] = password
                    handled(true)
                    continueMergePasswordFlow(files, outputName, passwords, index + 1)
                } else {
                    Toast.makeText(this@PdfToolActivity, R.string.pdf_password_incorrect, Toast.LENGTH_SHORT).show()
                    handled(false)
                }
            }
        }
    }

    private fun runMerge(files: List<LocalDocument>, outputName: String, passwords: Map<String, String>) {
        val loadingDialog = showTaskLoading()
        val loadingStartedAt = PdfTaskDelay.startedAt()
        lifecycleScope.launch {
            val result = PdfWorkStore.merge(this@PdfToolActivity, files, outputName, passwords)
            PdfTaskDelay.waitUntilSatisfied(loadingStartedAt)
            when (result) {
                is PdfWorkStore.MergeOutcome.Success -> {
                    finishProcessingWithAd(loadingDialog, loadingStartedAt) {
                        openResultPage(result.file.toDocument())
                    }
                }
                PdfWorkStore.MergeOutcome.TooLarge -> {
                    loadingDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@PdfToolActivity, R.string.merge_pdf_too_large, Toast.LENGTH_SHORT).show()
                }
                PdfWorkStore.MergeOutcome.Failed -> {
                    loadingDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@PdfToolActivity, R.string.merge_pdf_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleSplitStep() {
        if (binding.itemList.adapter == pageAdapter) {
            requestSplitName()
            return
        }
        val selected = sourceAdapter.selected().firstOrNull()
        if (selected == null) {
            Toast.makeText(this, R.string.select_pdf_first, Toast.LENGTH_SHORT).show()
            return
        }
        prepareSplitPages(selected)
    }

    private fun prepareSplitPages(document: LocalDocument) {
        splitDocument = document
        fun loadPages(password: String) {
            binding.loadingText.setText(R.string.loading_pages)
            binding.loadingGroup.isVisible = true
            binding.itemList.isVisible = false
            binding.emptyGroup.isVisible = false
            lifecycleScope.launch {
                val count = PdfFastAccess.pageCount(this@PdfToolActivity, document.path, password)
                binding.loadingGroup.isVisible = false
                if (count == null || count <= 0 || count == 1) {
                    Toast.makeText(
                        this@PdfToolActivity,
                        if (count == 1) R.string.one_page_tips else R.string.split_pdf_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    loadPdfSources()
                    return@launch
                }
                splitPassword = password
                binding.hintText.setText(R.string.split_page_hint)
                binding.actionButton.setText(R.string.split_pdf)
                binding.itemList.adapter = pageAdapter
                binding.itemList.layoutManager = GridLayoutManager(this@PdfToolActivity, 2)
                binding.emptyGroup.isVisible = false
                binding.itemList.isVisible = true
                pageAdapter.submit(count, document.path, password)
            }
        }
        lifecycleScope.launch {
            val locked = withContext(Dispatchers.IO) {
                PdfPasswordInspector.needsPassword(document.path)
            }
            if (locked) {
                requestSplitPassword(document) { password -> loadPages(password) }
            } else {
                loadPages("")
            }
        }
    }

    private fun requestSplitPassword(document: LocalDocument, onReady: (String) -> Unit) {
        InputDialogs.passwordAsync(
            activity = this,
            title = getString(R.string.pdf_password_title),
            hint = getString(R.string.pdf_password_hint),
            message = getString(R.string.pdf_password_file_message, document.name)
        ) { password, handled ->
            if (password.isBlank()) {
                Toast.makeText(this, R.string.pdf_password_empty, Toast.LENGTH_SHORT).show()
                handled(false)
                return@passwordAsync
            }
            lifecycleScope.launch {
                val valid = withContext(Dispatchers.IO) {
                    PdfPasswordInspector.verifyFast(this@PdfToolActivity, document.path, password)
                }
                if (valid) {
                    handled(true)
                    onReady(password)
                } else {
                    Toast.makeText(this@PdfToolActivity, R.string.pdf_password_incorrect, Toast.LENGTH_SHORT).show()
                    handled(false)
                }
            }
        }
    }

    private fun requestSplitName() {
        val document = splitDocument ?: return
        val pages = pageAdapter.selected()
        if (pages.isEmpty()) {
            Toast.makeText(this, R.string.split_no_page_selected, Toast.LENGTH_SHORT).show()
            return
        }
        InputDialogs.fileName(
            activity = this,
            title = getString(R.string.split_pdf),
            defaultValue = PdfWorkStore.defaultSplitName(),
            onSuccessDismiss = { name -> runSplit(document, name, pages) }
        ) { name ->
            if (name.isBlank()) {
                Toast.makeText(this, R.string.file_name_empty, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    private fun runSplit(document: LocalDocument, outputName: String, pages: List<Int>) {
        val loadingDialog = showTaskLoading()
        val loadingStartedAt = PdfTaskDelay.startedAt()
        lifecycleScope.launch {
            val output = PdfWorkStore.split(this@PdfToolActivity, document, outputName, pages, splitPassword)
            PdfTaskDelay.waitUntilSatisfied(loadingStartedAt)
            if (output == null) {
                loadingDialog.dismissAllowingStateLoss()
                Toast.makeText(this@PdfToolActivity, R.string.split_pdf_failed, Toast.LENGTH_SHORT).show()
            } else {
                finishProcessingWithAd(loadingDialog, loadingStartedAt) {
                    openResultPage(output.toDocument())
                }
            }
        }
    }

    private fun requestLockPassword() {
        val document = sourceAdapter.selected().firstOrNull()
        if (document == null) {
            Toast.makeText(this, R.string.select_pdf_first, Toast.LENGTH_SHORT).show()
            return
        }
        InputDialogs.password(
            activity = this,
            title = getString(R.string.set_pdf_password_title),
            hint = getString(R.string.pdf_password_hint),
            message = getString(R.string.set_pdf_password_message)
        ) { password ->
            if (password.isBlank()) {
                Toast.makeText(this, R.string.pdf_password_empty, Toast.LENGTH_SHORT).show()
                false
            } else {
                runLock(document, password)
                true
            }
        }
    }

    private fun runLock(document: LocalDocument, password: String) {
        val loadingDialog = showTaskLoading()
        val loadingStartedAt = PdfTaskDelay.startedAt()
        lifecycleScope.launch {
            val result = PdfWorkStore.lock(this@PdfToolActivity, document, password)
            PdfTaskDelay.waitUntilSatisfied(loadingStartedAt)
            when (result) {
                PdfWorkStore.PasswordOutcome.Success -> {
                    finishProcessingWithAd(loadingDialog, loadingStartedAt) {
                        Toast.makeText(this@PdfToolActivity, R.string.lock_pdf_success, Toast.LENGTH_SHORT).show()
                        removeProcessedDocument(document)
                    }
                }
                PdfWorkStore.PasswordOutcome.IncorrectPassword,
                PdfWorkStore.PasswordOutcome.Failed -> {
                    loadingDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@PdfToolActivity, R.string.lock_pdf_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestUnlockPassword() {
        val document = sourceAdapter.selected().firstOrNull()
        if (document == null) {
            Toast.makeText(this, R.string.select_pdf_first, Toast.LENGTH_SHORT).show()
            return
        }
        InputDialogs.passwordAsync(
            activity = this,
            title = getString(R.string.unlock_pdf_password_title),
            hint = getString(R.string.pdf_password_hint),
            message = getString(R.string.unlock_pdf_password_message)
        ) { password, handled ->
            if (password.isBlank()) {
                Toast.makeText(this, R.string.pdf_password_empty, Toast.LENGTH_SHORT).show()
                handled(false)
                return@passwordAsync
            }
            lifecycleScope.launch {
                val valid = withContext(Dispatchers.IO) {
                    PdfPasswordInspector.verifyFast(this@PdfToolActivity, document.path, password)
                }
                if (valid) {
                    handled(true)
                    runUnlock(document, password)
                } else {
                    Toast.makeText(this@PdfToolActivity, R.string.pdf_password_incorrect, Toast.LENGTH_SHORT).show()
                    handled(false)
                }
            }
        }
    }

    private fun runUnlock(document: LocalDocument, password: String) {
        val loadingDialog = showTaskLoading()
        val loadingStartedAt = PdfTaskDelay.startedAt()
        lifecycleScope.launch {
            val result = PdfWorkStore.unlock(this@PdfToolActivity, document, password)
            PdfTaskDelay.waitUntilSatisfied(loadingStartedAt)
            when (result) {
                PdfWorkStore.PasswordOutcome.Success -> {
                    finishProcessingWithAd(loadingDialog, loadingStartedAt) {
                        Toast.makeText(this@PdfToolActivity, R.string.unlock_pdf_success, Toast.LENGTH_SHORT).show()
                        removeProcessedDocument(document)
                    }
                }
                PdfWorkStore.PasswordOutcome.IncorrectPassword -> {
                    loadingDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@PdfToolActivity, R.string.pdf_password_incorrect, Toast.LENGTH_SHORT).show()
                }
                PdfWorkStore.PasswordOutcome.Failed -> {
                    loadingDialog.dismissAllowingStateLoss()
                    Toast.makeText(this@PdfToolActivity, R.string.unlock_pdf_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTaskLoading(): TaskLoadingDialog {
        return TaskLoadingDialog.newInstance(R.string.loading).also {
            it.show(supportFragmentManager, "pdf_task_loading")
        }
    }

    private fun finishProcessingWithAd(
        loadingDialog: TaskLoadingDialog,
        loadingStartedAt: Long,
        next: () -> Unit
    ) {
        // 广告展示前先关闭加载弹窗，广告关闭/失败后继续原处理结果跳转。
        loadingDialog.dismissAllowingStateLoss()
        if (BlockUtils.shouldBlockAds(this)) {
            next()
            return
        }
        Ads.showFullScreen(
            scene = AdsScene.ResultInterstitial,
            activity = this,
            onClosed = { next() },
            onFailed = { next() }
        )
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

    private fun removeProcessedDocument(document: LocalDocument) {
        sourceAdapter.remove(document)
        binding.emptyGroup.isVisible = sourceAdapter.isEmpty()
        binding.itemList.isVisible = sourceAdapter.isEmpty().not()
        updateActionState()
    }

    private fun openResultPage(document: LocalDocument) {
        Toast.makeText(this, if (mode == PdfToolMode.MERGE) R.string.merge_pdf_success else R.string.split_pdf_success, Toast.LENGTH_SHORT).show()
        startActivity(PdfCreateResultActivity.intent(this, document))
        finish()
    }

    private fun java.io.File.toDocument(): LocalDocument {
        return with(CreatedPdfStore) { toDocument() }
    }

    enum class PdfToolMode(val titleRes: Int, val hintRes: Int, val emptyRes: Int, val actionRes: Int) {
        MERGE(R.string.merge_pdf, R.string.select_pdf, R.string.no_pdf_files, R.string.merge_pdf),
        SPLIT(R.string.split_pdf, R.string.split_select_pdf_hint, R.string.no_pdf_files, R.string.continue_action),
        LOCK(R.string.lock_pdf, R.string.lock_select_pdf_hint, R.string.no_unlocked_pdf_files, R.string.lock_pdf),
        UNLOCK(R.string.unlock_pdf, R.string.unlock_select_pdf_hint, R.string.no_locked_pdf_files, R.string.unlock_pdf);

        fun accepts(document: LocalDocument): Boolean {
            return when (this) {
                MERGE, SPLIT -> true
                LOCK -> PdfPasswordInspector.canOpenWithoutPassword(document.path)
                UNLOCK -> PdfPasswordInspector.needsPassword(document.path)
            }
        }

        companion object {
            fun from(raw: String?): PdfToolMode = entries.firstOrNull { it.name == raw } ?: MERGE
        }
    }

    companion object {
        private const val PDF_SECURITY_CHECK_PARALLELISM = 4
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, mode: PdfToolMode, preselectedPath: String? = null): Intent {
            return Intent(context, PdfToolActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_PATH, preselectedPath)
        }
    }
}

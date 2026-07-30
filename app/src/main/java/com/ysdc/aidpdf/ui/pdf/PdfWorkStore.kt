package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.os.Environment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.ysdc.aidpdf.data.document.LocalDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfWorkStore {

    private const val OUTPUT_DIR_NAME = "AidPDF"
    private const val PDF_EXTENSION = ".pdf"

    fun defaultMergeName(): String = "AidPDF_Merge_${System.currentTimeMillis()}"
    fun defaultSplitName(): String = "AidPDF_Split_${System.currentTimeMillis()}"

    suspend fun pageCount(context: Context, document: LocalDocument, password: String = ""): Int? = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(File(document.path), password, memory(context)).use { it.numberOfPages }
        }.getOrNull()
    }

    suspend fun merge(
        context: Context,
        documents: List<LocalDocument>,
        outputName: String,
        passwords: Map<String, String>
    ): MergeOutcome = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val targetFile = createOutputFile(outputName) ?: return@withContext MergeOutcome.Failed
            outputFile = targetFile
            val memory = memory(context)
            if (passwords.isEmpty()) {
                PDFMergerUtility().apply {
                    destinationFileName = targetFile.absolutePath
                    setDocumentMergeMode(PDFMergerUtility.DocumentMergeMode.OPTIMIZE_RESOURCES_MODE)
                    documents.forEach { addSource(File(it.path)) }
                    mergeDocuments(memory)
                }
            } else {
                PDDocument(memory).use { target ->
                    val merger = PDFMergerUtility()
                    documents.forEach { document ->
                        PDDocument.load(File(document.path), passwords[document.path].orEmpty(), memory).use { source ->
                            source.isAllSecurityToBeRemoved = true
                            merger.appendDocument(target, source)
                        }
                    }
                    target.save(targetFile)
                }
            }
            CreatedPdfStore.scan(context, targetFile)
            MergeOutcome.Success(targetFile)
        } catch (error: OutOfMemoryError) {
            cleanup(outputFile)
            MergeOutcome.TooLarge
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            cleanup(outputFile)
            MergeOutcome.Failed
        }
    }

    suspend fun split(context: Context, document: LocalDocument, outputName: String, pages: List<Int>, password: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            val outputFile = createOutputFile(outputName) ?: return@withContext null
            PDDocument.load(File(document.path), password, memory(context)).use { source ->
                val cleanPages = pages.distinct().filter { it in 0 until source.numberOfPages }
                if (cleanPages.isEmpty()) return@withContext null
                PDDocument(memory(context)).use { target ->
                    cleanPages.forEach { pageIndex -> target.importPage(source.getPage(pageIndex)) }
                    target.save(outputFile)
                }
            }
            CreatedPdfStore.scan(context, outputFile)
            outputFile
        }.getOrNull()
    }

    suspend fun lock(context: Context, document: LocalDocument, password: String): PasswordOutcome = withContext(Dispatchers.IO) {
        val source = File(document.path)
        val output = temporaryFile(source)
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(source, memory(context)).use { pdf ->
                val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply {
                    encryptionKeyLength = 128
                    isPreferAES = true
                }
                pdf.protect(policy)
                pdf.save(output)
            }
            if (replaceOriginal(source, output)) {
                PdfPasswordInspector.invalidate(document.path)
                CreatedPdfStore.scan(context, source)
                PasswordOutcome.Success
            } else {
                output.delete()
                PasswordOutcome.Failed
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            output.delete()
            PasswordOutcome.Failed
        }
    }

    suspend fun unlock(context: Context, document: LocalDocument, password: String): PasswordOutcome = withContext(Dispatchers.IO) {
        val source = File(document.path)
        val output = temporaryFile(source)
        try {
            if (PdfPasswordInspector.verifyFast(context, document.path, password).not()) {
                return@withContext PasswordOutcome.IncorrectPassword
            }
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(source, password, memory(context)).use { pdf ->
                pdf.isAllSecurityToBeRemoved = true
                pdf.save(output)
            }
            if (replaceOriginal(source, output)) {
                PdfPasswordInspector.invalidate(document.path)
                CreatedPdfStore.scan(context, source)
                PasswordOutcome.Success
            } else {
                output.delete()
                PasswordOutcome.Failed
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            output.delete()
            PasswordOutcome.Failed
        }
    }

    private fun createOutputFile(rawName: String): File? {
        val directory = outputDirectory() ?: return null
        val cleanBase = cleanFileName(rawName).ifBlank { "AidPDF_${System.currentTimeMillis()}" }
        var candidate = File(directory, "$cleanBase$PDF_EXTENSION")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$cleanBase ($index)$PDF_EXTENSION")
            index++
        }
        return candidate
    }

    private fun outputDirectory(): File? {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), OUTPUT_DIR_NAME)
        return directory.takeIf { it.exists() || it.mkdirs() }
    }

    private fun cleanFileName(rawName: String): String {
        return rawName.trim()
            .removeSuffix(PDF_EXTENSION)
            .removeSuffix(PDF_EXTENSION.uppercase())
            .replace(Regex("[\\\\/:*?\"<>|\\x00]"), "_")
            .trim()
    }

    private fun temporaryFile(source: File): File = File(source.parentFile, ".${source.name}.${System.currentTimeMillis()}.tmp")

    private fun replaceOriginal(source: File, output: File): Boolean {
        val backup = File(source.parentFile, ".${source.name}.${System.currentTimeMillis()}.bak")
        if (source.renameTo(backup).not()) return false
        return runCatching {
            output.copyTo(source, overwrite = true)
            output.delete()
            backup.delete()
            true
        }.getOrElse {
            source.delete()
            backup.renameTo(source)
            output.delete()
            false
        }
    }

    private fun cleanup(file: File?) {
        if (file?.exists() == true) file.delete()
    }

    private fun memory(context: Context): MemoryUsageSetting {
        val cacheDir = File(context.cacheDir, "pdfbox").apply { mkdirs() }
        return MemoryUsageSetting.setupTempFileOnly().setTempDir(cacheDir)
    }

    sealed class MergeOutcome {
        data class Success(val file: File) : MergeOutcome()
        data object TooLarge : MergeOutcome()
        data object Failed : MergeOutcome()
    }

    sealed class PasswordOutcome {
        data object Success : PasswordOutcome()
        data object IncorrectPassword : PasswordOutcome()
        data object Failed : PasswordOutcome()
    }
}

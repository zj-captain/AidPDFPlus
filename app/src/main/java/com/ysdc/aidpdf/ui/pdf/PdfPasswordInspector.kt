package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PdfPasswordInspector {

    fun needsPassword(path: String): Boolean {
        return snapshot(path)?.encrypted == true
    }

    fun verify(path: String, password: String): Boolean {
        return runCatching {
            PDDocument.load(File(path), password, MemoryUsageSetting.setupTempFileOnly()).use { true }
        }.getOrDefault(false)
    }

    fun verifyFast(context: Context, path: String, password: String): Boolean {
        return PdfFastAccess.verifyPassword(context, path, password)
    }

    fun canOpenWithoutPassword(path: String): Boolean {
        return snapshot(path)?.let { it.canOpenAsPdf && it.encrypted.not() } == true
    }

    fun invalidate(path: String) {
        securityCache.remove(path)
    }

    private fun snapshot(path: String): SecuritySnapshot? {
        val file = File(path)
        if (file.exists().not() || file.isFile.not() || file.canRead().not()) {
            securityCache.remove(path)
            return null
        }
        val size = file.length()
        val modifiedAt = file.lastModified()
        securityCache[path]
            ?.takeIf { it.size == size && it.modifiedAt == modifiedAt }
            ?.let { return it }

        val canOpenAsPdf = PdfFastAccess.canOpenAsPdf(path)
        val encrypted = canOpenAsPdf && PdfFastAccess.looksEncrypted(path)
        return SecuritySnapshot(
            size = size,
            modifiedAt = modifiedAt,
            canOpenAsPdf = canOpenAsPdf,
            encrypted = encrypted
        ).also { securityCache[path] = it }
    }

    private val securityCache = ConcurrentHashMap<String, SecuritySnapshot>()

    private data class SecuritySnapshot(
        val size: Long,
        val modifiedAt: Long,
        val canOpenAsPdf: Boolean,
        val encrypted: Boolean
    )
}

package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import kotlin.math.min

object PdfFastAccess {

    private const val PDF_HEADER = "%PDF"
    private const val ENCRYPT_MARK = "/Encrypt"
    private const val MAX_SCAN_BYTES = 1024 * 1024

    fun looksEncrypted(path: String): Boolean {
        val file = File(path)
        if (file.exists().not() || file.isFile.not() || file.canRead().not()) return false
        return runCatching {
            file.inputStream().use { input ->
                val size = file.length()
                val firstBytes = ByteArray(min(64 * 1024L, size).toInt())
                val firstRead = input.read(firstBytes)
                if (firstRead > 0 && contains(firstBytes, firstRead, ENCRYPT_MARK)) return true
            }
            file.inputStream().use { input ->
                val skip = (file.length() - MAX_SCAN_BYTES).coerceAtLeast(0L)
                if (skip > 0L) input.skip(skip)
                val tail = input.readBytes()
                contains(tail, tail.size, ENCRYPT_MARK)
            }
        }.getOrDefault(false)
    }

    fun canOpenAsPdf(path: String): Boolean {
        val file = File(path)
        if (file.exists().not() || file.isFile.not() || file.canRead().not()) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(PDF_HEADER.length)
                input.read(header) == header.size && header.decodeToString() == PDF_HEADER
            }
        }.getOrDefault(false)
    }

    fun verifyPassword(context: Context, path: String, password: String): Boolean {
        return openDocument(context, path, password) { core, document ->
            core.getPageCount(document) >= 0
        } ?: false
    }

    fun pageCount(context: Context, path: String, password: String): Int? {
        return openDocument(context, path, password.ifBlank { null }) { core, document ->
            core.getPageCount(document)
        }
    }

    fun renderPage(
        context: Context,
        path: String,
        password: String,
        pageIndex: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        return openDocument(context, path, password.ifBlank { null }) { core, document ->
            val pageCount = core.getPageCount(document)
            if (pageIndex !in 0 until pageCount) return@openDocument null
            core.openPage(document, pageIndex)
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                core.renderPageBitmap(document, bitmap, pageIndex, 0, 0, width, height, true)
            }
        }
    }

    private inline fun <T> openDocument(
        context: Context,
        path: String,
        password: String?,
        action: (PdfiumCore, com.shockwave.pdfium.PdfDocument) -> T
    ): T? {
        val file = File(path)
        if (file.exists().not()) return null
        return runCatching {
            val core = PdfiumCore(context.applicationContext)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val document = try {
                core.newDocument(descriptor, password)
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
            try {
                action(core, document)
            } finally {
                core.closeDocument(document)
            }
        }.getOrNull()
    }

    private fun contains(bytes: ByteArray, length: Int, needle: String): Boolean {
        if (length <= 0 || needle.isEmpty()) return false
        val target = needle.encodeToByteArray()
        if (target.size > length) return false
        for (index in 0..(length - target.size)) {
            var matched = true
            for (offset in target.indices) {
                if (bytes[index + offset] != target[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }
}

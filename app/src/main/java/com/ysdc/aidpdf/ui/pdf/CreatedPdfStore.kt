package com.ysdc.aidpdf.ui.pdf

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.ysdc.aidpdf.data.document.LocalDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CreatedPdfStore {

    fun defaultName(): String = "AidPDF_Create_${System.currentTimeMillis()}"

    suspend fun save(context: Context, sourceUri: Uri, rawName: String): LocalDocument? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = outputDirectory() ?: return@withContext null
            val outputFile = nextFile(directory, rawName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            scan(context, outputFile)
            outputFile.toDocument()
        }.getOrNull()
    }

    fun deleteTemporary(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun outputDirectory(): File? {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AidPDF")
        return directory.takeIf { it.exists() || it.mkdirs() }
    }

    private fun nextFile(directory: File, rawName: String): File {
        val baseName = cleanName(rawName).ifBlank { defaultName() }
        var candidate = File(directory, "$baseName.pdf")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$baseName ($index).pdf")
            index++
        }
        return candidate
    }

    private fun cleanName(rawName: String): String {
        return rawName.trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|\\x00]"), "_")
            .trim()
    }

    fun File.toDocument(): LocalDocument {
        return LocalDocument(
            name = name,
            path = absolutePath,
            mimeType = "application/pdf",
            size = length(),
            modifiedAt = lastModified()
        )
    }

    fun scan(context: Context, file: File) {
        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(file.absolutePath),
            arrayOf("application/pdf"),
            null
        )
    }
}

package com.ysdc.aidpdf.data.document

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.ysdc.aidpdf.R
import java.io.File

class DocumentLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val stateStore = DocumentStateStore()

    fun homeDocuments(): List<LocalDocument> {
        val records = stateStore.records()
        return scanDocuments(appContext.contentResolver)
            .map { document ->
                records[document.path]?.let { record ->
                    document.copy(
                        openedAt = record.openedAt,
                        favorite = record.favorite,
                        favoriteAt = record.favoriteAt
                    )
                } ?: document
            }
            .sortedByDescending { it.modifiedAt }
    }

    fun recentDocuments(): List<LocalDocument> {
        return stateStore.records().values
            .filter { it.openedAt > 0L }
            .mapNotNull { it.toDocumentIfAvailable() }
            .sortedByDescending { it.openedAt }
    }

    fun favoriteDocuments(): List<LocalDocument> {
        return stateStore.records().values
            .filter { it.favorite }
            .mapNotNull { it.toDocumentIfAvailable() }
            .sortedByDescending { it.favoriteAt }
    }

    fun markOpened(document: LocalDocument): LocalDocument {
        val saved = stateStore.find(document.path)
        val updated = document.copy(
            openedAt = System.currentTimeMillis(),
            favorite = saved?.favorite ?: document.favorite,
            favoriteAt = saved?.favoriteAt ?: document.favoriteAt
        )
        stateStore.save(updated.toRecord())
        return updated
    }

    fun toggleFavorite(document: LocalDocument): LocalDocument {
        val saved = stateStore.find(document.path)
        val nextFavorite = saved?.favorite?.not() ?: document.favorite.not()
        val updated = document.copy(
            favorite = nextFavorite,
            favoriteAt = if (nextFavorite) System.currentTimeMillis() else 0L,
            openedAt = saved?.openedAt ?: document.openedAt
        )
        if (updated.openedAt <= 0L && updated.favorite.not()) {
            stateStore.delete(document.path)
        } else {
            stateStore.save(updated.toRecord())
        }
        return updated
    }

    fun removeRecent(document: LocalDocument) {
        stateStore.clearOpened(document.path)
    }

    fun rename(document: LocalDocument, rawName: String): DocumentOperationResult {
        val cleanName = rawName.trim()
        if (cleanName.isBlank()) return DocumentOperationResult(errorRes = R.string.rename_empty)

        val source = File(document.path)
        if (source.exists().not()) return DocumentOperationResult(errorRes = R.string.rename_failed)

        val target = File(source.parentFile, buildTargetName(source.name, cleanName))
        if (target.absolutePath != source.absolutePath && target.exists()) {
            return DocumentOperationResult(errorRes = R.string.rename_exists)
        }
        if (target.absolutePath != source.absolutePath && source.renameTo(target).not()) {
            return DocumentOperationResult(errorRes = R.string.rename_failed)
        }

        val renamed = document.copy(
            name = target.name,
            path = target.absolutePath,
            size = target.length().takeIf { it > 0L } ?: document.size,
            modifiedAt = target.lastModified().takeIf { it > 0L } ?: document.modifiedAt
        )
        val saved = stateStore.find(document.path)
        if (saved != null || renamed.openedAt > 0L || renamed.favorite) {
            stateStore.delete(document.path)
            stateStore.save(renamed.toRecord())
        }
        refreshMedia(document.path, renamed.path)
        return DocumentOperationResult(document = renamed)
    }

    fun delete(document: LocalDocument): Boolean {
        val file = File(document.path)
        val deleted = file.exists().not() || file.delete()
        if (deleted) {
            stateStore.delete(document.path)
            refreshMedia(document.path)
        }
        return deleted
    }

    private fun scanDocuments(resolver: ContentResolver): List<LocalDocument> {
        val documents = queryMediaStore(resolver)
        if (documents.isNotEmpty()) return documents
        return scanPublicDirectories()
    }

    private fun queryMediaStore(resolver: ContentResolver): List<LocalDocument> {
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val mimeArgs = DocumentKind.supportedMimes()
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (${mimeArgs.joinToString(",") { "?" }})"
        val results = mutableListOf<LocalDocument>()
        runCatching {
            resolver.query(
                uri,
                projection,
                selection,
                mimeArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIndex).orEmpty()
                    val file = File(path)
                    if (path.isBlank() || file.exists().not() || file.isDirectory || file.canRead().not()) continue
                    val name = cursor.getString(nameIndex).orEmpty().ifBlank { file.name }
                    if (isSupportedDocument(name, cursor.getString(mimeIndex).orEmpty()).not()) continue
                    results.add(
                        LocalDocument(
                            name = name,
                            path = path,
                            mimeType = cursor.getString(mimeIndex).orEmpty(),
                            size = cursor.getLong(sizeIndex).takeIf { it > 0L } ?: file.length(),
                            modifiedAt = cursor.getLong(modifiedIndex).takeIf { it > 0L }?.times(1000L) ?: file.lastModified()
                        )
                    )
                }
            }
        }
        return results
    }

    private fun scanPublicDirectories(): List<LocalDocument> {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ).distinctBy { it.absolutePath }

        return roots
            .flatMap { root -> root.walkTopDown().maxDepth(5).filter { it.isFile }.toList() }
            .filter { file -> isSupportedDocument(file.name, "") && file.canRead() && file.length() > 0L }
            .distinctBy { it.absolutePath }
            .map { file ->
                LocalDocument(
                    name = file.name,
                    path = file.absolutePath,
                    mimeType = mimeFor(file.name),
                    size = file.length(),
                    modifiedAt = file.lastModified()
                )
            }
            .sortedByDescending { it.modifiedAt }
    }

    private fun isSupportedDocument(name: String, mimeType: String): Boolean {
        val kind = DocumentKind.resolve(name, mimeType)
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return mimeType.lowercase() in kind.mimeSet || extension in kind.extensionSet
    }

    private fun mimeFor(name: String): String {
        return when (DocumentKind.resolve(name, "")) {
            DocumentKind.PDF -> "application/pdf"
            DocumentKind.WORD -> "application/msword"
            DocumentKind.EXCEL -> "application/vnd.ms-excel"
            DocumentKind.PPT -> "application/vnd.ms-powerpoint"
        }
    }

    private fun buildTargetName(originalName: String, inputName: String): String {
        val originalExtension = originalName.substringAfterLast('.', missingDelimiterValue = "")
        if (originalExtension.isBlank()) return inputName
        return if (inputName.endsWith(".$originalExtension", ignoreCase = true)) {
            inputName
        } else {
            "$inputName.$originalExtension"
        }
    }

    private fun LocalDocument.toRecord(): DocumentRecord {
        return DocumentRecord(
            path = path,
            name = name,
            mimeType = mimeType,
            size = size,
            modifiedAt = modifiedAt,
            openedAt = openedAt,
            favorite = favorite,
            favoriteAt = favoriteAt
        )
    }

    private fun DocumentRecord.toDocumentIfAvailable(): LocalDocument? {
        val file = File(path)
        if (file.exists().not()) {
            stateStore.delete(path)
            return null
        }
        return LocalDocument(
            name = file.name.ifBlank { name },
            path = path,
            mimeType = mimeType.ifBlank { mimeFor(file.name) },
            size = file.length().takeIf { it > 0L } ?: size,
            modifiedAt = file.lastModified().takeIf { it > 0L } ?: modifiedAt,
            openedAt = openedAt,
            favorite = favorite,
            favoriteAt = favoriteAt
        )
    }

    private fun refreshMedia(vararg paths: String) {
        MediaScannerConnection.scanFile(appContext, paths, null, null)
    }
}

data class DocumentOperationResult(
    val document: LocalDocument? = null,
    val errorRes: Int? = null
)

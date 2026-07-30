package com.ysdc.aidpdf.data.document

import androidx.annotation.DrawableRes
import com.ysdc.aidpdf.R
import java.io.File

data class LocalDocument(
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val modifiedAt: Long,
    val openedAt: Long = 0L,
    val favorite: Boolean = false,
    val favoriteAt: Long = 0L
) {
    val kind: DocumentKind
        get() = DocumentKind.resolve(name, mimeType)

    fun exists(): Boolean = File(path).exists()
}

enum class HomeSection {
    HOME,
    RECENT,
    FAVORITES,
    SETTING
}

enum class DocumentKind(
    @DrawableRes val iconRes: Int,
    val extensionSet: Set<String>,
    val mimeSet: Set<String>
) {
    PDF(
        iconRes = R.drawable.img_pdf,
        extensionSet = setOf("pdf"),
        mimeSet = setOf("application/pdf")
    ),
    WORD(
        iconRes = R.drawable.img_word,
        extensionSet = setOf("doc", "docx"),
        mimeSet = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
        )
    ),
    EXCEL(
        iconRes = R.drawable.img_excel,
        extensionSet = setOf("xls", "xlsx", "excel"),
        mimeSet = setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template"
        )
    ),
    PPT(
        iconRes = R.drawable.img_ppt,
        extensionSet = setOf("ppt", "pptx"),
        mimeSet = setOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
        )
    );

    companion object {
        val filters: List<DocumentKind> = listOf(PDF, WORD, EXCEL, PPT)

        fun resolve(name: String, mimeType: String): DocumentKind {
            val cleanMime = mimeType.lowercase()
            val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { cleanMime in it.mimeSet || extension in it.extensionSet } ?: PDF
        }

        fun supportedMimes(): Array<String> = entries.flatMap { it.mimeSet }.distinct().toTypedArray()
    }
}

package com.ysdc.aidpdf.data.document

internal fun List<LocalDocument>.replaceRenamedDocument(
    previousPath: String,
    renamed: LocalDocument
): List<LocalDocument> {
    return map { document ->
        if (document.path == previousPath) renamed else document
    }
}

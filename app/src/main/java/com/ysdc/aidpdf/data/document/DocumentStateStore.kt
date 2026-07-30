package com.ysdc.aidpdf.data.document

import com.ysdc.aidpdf.store.documentRecordsJson
import org.json.JSONArray
import org.json.JSONObject

class DocumentStateStore {

    fun records(): Map<String, DocumentRecord> {
        return runCatching {
            val array = JSONArray(documentRecordsJson)
            buildMap {
                for (index in 0 until array.length()) {
                    val record = array.getJSONObject(index).toRecord()
                    put(record.path, record)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun find(path: String): DocumentRecord? = records()[path]

    fun save(record: DocumentRecord) {
        val next = records().toMutableMap()
        next[record.path] = record
        persist(next.values)
    }

    fun delete(path: String) {
        val next = records().toMutableMap()
        next.remove(path)
        persist(next.values)
    }

    fun clearOpened(path: String) {
        val saved = find(path) ?: return
        if (saved.favorite) {
            save(saved.copy(openedAt = 0L))
        } else {
            delete(path)
        }
    }

    private fun persist(records: Collection<DocumentRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        documentRecordsJson = array.toString()
    }

    private fun JSONObject.toRecord(): DocumentRecord {
        return DocumentRecord(
            path = optString("path"),
            name = optString("name"),
            mimeType = optString("mimeType"),
            size = optLong("size"),
            modifiedAt = optLong("modifiedAt"),
            openedAt = optLong("openedAt"),
            favorite = optBoolean("favorite"),
            favoriteAt = optLong("favoriteAt")
        )
    }

    private fun DocumentRecord.toJson(): JSONObject {
        return JSONObject()
            .put("path", path)
            .put("name", name)
            .put("mimeType", mimeType)
            .put("size", size)
            .put("modifiedAt", modifiedAt)
            .put("openedAt", openedAt)
            .put("favorite", favorite)
            .put("favoriteAt", favoriteAt)
    }
}

data class DocumentRecord(
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedAt: Long,
    val openedAt: Long,
    val favorite: Boolean,
    val favoriteAt: Long
)

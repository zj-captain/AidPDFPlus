package com.ysdc.aidpdf.tracking

internal data class EventPayload(
    val name: String,
    val parameters: Map<String, Any>
)

internal object EventPayloadNormalizer {

    private const val MAX_PARAMETERS = 25
    private val validIdentifier = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
    private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

    fun normalize(name: String, parameters: Map<String, Any?>): EventPayload? {
        val eventName = name.trim()
        if (!isValidIdentifier(eventName)) return null

        val normalizedParameters = linkedMapOf<String, Any>()
        parameters.forEach { (rawKey, rawValue) ->
            if (normalizedParameters.size >= MAX_PARAMETERS) return@forEach

            val key = rawKey.trim()
            if (!isValidIdentifier(key) || normalizedParameters.containsKey(key)) return@forEach

            normalizeValue(rawValue)?.let { value ->
                normalizedParameters[key] = value
            }
        }

        return EventPayload(eventName, normalizedParameters)
    }

    private fun isValidIdentifier(value: String): Boolean {
        return validIdentifier.matches(value) && reservedPrefixes.none(value::startsWith)
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String -> value
            is CharSequence -> value.toString()
            is Char -> value.toString()
            is Boolean -> if (value) 1L else 0L
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            is Float -> value.takeIf(Float::isFinite)?.toDouble()
            is Double -> value.takeIf(Double::isFinite)
            else -> null
        }
    }
}

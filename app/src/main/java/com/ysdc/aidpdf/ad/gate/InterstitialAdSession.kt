package com.ysdc.aidpdf.ad.gate

internal class InterstitialAdSession {

    private var nextId = 0L
    private var activeId: Long? = null

    val isActive: Boolean
        get() = activeId != null

    fun start(): Long? {
        if (activeId != null) return null
        return (++nextId).also { activeId = it }
    }

    fun isActive(id: Long): Boolean = activeId == id

    fun finish(id: Long): Boolean {
        if (activeId != id) return false
        activeId = null
        return true
    }

    fun abandon() {
        activeId = null
    }
}

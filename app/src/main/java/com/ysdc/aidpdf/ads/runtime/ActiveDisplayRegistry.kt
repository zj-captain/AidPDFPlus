package com.ysdc.aidpdf.ads.runtime

import android.view.ViewGroup
import com.ysdc.aidpdf.ads.model.AdDisplayHandle
import java.util.WeakHashMap

class ActiveDisplayRegistry {
    private val nativeHandles = WeakHashMap<ViewGroup, AdDisplayHandle>()
    private val bannerHandles = WeakHashMap<ViewGroup, AdDisplayHandle>()

    fun hasNative(parent: ViewGroup): Boolean = nativeHandles[parent] != null

    fun registerNative(parent: ViewGroup, handle: AdDisplayHandle) {
        nativeHandles[parent] = handle
    }

    fun unregisterNative(parent: ViewGroup, handle: AdDisplayHandle) {
        if (nativeHandles[parent] === handle) {
            nativeHandles.remove(parent)
        }
    }

    fun hasBanner(parent: ViewGroup): Boolean = bannerHandles[parent] != null

    fun registerBanner(parent: ViewGroup, handle: AdDisplayHandle) {
        bannerHandles[parent] = handle
    }

    fun unregisterBanner(parent: ViewGroup, handle: AdDisplayHandle) {
        if (bannerHandles[parent] === handle) {
            bannerHandles.remove(parent)
        }
    }

    fun snapshotHandles(): List<AdDisplayHandle> {
        return (nativeHandles.values + bannerHandles.values).distinct()
    }

    fun clear() {
        nativeHandles.clear()
        bannerHandles.clear()
    }
}

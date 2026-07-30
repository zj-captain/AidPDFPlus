package com.ysdc.aidpdf.core.block

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.ysdc.aidpdf.ad.AidAdHub
import com.ysdc.aidpdf.store.installReferrer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object InstallReferrerRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var requestJob: Job? = null

    @Synchronized
    fun refreshIfMissing(context: Context, onStored: () -> Unit = {}) {
        if (installReferrer.isNotBlank()) {
            onStored()
            return
        }
        if (requestJob?.isActive == true) return

        val appContext = context.applicationContext
        requestJob = scope.launch {
            repeat(FAST_RETRY_LIMIT) {
                if (requestAndStore(appContext)) {
                    withContext(Dispatchers.Main.immediate) { onStored() }
                    return@launch
                }
            }

            while (installReferrer.isBlank()) {
                delay(SLOW_RETRY_DELAY_MILLIS)
                if (requestAndStore(appContext)) {
                    withContext(Dispatchers.Main.immediate) { onStored() }
                    return@launch
                }
            }
        }
    }

    private suspend fun requestAndStore(context: Context): Boolean {
        val value = requestOnce(context)?.takeIf(String::isNotBlank) ?: return false
        installReferrer = value
        return true
    }

    private suspend fun requestOnce(context: Context): String? {
        return runCatching {
            val client = InstallReferrerClient.newBuilder(context).build()
            try {
                withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation {
                            runCatching { client.endConnection() }
                        }
                        client.startConnection(object : InstallReferrerStateListener {
                            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                                val value = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                    runCatching { client.installReferrer.installReferrer }.getOrNull()
                                } else {
                                    null
                                }
                                if (continuation.isActive) continuation.resume(value)
                            }

                            override fun onInstallReferrerServiceDisconnected() {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                }
            } finally {
                runCatching { client.endConnection() }
            }
        }.onFailure {
            AidAdHub.log("Install referrer request failed: ${it.message}")
        }.getOrNull()
    }

    private const val FAST_RETRY_LIMIT = 10
    private const val REQUEST_TIMEOUT_MILLIS = 15_000L
    private const val SLOW_RETRY_DELAY_MILLIS = 5 * 60_000L
}

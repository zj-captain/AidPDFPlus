package com.ysdc.aidpdf.ads.utils

import android.os.Looper
import androidx.annotation.MainThread
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/**
 * 展示前读取 Next-Gen GMA 已加载广告对象中的价格候选值。
 *
 * 这个工具只做本地、只读反射，不调用 show()、不发起请求，也不修改广告对象。
 * 固定路径和递归策略按竞品 APK 中反编译出的 AdmobNextGenReflectionUtil 对齐。
 *
 * 重要边界：
 * - 反射访问的是 SDK 私有对象，Google 没有把它承诺为公开 API；版本变化后可能失效。
 * - [probe] 命中的值只能用于展示前竞价/诊断，不能当作最终收益真值。
 * - 同一广告曝光后的 [inspectPaidEvent] 才是公开 SDK 回调提供的收益真值。
 * - 任意反射失败都会安全返回结果，不影响广告展示和原有收益上报链路。
 */
object AdMobPriceReflectionUtil {

    private const val TAG = "AdMobPriceProbe"
    private const val MICROS_PER_UNIT = 1_000_000.0
    private const val MAX_RECURSION_DEPTH = 10
    private const val MAX_VISITED_OBJECTS = 800
    private const val MAX_FIELDS_PER_OBJECT = 128
    private const val GOOGLE_SAMPLE_AD_UNIT_PREFIX = "ca-app-pub-3940256099942544/"

    private val currencyPattern = Regex("^[A-Z]{3}$")

    /** 探测结果状态。 */
    enum class Status {
        /** 公开 onAdPaid 回调返回了价格；这是可作为收益真值的结果。 */
        FOUND,

        /** Google 官方示例广告位的回调值，仅用于验证回调链路。 */
        GOOGLE_TEST_VALUE,

        /** 正式广告的公开回调返回了 0。 */
        ZERO_VALUE_REPORTED,

        /** 命中了 Next-Gen 私有对象中的价格候选，但尚未经过公开回调验证。 */
        PRIVATE_VALUE_UNVERIFIED,

        AD_NOT_READY,
        WRONG_THREAD,
        UNSUPPORTED_SDK,
        VALUE_NOT_PRESENT,
        REFLECTION_FAILED
    }

    /** 价格读取来源。 */
    enum class Source {
        /** Google 公开广告事件回调 onAdPaid 提供的 AdValue。 */
        GOOGLE_ON_PAID_EVENT,

        /** 竞品固定路径末端本身就是 Next-Gen AdValue。 */
        NEXT_GEN_FIXED_PATH_AD_VALUE,

        /** 竞品固定路径末端通过 PrecisionType + long + String 特征重建。 */
        NEXT_GEN_FIXED_PATH_RECONSTRUCTED,

        /** 受限递归遍历中发现真实 AdValue。 */
        NEXT_GEN_RECURSIVE_AD_VALUE,

        /** 受限递归遍历中通过严格字段特征重建。 */
        NEXT_GEN_RECURSIVE_RECONSTRUCTED
    }

    /** 对结果真实性的描述，不等同于 Google PrecisionType 的定义。 */
    enum class Confidence {
        /** 来自当前广告公开 onAdPaid 回调，可以作为该次曝光的收益真值。 */
        PUBLIC_API_CALLBACK,

        /** 末端对象真实属于 AdValue 类型，但来自私有对象图。 */
        PRIVATE_AD_VALUE,

        /** 私有对象按竞品字段特征重建，必须用同一广告的 onAdPaid 对照。 */
        PRIVATE_FIELD_RECONSTRUCTED
    }

    data class Price(
        /** 单次曝光价值，单位为微单位；1,000,000 micros = 1 个 currencyCode 货币单位。 */
        val valueMicros: Long,
        val currencyCode: String,
        val precisionType: Int?,
        val precisionName: String,
        val source: Source,
        val confidence: Confidence
    ) {
        val impressionValue: Double get() = valueMicros / MICROS_PER_UNIT
        val ecpm: Double get() = impressionValue * 1_000.0
    }

    data class Result(
        val status: Status,
        val price: Price?,
        val adClassName: String?,
        val matchedPath: String?,
        val visitedObjects: Int,
        val reflectionErrors: Int,
        val message: String,
        val adUnitId: String? = null,
        val isGoogleSampleAdUnit: Boolean = false
    ) {
        /** 只有公开收益回调的正值才会返回 true。 */
        val isFound: Boolean get() = status == Status.FOUND && price != null
    }

    private data class ScanState(
        var visitedObjects: Int = 0,
        var reflectionErrors: Int = 0
    )

    private data class ParsedValue(
        val adValue: AdValue,
        val source: Source,
        val confidence: Confidence
    )

    private data class Candidate(
        val parsed: ParsedValue,
        val path: String
    )

    /*
     * 以下数组逐字提取自竞品 APK 的：
     * jadx/jadx147_simple_admob/AdmobNextGenReflectionUtil.java
     *
     * 这里只复制竞品已经实际使用的路径，不根据当前 SDK 类名猜测新增路径。
     */
    private val interstitialPaths = listOf(
        arrayOf("b", "k", "L", "e", "b", "j", "a", "M", "c", "m"),
        arrayOf("b", "k", "M", "c", "m")
    )
    private val appOpenPaths = listOf(arrayOf("b", "k", "M", "c", "m"))
    private val rewardedPaths = listOf(arrayOf("c", "a", "a", "k", "M", "c", "m"))
    private val nativePaths = listOf(
        arrayOf("b", "l", "j", "e", "b", "j", "a", "M", "c", "m"),
        arrayOf("b", "l", "s", "e", "m")
    )
    private val bannerPaths = listOf(arrayOf("b", "k", "a", "d", "d", "a", "m"))

    /**
     * 从已加载广告对象探测展示前价格候选。
     * 必须在主线程、广告已进入 Ready 状态后调用。
     */
    @MainThread
    fun probe(ad: Any?, adUnitId: String? = null): Result {
        if (ad == null) return notReady(adUnitId)

        val isSample = isGoogleSampleAdUnit(adUnitId)
        return try {
            probeLoadedAd(ad, adUnitId, isSample)
        } catch (throwable: Throwable) {
            rethrowIfFatal(throwable)
            Result(
                status = Status.REFLECTION_FAILED,
                price = null,
                adClassName = ad.javaClass.name,
                matchedPath = null,
                visitedObjects = 0,
                reflectionErrors = 1,
                message = "展示前价格探测发生异常，已安全降级：${throwable.javaClass.simpleName}",
                adUnitId = adUnitId,
                isGoogleSampleAdUnit = isSample
            ).logged()
        }
    }

    private fun probeLoadedAd(ad: Any, adUnitId: String?, isSample: Boolean): Result {
        val adType = resolveAdType(ad)
        "开始探测 广告类=${ad.javaClass.name} 类型=${adType ?: "Unsupported"} " +
            "广告位=${adUnitId ?: "unknown"} Google示例广告位=${if (isSample) "是" else "否"}".logD(TAG)

        if (Looper.myLooper() != Looper.getMainLooper()) {
            return Result(
                status = Status.WRONG_THREAD,
                price = null,
                adClassName = ad.javaClass.name,
                matchedPath = null,
                visitedObjects = 0,
                reflectionErrors = 0,
                message = "价格探测必须在主线程调用",
                adUnitId = adUnitId,
                isGoogleSampleAdUnit = isSample
            ).logged()
        }

        if (adType == null) {
            return Result(
                status = Status.UNSUPPORTED_SDK,
                price = null,
                adClassName = ad.javaClass.name,
                matchedPath = null,
                visitedObjects = 0,
                reflectionErrors = 0,
                message = "对象不是当前 Next-Gen GMA 支持的广告类型",
                adUnitId = adUnitId,
                isGoogleSampleAdUnit = isSample
            ).logged()
        }

        val state = ScanState()
        val fixedCandidate = findByFixedPaths(ad, adType.paths, adType.label, state)
        if (fixedCandidate != null) {
            // 与竞品一致：固定路径先返回正价格；所有路径都非正时返回最后一个非正候选。
            return privateResult(
                ad,
                adUnitId,
                isSample,
                fixedCandidate.toPrice(),
                fixedCandidate.path,
                state
            )
        }

        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val recursiveCandidate = findRecursively(
            value = ad,
            path = "<root>",
            depth = 0,
            visited = visited,
            state = state
        )
        if (recursiveCandidate != null) {
            return privateResult(
                ad,
                adUnitId,
                isSample,
                recursiveCandidate.toPrice(),
                recursiveCandidate.path,
                state
            )
        }

        return Result(
            status = if (state.reflectionErrors > 0) Status.REFLECTION_FAILED else Status.VALUE_NOT_PRESENT,
            price = null,
            adClassName = ad.javaClass.name,
            matchedPath = null,
            visitedObjects = state.visitedObjects,
            reflectionErrors = state.reflectionErrors,
            message = "竞品固定路径和受限递归都未取得价格候选；不要把未命中解释为价格为 0",
            adUnitId = adUnitId,
            isGoogleSampleAdUnit = isSample
        ).logged()
    }

    private fun findByFixedPaths(
        ad: Any,
        paths: List<Array<String>>,
        adLabel: String,
        state: ScanState
    ): Candidate? {
        var lastNonPositive: Candidate? = null
        for ((index, path) in paths.withIndex()) {
            val pathText = path.joinToString("→")
            "[$adLabel] 尝试固定路径#$index $pathText".logD(TAG)
            val leaf = traverse(ad, path, state) ?: continue
            val parsed = parseLeaf(leaf, fixedPath = true) ?: continue
            val candidate = Candidate(parsed, pathText)
            if (parsed.adValue.valueMicros > 0L) {
                "[$adLabel] 固定路径命中正价格 valueMicros=${parsed.adValue.valueMicros} path=$pathText".logD(TAG)
                return candidate
            }
            "[$adLabel] 固定路径命中非正价格 valueMicros=${parsed.adValue.valueMicros} path=$pathText".logD(TAG)
            lastNonPositive = candidate
        }
        return lastNonPositive
    }

    private fun findRecursively(
        value: Any?,
        path: String,
        depth: Int,
        visited: MutableSet<Any>,
        state: ScanState
    ): Candidate? {
        if (value == null || depth > MAX_RECURSION_DEPTH || state.visitedObjects >= MAX_VISITED_OBJECTS) return null
        if (!visited.add(value)) return null
        state.visitedObjects++

        parseLeaf(value, fixedPath = false)?.let { return Candidate(it, path) }

        var current: Class<*>? = value.javaClass
        var fieldsRead = 0
        while (current != null && current != Any::class.java && fieldsRead < MAX_FIELDS_PER_OBJECT) {
            for (field in current.declaredFields) {
                if (fieldsRead++ >= MAX_FIELDS_PER_OBJECT) break
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || isBasicType(field.type)) continue

                val child = try {
                    readField(value, field)
                } catch (throwable: Throwable) {
                    rethrowIfFatal(throwable)
                    state.reflectionErrors++
                    null
                } ?: continue

                val childPath = if (path == "<root>") field.name else "$path→${field.name}"
                findRecursively(child, childPath, depth + 1, visited, state)?.let { return it }
            }
            current = current.superclass
        }
        return null
    }

    private fun parseLeaf(value: Any, fixedPath: Boolean): ParsedValue? {
        if (value is AdValue) {
            if (!isValidAdValue(value)) return null
            return ParsedValue(
                adValue = value,
                source = if (fixedPath) Source.NEXT_GEN_FIXED_PATH_AD_VALUE else Source.NEXT_GEN_RECURSIVE_AD_VALUE,
                confidence = Confidence.PRIVATE_AD_VALUE
            )
        }

        val reconstructed = reconstructAdValue(value) ?: return null
        return ParsedValue(
            adValue = reconstructed,
            source = if (fixedPath) Source.NEXT_GEN_FIXED_PATH_RECONSTRUCTED
            else Source.NEXT_GEN_RECURSIVE_RECONSTRUCTED,
            confidence = Confidence.PRIVATE_FIELD_RECONSTRUCTED
        )
    }

    /**
     * 与竞品 checkAndCreateAdValue 对齐：只接受真实 PrecisionType、Long/long、非空 String。
     * AdValue 构造器在当前本地 1.3.0 AAR 中是公开构造器，因此这里不再反射调用构造器。
     */
    private fun reconstructAdValue(value: Any): AdValue? {
        if (value is AdValue) return value

        return try {
            var precision: PrecisionType? = null
            var micros: Long? = null
            var currency: String? = null
            var type: Class<*>? = value.javaClass

            while (type != null && type != Any::class.java) {
                for (field in type.declaredFields) {
                    if (Modifier.isStatic(field.modifiers)) continue
                    val fieldValue = readField(value, field) ?: continue
                    when {
                        field.type == PrecisionType::class.java && fieldValue is PrecisionType -> {
                            precision = fieldValue
                        }

                        field.type == Long::class.javaPrimitiveType || field.type == Long::class.java -> {
                            val candidate = (fieldValue as? Number)?.toLong() ?: continue
                            if (micros == null || (candidate > 0L && candidate > micros!!)) {
                                micros = candidate
                            }
                        }

                        field.type == String::class.java -> {
                            val candidate = (fieldValue as? String)?.trim() ?: continue
                            // 与竞品保持相同的 2~5 字符候选规则；最终价格仍要求标准三字母币种。
                            if (candidate.isNotEmpty() &&
                                (currency == null || candidate.length in 2..5)
                            ) {
                                currency = candidate
                            }
                        }
                    }
                }
                type = type.superclass
            }

            val resolvedPrecision = precision ?: return null
            val resolvedMicros = micros ?: return null
            val resolvedCurrency = currency ?: return null
            if (resolvedMicros < 0L) return null
            val normalizedCurrency = resolvedCurrency.uppercase(Locale.US)
            if (!currencyPattern.matches(normalizedCurrency)) return null

            AdValue(resolvedPrecision, resolvedMicros, normalizedCurrency)
        } catch (throwable: Throwable) {
            rethrowIfFatal(throwable)
            "重建 Next-Gen AdValue 失败 类=${value.javaClass.name}".logE(TAG, throwable)
            null
        }
    }

    private fun Candidate.toPrice(): Price {
        val value = parsed.adValue
        return Price(
            valueMicros = value.valueMicros,
            currencyCode = value.currencyCode,
            precisionType = precisionTypeCode(value.precisionType),
            precisionName = value.precisionType.name,
            source = parsed.source,
            confidence = parsed.confidence
        )
    }

    /** 记录公开 onAdPaid；只有这里的正值才是该次曝光可用的收益真值。 */
    fun inspectPaidEvent(adValue: AdValue, adUnitId: String): Result {
        return try {
            val sample = isGoogleSampleAdUnit(adUnitId)
            val price = adValue.toPublicPrice()
            if (price == null) {
                return Result(
                    status = Status.REFLECTION_FAILED,
                    price = null,
                    adClassName = adValue.javaClass.name,
                    matchedPath = "<onAdPaid>",
                    visitedObjects = 1,
                    reflectionErrors = 0,
                    message = "公开 onAdPaid 返回了无法解析的 AdValue",
                    adUnitId = adUnitId,
                    isGoogleSampleAdUnit = sample
                ).logged()
            }
            val status = when {
                sample -> Status.GOOGLE_TEST_VALUE
                adValue.valueMicros == 0L -> Status.ZERO_VALUE_REPORTED
                else -> Status.FOUND
            }
            Result(
                status = status,
                price = price,
                adClassName = adValue.javaClass.name,
                matchedPath = "<onAdPaid>",
                visitedObjects = 1,
                reflectionErrors = 0,
                message = when (status) {
                    Status.GOOGLE_TEST_VALUE -> "Google 示例广告位回调值，仅用于验证回调链路"
                    Status.ZERO_VALUE_REPORTED -> "正式广告公开 onAdPaid 回调返回 0；保留原值上报，不当作正价格"
                    else -> "价格来自同一广告的公开 onAdPaid 回调，可作为该次曝光收益真值"
                },
                adUnitId = adUnitId,
                isGoogleSampleAdUnit = sample
            ).logged()
        } catch (throwable: Throwable) {
            rethrowIfFatal(throwable)
            Result(
                status = Status.REFLECTION_FAILED,
                price = null,
                adClassName = adValue.javaClass.name,
                matchedPath = "<onAdPaid>",
                visitedObjects = 1,
                reflectionErrors = 1,
                message = "公开收益回调诊断异常，已安全降级：${throwable.javaClass.simpleName}",
                adUnitId = adUnitId,
                isGoogleSampleAdUnit = isGoogleSampleAdUnit(adUnitId)
            ).logged()
        }
    }

    fun notReady(adUnitId: String? = null): Result = Result(
        status = Status.AD_NOT_READY,
        price = null,
        adClassName = null,
        matchedPath = null,
        visitedObjects = 0,
        reflectionErrors = 0,
        message = "广告尚未加载完成或缓存已过期",
        adUnitId = adUnitId,
        isGoogleSampleAdUnit = isGoogleSampleAdUnit(adUnitId)
    ).logged()

    private fun AdValue.toPublicPrice(): Price? {
        val normalizedCurrency = currencyCode.trim().uppercase(Locale.US)
        if (valueMicros < 0L || !currencyPattern.matches(normalizedCurrency)) return null
        return Price(
            valueMicros = valueMicros,
            currencyCode = normalizedCurrency,
            precisionType = precisionTypeCode(precisionType),
            precisionName = precisionType.name,
            source = Source.GOOGLE_ON_PAID_EVENT,
            confidence = Confidence.PUBLIC_API_CALLBACK
        )
    }

    private fun isValidAdValue(value: AdValue): Boolean =
        value.valueMicros >= 0L &&
            currencyPattern.matches(value.currencyCode.trim().uppercase(Locale.US))

    private fun privateResult(
        ad: Any,
        adUnitId: String?,
        isSample: Boolean,
        price: Price,
        path: String,
        state: ScanState
    ): Result = Result(
        status = Status.PRIVATE_VALUE_UNVERIFIED,
        price = price,
        adClassName = ad.javaClass.name,
        matchedPath = path,
        visitedObjects = state.visitedObjects,
        reflectionErrors = state.reflectionErrors,
        message = "命中竞品 Next-Gen 私有路径/递归价格候选；展示前不能证明等于最终曝光收益，需用同一广告 onAdPaid 对照",
        adUnitId = adUnitId,
        isGoogleSampleAdUnit = isSample
    ).logged()

    private fun resolveAdType(ad: Any): AdType? = when {
        ad is InterstitialAd -> AdType("插屏", interstitialPaths)
        ad is AppOpenAd -> AdType("开屏", appOpenPaths)
        ad is RewardedAd -> AdType("激励", rewardedPaths)
        ad is NativeAd -> AdType("原生", nativePaths)
        ad is BannerAd -> AdType("Banner", bannerPaths)
        else -> null
    }

    private data class AdType(val label: String, val paths: List<Array<String>>)

    private fun traverse(root: Any, path: Array<String>, state: ScanState): Any? {
        var current: Any? = root
        for (fieldName in path) {
            if (current == null) return null
            state.visitedObjects++
            val target = current
            current = try {
                readNamedField(target, fieldName)
            } catch (throwable: Throwable) {
                rethrowIfFatal(throwable)
                state.reflectionErrors++
                "固定路径读取失败 当前类=${target.javaClass.name} 字段=$fieldName".logE(TAG, throwable)
                return null
            }
            if (current == null) {
                "固定路径中断 字段=$fieldName".logD(TAG)
                return null
            }
        }
        return current
    }

    private fun readNamedField(target: Any, fieldName: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            try {
                return readField(target, type.getDeclaredField(fieldName))
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun readField(target: Any, field: Field): Any? {
        if (!field.isAccessible) field.isAccessible = true
        return field.get(target)
    }

    private fun isBasicType(type: Class<*>): Boolean =
        type.isPrimitive ||
            type == String::class.java ||
            Number::class.java.isAssignableFrom(type) ||
            type == Boolean::class.java ||
            type == Character::class.java ||
            type.isEnum ||
            type.name.startsWith("java.lang.") ||
            (type.isArray && type.componentType?.let(::isBasicType) == true)

    private fun isGoogleSampleAdUnit(adUnitId: String?): Boolean =
        adUnitId?.startsWith(GOOGLE_SAMPLE_AD_UNIT_PREFIX) == true

    private fun precisionTypeCode(value: PrecisionType): Int = when (value) {
        PrecisionType.UNKNOWN -> 0
        PrecisionType.ESTIMATED -> 1
        PrecisionType.PUBLISHER_PROVIDED -> 2
        PrecisionType.PRECISE -> 3
    }

    private fun Result.logged(): Result {
        val detail = buildString {
            append("状态=$status 广告类=${adClassName ?: "null"}")
            append(" 广告位=${adUnitId ?: "unknown"}")
            append(" Google示例广告位=${if (isGoogleSampleAdUnit) "是" else "否"}")
            price?.let {
                append(" valueMicros=${it.valueMicros}")
                append(" 单次曝光价值=${formatDecimal(it.impressionValue)} ${it.currencyCode}")
                append(" eCPM=${formatDecimal(it.ecpm)} ${it.currencyCode}")
                append(" 精度=${it.precisionName}(${it.precisionType})")
                append(" 来源=${it.source} 可信度=${it.confidence}")
            }
            append(" 路径=${matchedPath ?: "null"}")
            append(" 遍历对象=$visitedObjects 反射异常=$reflectionErrors")
            append(" 说明=$message")
        }
        when (status) {
            Status.FOUND,
            Status.GOOGLE_TEST_VALUE -> "价格诊断 $detail".logD(TAG)
            Status.WRONG_THREAD,
            Status.REFLECTION_FAILED -> "价格诊断失败 $detail".logE(TAG)
            else -> "价格诊断结果 $detail".logW(TAG)
        }
        return this
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    /** 只吞掉工具自身的普通反射故障；OOM/ThreadDeath 等致命错误继续抛出。 */
    private fun rethrowIfFatal(throwable: Throwable) {
        if (throwable is VirtualMachineError || throwable is ThreadDeath) throw throwable
        if (throwable is InterruptedException) Thread.currentThread().interrupt()
    }
}

# AdMobPriceReflectionUtil 独立接入与使用手册

> 文档基准日期：2026-08-13
>
> 适用对象：希望将 `AdMobPriceReflectionUtil.kt` 复制到任意 Android 工程并独立接入的开发者
>
> 已核对 SDK 基线：`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.3.0`

## 1. 文档目的

本文不依赖任何既有广告管理框架、广告仓库、广告位枚举或业务工程结构。

只要目标项目使用的是本文指定的 **Next-Gen Google Mobile Ads SDK**，并能拿到已经加载完成的广告对象，就可以按照本文接入 `AdMobPriceReflectionUtil`。

本文说明以下内容：

1. 如何把工具类复制到另一个项目；
2. 需要添加什么依赖；
3. 如何解除工具类中的项目专属日志依赖；
4. 如何在广告加载完成后、展示前调用 `probe()`；
5. 如何在公开 `onAdPaid` 回调中调用 `inspectPaidEvent()`；
6. 如何正确理解价格、状态、来源和可信度；
7. 如何在不影响广告展示和正式收益上报的前提下安全降级；
8. SDK 升级后如何验证反射逻辑是否仍然有效。

## 2. 能力和边界

`AdMobPriceReflectionUtil` 提供两条完全不同的价格读取链路。

| 链路 | 方法 | 发生时机 | 数据来源 | 可否作为最终收益真值 |
|---|---|---|---|---|
| 展示前候选探测 | `probe(ad, adUnitId)` | 广告加载完成后、展示前 | SDK 私有对象图 | 不可以 |
| 曝光后公开值诊断 | `inspectPaidEvent(adValue, adUnitId)` | SDK 调用 `onAdPaid` 时 | Google 公开 `AdValue` | 是该次展示的官方展示级收入值；具体精度见 `PrecisionType` |

工具类本身不会：

- 加载广告；
- 调用 `show()`；
- 触发曝光；
- 发起网络请求；
- 修改广告对象中的字段；
- 注册或替换广告回调；
- 自动选择要展示的广告；
- 自动上报广告收益；
- 把反射候选值变成 Google 官方确认值。

### 2.1 必须牢记的结论

- `probe()` 返回的是**私有对象中的展示前候选**，不是最终收益。
- `inspectPaidEvent()` 处理的是**公开回调值**。这是该次展示的官方展示级收入数据，但 `ESTIMATED`、`PUBLISHER_PROVIDED` 等精度不应被误写成财务结算终值。
- 反射未命中不等于广告价格为 `0`。
- `Result.isFound` 只表示正式广告位的公开 paid 回调返回正值。
- 展示前命中必须判断 `PRIVATE_VALUE_UNVERIFIED`，不能判断 `isFound`。
- 不同币种的价格不能直接比较。

## 3. 已验证的 SDK 基线

当前工具类的导入类型、`AdValue` 构造方式和私有路径基于：

```kotlin
implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.3.0")
```

仓库需要能够解析 Google Maven：

```kotlin
repositories {
    google()
    mavenCentral()
}
```

按 Google 当前 Next-Gen 接入要求，目标项目还需要：

```kotlin
android {
    compileSdk = 34 // 或更高

    defaultConfig {
        minSdk = 24 // 或更高
    }
}
```

必须在加载广告及调用其他 `MobileAds` 方法前完成 Next-Gen SDK 初始化。Google 当前说明要求把初始化调用放在后台线程，以免阻塞主线程：

```kotlin
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig

val initializationConfig = InitializationConfig.Builder(
    "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" // 目标应用的 AdMob App ID
).build()

Thread {
    MobileAds.initialize(
        applicationContext,
        initializationConfig
    ) {
        // 初始化完成后，再开放广告加载流程。
    }
}.start()
```

初始化方式应以目标 SDK 版本的公开 API 为准；上面的片段用于说明调用顺序，不替代项目现有的初始化、同意管理和线程封装。

### 3.1 版本适用范围

以下内容已经通过本地 `ads-mobile-sdk-1.3.0.aar` 核对：

- `AdValue` 的公开类型和属性；
- `PrecisionType` 的枚举值；
- 五种支持广告类型；
- 各广告类型的 `adEventCallback`；
- `AdEventCallback.onAdPaid(AdValue)`；
- `AdValue(PrecisionType, Long, String)` 构造方式。

这里的“已核对”仅指公开类签名和工具源码的静态一致性。固定私有路径是否在目标设备、目标广告响应以及目标构建类型中实际命中，仍属于必须通过真机数据验证的内容；本文不把静态存在性写成运行时已成功。

**没有证据证明私有字段路径可以跨 SDK 版本稳定工作。**

因此：

- `1.3.0` 是当前文档的已核对编译基线；
- 低于、等于或高于该版本的其他具体版本，都应重新做真机验证；
- SDK 能成功编译，不代表展示前反射路径一定能命中；
- SDK 升级后返回 `VALUE_NOT_PRESENT` 或 `REFLECTION_FAILED`，不代表广告价格为零。

### 3.2 不支持 Legacy GMA 类型

工具类识别的是：

```text
com.google.android.libraries.ads.mobile.sdk.*
```

它不识别旧版 Legacy GMA 广告对象：

```text
com.google.android.gms.ads.*
```

如果传入 Legacy GMA 广告对象，`probe()` 会返回：

```text
UNSUPPORTED_SDK
```

迁移项目还应检查依赖树，避免同时引入存在冲突的 GMA 实现。Google 当前迁移说明要求：如果项目使用中介适配器，应全局排除适配器传递引入的 Legacy `play-services-ads` 和 `play-services-ads-lite`：

```kotlin
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
```

如果目标项目没有使用中介，也应通过依赖树确认没有同时引入 Legacy 主 SDK；不要仅凭猜测增删其他 `com.google.android.gms` 模块。

## 4. 当前工具支持的广告对象

`probe()` 当前只识别以下五种 Next-Gen 类型：

```kotlin
com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
```

当前没有为以下类型建立分支：

- `RewardedInterstitialAd`；
- `SwipeableInterstitialAd`；
- 其他未来新增广告类型；
- 中介 SDK 自己封装、但没有暴露上述真实对象的包装类；
- 任意业务层代理对象。

这些对象传给 `probe()` 时会返回 `UNSUPPORTED_SDK`。

如果业务层持有的是包装对象，应取出其内部真实 Next-Gen 广告实例再调用；不要把业务包装对象直接传给工具。

## 5. 把工具类复制到新项目

### 5.1 复制文件

把 `AdMobPriceReflectionUtil.kt` 放入目标 Android 模块，例如：

```text
app/src/main/java/com/example/app/ads/AdMobPriceReflectionUtil.kt
```

修改文件第一行的包名：

```kotlin
package com.example.app.ads
```

业务代码随后按新包名导入：

```kotlin
import com.example.app.ads.AdMobPriceReflectionUtil
```

### 5.2 处理项目专属日志依赖

当前工具源码中有三项不是 Google SDK API，而是原工程的日志扩展：

```kotlin
import com.xm.framework.ext.util.logD
import com.xm.framework.ext.util.logE
import com.xm.framework.ext.util.logW
```

其他项目如果没有这些扩展，直接复制会编译失败。

迁移时必须执行以下两种方案之一。

#### 方案 A：替换成目标项目自己的日志组件

将三个 import 替换成目标项目已有的日志 API，并调整调用代码。

#### 方案 B：使用 Android Log 兼容扩展

删除上述三个 import，新增：

```kotlin
import android.util.Log
```

然后在同一 Kotlin 文件末尾、`AdMobPriceReflectionUtil` 对象外添加：

```kotlin
private fun String.logD(tag: String) {
    Log.d(tag, this)
}

private fun String.logW(tag: String) {
    Log.w(tag, this)
}

private fun String.logE(tag: String, throwable: Throwable? = null) {
    if (throwable == null) {
        Log.e(tag, this)
    } else {
        Log.e(tag, this, throwable)
    }
}
```

这样可以保持工具类现有调用方式不变。

> 注意：当前工具每次调用都会记录广告位、候选价格、币种、路径和状态。生产环境是否允许记录这些信息，应由目标项目自行决定。可以在日志兼容层增加 Debug 开关，或在 Release 中使用空实现。

### 5.3 AndroidX MainThread 注解

源码使用：

```kotlin
import androidx.annotation.MainThread
```

`ads-mobile-sdk:1.3.0` 的 POM 本身声明了 AndroidX Annotation 依赖。若目标项目主动排除了传递依赖，需要自行补回与项目兼容的 `androidx.annotation`。

## 6. 最小接入流程

正确的最小流程是：

```text
加载广告
  ↓
拿到真实广告对象
  ↓
保留广告对象和 adUnitId
  ↓
主线程调用 probe()
  ↓
仅把结果作为候选/诊断信息
  ↓
给广告设置原有业务 adEventCallback
  ↓
展示广告
  ↓
onAdPaid(adValue)
  ↓
调用 inspectPaidEvent()
  ↓
正式收益上报仍使用原始 adValue
```

### 6.1 展示前最小示例

```kotlin
import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())

fun inspectLoadedAd(ad: Any, adUnitId: String) {
    mainHandler.post {
        val result = AdMobPriceReflectionUtil.probe(
            ad = ad,
            adUnitId = adUnitId
        )

        if (
            result.status ==
                AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED &&
            result.price != null
        ) {
            val candidate = result.price
            val micros = candidate.valueMicros
            val currency = candidate.currencyCode
            val ecpm = candidate.ecpm

            // 这里只能作为展示前候选或诊断数据。
            // 不要作为该次曝光的最终收益上报。
        }
    }
}
```

如果调用点本来就在主线程，可以直接调用，不需要额外 `post`。

### 6.2 广告尚未就绪

不要用伪造对象或空包装类调用 `probe()`。

可以直接使用：

```kotlin
val result = AdMobPriceReflectionUtil.notReady(adUnitId)
```

或者传入 `null`：

```kotlin
val result = AdMobPriceReflectionUtil.probe(null, adUnitId)
```

两种方式都会得到：

```text
AD_NOT_READY
```

## 7. 三个公开方法

## 7.1 `probe(ad, adUnitId)`

声明：

```kotlin
@MainThread
fun probe(
    ad: Any?,
    adUnitId: String? = null
): AdMobPriceReflectionUtil.Result
```

用途：

- 在广告加载完成后探测展示前候选；
- 先尝试广告类型对应的固定字段路径；
- 固定路径没有命中后执行受限递归扫描；
- 所有普通反射异常都转为结构化结果。

调用条件：

1. `ad` 必须是已经加载成功的真实广告对象；
2. 必须在 Android 主线程调用；
3. 最好传入真实 `adUnitId`；
4. 应在广告仍有效且尚未释放时调用；
5. 不应在热循环、滚动回调或每帧重复调用。

可能返回：

```text
PRIVATE_VALUE_UNVERIFIED
AD_NOT_READY
WRONG_THREAD
UNSUPPORTED_SDK
VALUE_NOT_PRESENT
REFLECTION_FAILED
```

`probe()` 不会返回 `FOUND`。

### 正确判断展示前命中

```kotlin
val hasPrivateCandidate =
    result.status == AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED &&
        result.price != null
```

### 错误判断

```kotlin
if (result.isFound) {
    // 错误：展示前反射命中时 isFound 仍然是 false。
}
```

## 7.2 `inspectPaidEvent(adValue, adUnitId)`

声明：

```kotlin
fun inspectPaidEvent(
    adValue: AdValue,
    adUnitId: String
): AdMobPriceReflectionUtil.Result
```

用途：

- 接收 Google 公开 `onAdPaid` 回调中的原始 `AdValue`；
- 校验金额和三字母币种；
- 标记 Google 示例广告位；
- 生成与 `probe()` 统一的 `Result` 结构，便于日志和对照。

该方法不是反射取价。它处理的是调用方已经从公开 SDK 回调获得的 `AdValue`。

可能返回：

```text
FOUND
GOOGLE_TEST_VALUE
ZERO_VALUE_REPORTED
REFLECTION_FAILED
```

正式广告位且 `valueMicros > 0` 时返回 `FOUND`。这里的 `FOUND` 表示工具确认拿到了公开展示级回调正值，不保证其精度一定是 `PRECISE`，也不表示已完成财务结算。

正式广告位且 `valueMicros == 0` 时返回 `ZERO_VALUE_REPORTED`。

Google 示例广告位的有效值统一返回 `GOOGLE_TEST_VALUE`，即使数值大于零也不会返回 `FOUND`。

### 收益上报原则

正式展示级收入采集应继续使用回调原始值。Google 的实现建议是在拿到广告对象后立即设置 paid listener，并确保在展示前完成；回调发生时应及时把数据发送到目标分析系统：

```kotlin
override fun onAdPaid(adValue: AdValue) {
    val diagnosis = AdMobPriceReflectionUtil.inspectPaidEvent(
        adValue = adValue,
        adUnitId = adUnitId
    )

    // 诊断结果可用于日志、质量监控和与展示前候选对照。
    saveDiagnosis(diagnosis)

    // 正式收益链路继续使用 Google 回调原始 AdValue。
    reportRevenue(
        valueMicros = adValue.valueMicros,
        currencyCode = adValue.currencyCode,
        precisionType = adValue.precisionType
    )
}
```

不要为了调用工具而改变原有上报顺序，也不要因为诊断失败而阻断正式收益上报。

## 7.3 `notReady(adUnitId)`

声明：

```kotlin
fun notReady(
    adUnitId: String? = null
): AdMobPriceReflectionUtil.Result
```

用途：

- 广告对象不存在；
- 广告还在加载；
- 缓存广告已经过期；
- 广告已展示或释放，不能再视为 Ready；
- 业务封装需要统一返回 `Result`。

返回状态固定为：

```text
AD_NOT_READY
```

## 8. 五种广告类型的接入位置

工具不负责广告加载。目标项目应在自己的加载成功回调中保存广告对象，并在主线程调用 `probe()`。

以下代码只展示工具的插入位置，不代表完整的 GMA 初始化、隐私合规、加载或展示实现。

### 8.1 InterstitialAd

```kotlin
private var interstitialAd: InterstitialAd? = null

fun onInterstitialLoaded(ad: InterstitialAd, adUnitId: String) {
    interstitialAd = ad

    val result = AdMobPriceReflectionUtil.probe(ad, adUnitId)
    handlePreShowCandidate(result)
}
```

paid 回调：

```kotlin
ad.adEventCallback = object : InterstitialAdEventCallback {
    override fun onAdPaid(adValue: AdValue) {
        handlePaidEvent(adValue, adUnitId)
    }

    // 继续保留项目需要的展示、关闭、点击和失败回调。
}
```

### 8.2 AppOpenAd

```kotlin
private var appOpenAd: AppOpenAd? = null

fun onAppOpenLoaded(ad: AppOpenAd, adUnitId: String) {
    appOpenAd = ad
    handlePreShowCandidate(
        AdMobPriceReflectionUtil.probe(ad, adUnitId)
    )
}
```

paid 回调：

```kotlin
ad.adEventCallback = object : AppOpenAdEventCallback {
    override fun onAdPaid(adValue: AdValue) {
        handlePaidEvent(adValue, adUnitId)
    }
}
```

### 8.3 RewardedAd

```kotlin
private var rewardedAd: RewardedAd? = null

fun onRewardedLoaded(ad: RewardedAd, adUnitId: String) {
    rewardedAd = ad
    handlePreShowCandidate(
        AdMobPriceReflectionUtil.probe(ad, adUnitId)
    )
}
```

paid 回调：

```kotlin
ad.adEventCallback = object : RewardedAdEventCallback {
    override fun onAdPaid(adValue: AdValue) {
        handlePaidEvent(adValue, adUnitId)
    }
}
```

### 8.4 NativeAd

```kotlin
private var nativeAd: NativeAd? = null

fun onNativeLoaded(ad: NativeAd, adUnitId: String) {
    nativeAd = ad
    handlePreShowCandidate(
        AdMobPriceReflectionUtil.probe(ad, adUnitId)
    )
}
```

paid 回调：

```kotlin
ad.adEventCallback = object : NativeAdEventCallback {
    override fun onAdPaid(adValue: AdValue) {
        handlePaidEvent(adValue, adUnitId)
    }
}
```

### 8.5 BannerAd

```kotlin
private var bannerAd: BannerAd? = null

fun onBannerLoaded(ad: BannerAd, adUnitId: String) {
    bannerAd = ad
    handlePreShowCandidate(
        AdMobPriceReflectionUtil.probe(ad, adUnitId)
    )
}
```

paid 回调：

```kotlin
ad.adEventCallback = object : BannerAdEventCallback {
    override fun onAdPaid(adValue: AdValue) {
        handlePaidEvent(adValue, adUnitId)
    }
}
```

### 8.6 不要覆盖已有回调

`adEventCallback` 是一个可赋值属性。业务代码应在同一个 callback 实现中同时处理：

- `onAdPaid`；
- 展示成功；
- 展示失败；
- 关闭；
- 曝光；
- 点击；
- 广告类型特有事件。

不要先设置业务 callback，再单独设置一个只处理价格的 callback。后一次赋值会替换该属性此前保存的 callback。

## 9. Result 结构

```kotlin
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
    val isFound: Boolean
}
```

字段说明：

| 字段 | 含义 | 使用建议 |
|---|---|---|
| `status` | 结果的机器可判定状态 | 业务分支应优先判断它 |
| `price` | 解析出的价格；失败时通常为 `null` | 使用前必须判空 |
| `adClassName` | 被探测广告类名，或 paid 场景下 `AdValue` 类名 | 仅用于诊断 |
| `matchedPath` | 固定路径、递归路径或 `<onAdPaid>` | 仅用于诊断和版本对照 |
| `visitedObjects` | 本次固定路径/递归扫描的计数 | 观察扫描规模，不作为价格依据 |
| `reflectionErrors` | 读取私有字段时累计的普通异常数量 | 大于零时应记录 SDK/设备环境 |
| `message` | 面向人的中文说明 | 不要依赖文本做业务分支 |
| `adUnitId` | 调用方传入的广告位 ID | 建议始终传真实值 |
| `isGoogleSampleAdUnit` | 是否匹配 Google 示例广告位前缀 | 测试和正式数据分流 |
| `isFound` | `status == FOUND && price != null` | 只用于公开正式 paid 正值 |

`message` 的文字未来可能调整。业务代码必须判断枚举，不要比较 `message` 字符串。

## 10. Status 完整语义

| Status | 来源方法 | `price` | 是否最终收益真值 | 处理方式 |
|---|---|---:|---:|---|
| `FOUND` | `inspectPaidEvent()` | 有 | 是 | 正式广告公开回调正值 |
| `GOOGLE_TEST_VALUE` | `inspectPaidEvent()` | 有 | 否 | 示例广告，只验证回调链路 |
| `ZERO_VALUE_REPORTED` | `inspectPaidEvent()` | 有，值为 0 | 公开回调事实，但不是正价格 | 保留原值和状态，不伪造成未命中 |
| `PRIVATE_VALUE_UNVERIFIED` | `probe()` | 有 | 否 | 展示前候选，必须与 paid 回调对照 |
| `AD_NOT_READY` | `probe(null)` / `notReady()` | 无 | 否 | 等待加载或走无价格降级 |
| `WRONG_THREAD` | `probe()` | 无 | 否 | 切回主线程重试一次 |
| `UNSUPPORTED_SDK` | `probe()` | 无 | 否 | 检查 SDK、广告类型和包装对象 |
| `VALUE_NOT_PRESENT` | `probe()` | 无 | 否 | 正常未命中，不能解释成 0 |
| `REFLECTION_FAILED` | 两者都可能 | 通常无 | 否 | 记录环境并降级，不能阻断广告 |

### 10.1 推荐的统一分支

```kotlin
fun handleResult(result: AdMobPriceReflectionUtil.Result) {
    when (result.status) {
        AdMobPriceReflectionUtil.Status.FOUND -> {
            // 正式广告位公开 onAdPaid 正值。
        }

        AdMobPriceReflectionUtil.Status.GOOGLE_TEST_VALUE -> {
            // Google 示例广告位，只验证接入。
        }

        AdMobPriceReflectionUtil.Status.ZERO_VALUE_REPORTED -> {
            // 公开回调确实报告了 0，保留原始事实。
        }

        AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED -> {
            // 展示前私有候选，不作为最终收益。
        }

        AdMobPriceReflectionUtil.Status.AD_NOT_READY -> {
            // 广告尚未就绪。
        }

        AdMobPriceReflectionUtil.Status.WRONG_THREAD -> {
            // 调用线程错误。
        }

        AdMobPriceReflectionUtil.Status.UNSUPPORTED_SDK -> {
            // 类型或 SDK 不受支持。
        }

        AdMobPriceReflectionUtil.Status.VALUE_NOT_PRESENT -> {
            // 无候选，不等于 0。
        }

        AdMobPriceReflectionUtil.Status.REFLECTION_FAILED -> {
            // 安全降级，不影响展示和正式上报。
        }
    }
}
```

## 11. Price 结构和单位

```kotlin
data class Price(
    val valueMicros: Long,
    val currencyCode: String,
    val precisionType: Int?,
    val precisionName: String,
    val source: Source,
    val confidence: Confidence
) {
    val impressionValue: Double
    val ecpm: Double
}
```

### 11.1 `valueMicros`

`valueMicros` 是单次曝光价值的微单位：

```text
1,000,000 micros = 1 个 currencyCode 货币单位
```

换算：

```kotlin
val impressionValue = valueMicros / 1_000_000.0
```

### 11.2 `impressionValue`

表示单次曝光价值：

```kotlin
val impressionValue: Double
    get() = valueMicros / 1_000_000.0
```

### 11.3 `ecpm`

工具中的 `ecpm` 是按单次曝光值换算出的每千次曝光价值：

```kotlin
val ecpm: Double
    get() = impressionValue * 1_000.0
```

等价于：

```text
eCPM = valueMicros / 1000.0
```

示例：

```text
valueMicros = 2,500
currencyCode = USD

单次曝光价值 = 0.0025 USD
eCPM          = 2.5 USD
```

### 11.4 币种

工具校验币种时只接受以下标准形式：

```text
^[A-Z]{3}$
```

校验过程会对临时字符串执行去除首尾空白和 `Locale.US` 大写转换。公开 `onAdPaid` 路径和字段重建路径会把规范化后的币种写入 `Price`。

需要注意：如果私有对象图中直接发现了真实 `AdValue`，`Candidate.toPrice()` 会保留该 `AdValue.currencyCode` 的原始字符串，而不是把规范化临时值写回。因此跨来源比较前建议调用方再次规范化：

```kotlin
val normalizedCurrency =
    price.currencyCode.trim().uppercase(Locale.US)
```

`USD`、`EUR`、`CNY` 符合格式；空字符串和最终不是三字母的值不会被接受。

### 11.5 精度

当前映射来自 `PrecisionType`：

| `precisionName` | `precisionType` |
|---|---:|
| `UNKNOWN` | 0 |
| `ESTIMATED` | 1 |
| `PUBLISHER_PROVIDED` | 2 |
| `PRECISE` | 3 |

`Confidence` 与 `PrecisionType` 是两个不同概念：

- `PrecisionType` 描述 Google `AdValue` 的价值精度；
- `Confidence` 描述工具是通过公开回调、私有 `AdValue`，还是字段重建获得结果。

不要混用。

## 12. Source 和 Confidence

### 12.1 Source

| Source | 含义 |
|---|---|
| `GOOGLE_ON_PAID_EVENT` | 来自公开 `onAdPaid` 回调 |
| `NEXT_GEN_FIXED_PATH_AD_VALUE` | 固定私有路径末端本身是 `AdValue` |
| `NEXT_GEN_FIXED_PATH_RECONSTRUCTED` | 固定路径末端通过字段特征重建 `AdValue` |
| `NEXT_GEN_RECURSIVE_AD_VALUE` | 受限递归扫描发现真实 `AdValue` |
| `NEXT_GEN_RECURSIVE_RECONSTRUCTED` | 受限递归扫描后通过字段特征重建 |

### 12.2 Confidence

| Confidence | 含义 | 可否作为最终收益 |
|---|---|---:|
| `PUBLIC_API_CALLBACK` | 来自公开 `onAdPaid` | 可作为该次展示的官方展示级收入数据；仍需结合 `PrecisionType` 理解精度 |
| `PRIVATE_AD_VALUE` | 私有对象图中的对象真实属于 `AdValue` | 不可以 |
| `PRIVATE_FIELD_RECONSTRUCTED` | 根据私有字段形态重建 | 不可以 |

即使 `confidence == PRIVATE_AD_VALUE`，也只能证明私有对象图中出现了一个合法 `AdValue`，不能证明它一定是最终获胜、最终曝光或最终结算值。

## 13. 展示前反射实现细节

`probe()` 的流程如下：

```text
检查 ad 是否为 null
  ↓
判断是否主线程
  ↓
识别五种支持的广告类型
  ↓
按该类型依次尝试固定字段路径
  ↓
固定路径命中合法候选 → PRIVATE_VALUE_UNVERIFIED
  ↓ 未命中
受限递归扫描私有对象图
  ↓
递归命中合法候选 → PRIVATE_VALUE_UNVERIFIED
  ↓ 未命中
有反射异常 → REFLECTION_FAILED
无反射异常 → VALUE_NOT_PRESENT
```

### 13.1 固定路径

当前固定路径为：

#### InterstitialAd

```text
b→k→L→e→b→j→a→M→c→m
b→k→M→c→m
```

#### AppOpenAd

```text
b→k→M→c→m
```

#### RewardedAd

```text
c→a→a→k→M→c→m
```

#### NativeAd

```text
b→l→j→e→b→j→a→M→c→m
b→l→s→e→m
```

#### BannerAd

```text
b→k→a→d→d→a→m
```

这些字段名不是 Google 公开 API，也不是通过当前 SDK 类名自行推测出来的稳定契约。它们来自既有逆向样本中的实际实现。

因此不能承诺：

- 每个设备都命中；
- 每个广告来源都命中；
- 每个 SDK 版本都命中；
- 命中值一定等于后续 paid 值。

### 13.2 固定路径候选选择

同一广告类型存在多条固定路径时：

1. 按代码中的顺序读取；
2. 遇到第一个 `valueMicros > 0` 的合法候选立即返回；
3. 如果所有命中候选都不是正值，返回最后一个非正候选；
4. 合法的零值仍可能形成 `PRIVATE_VALUE_UNVERIFIED`。

因此不能使用 `status == PRIVATE_VALUE_UNVERIFIED` 直接推断价格大于零，还需要检查：

```kotlin
result.price?.valueMicros?.let { it > 0L } == true
```

### 13.3 递归扫描限制

固定路径未命中时，工具进行受限递归扫描：

```text
最大递归深度：10
最大累计访问计数：800
单个对象继承链最多读取字段：128
```

扫描会：

- 使用对象身份集合避免循环引用；
- 跳过静态字段；
- 跳过 synthetic 字段；
- 跳过基本类型、字符串、数字、布尔、字符、枚举和基础数组；
- 捕获普通字段读取异常并计数；
- 遇到 `VirtualMachineError` 或 `ThreadDeath` 时继续抛出；
- 遇到 `InterruptedException` 时恢复线程中断标记。

`visitedObjects` 是诊断计数。固定路径阶段按经过字段累计，递归阶段按访问对象累计，不应把它解释成严格统一的“唯一对象数量”。

### 13.4 字段重建规则

当叶子对象不是 `AdValue` 时，工具只在同时找到以下字段特征时尝试重建：

- 一个真实 `PrecisionType` 字段；
- 一个 `long` 或 `Long` 字段；
- 一个非空 `String` 字段；
- 最终币种必须满足三个大写字母；
- `valueMicros` 不能小于零。

如果对象包含多个 long 候选，工具倾向保留较大的正值。这个过程仍然只是私有字段特征匹配，所以结果可信度是：

```text
PRIVATE_FIELD_RECONSTRUCTED
```

该结果比直接发现真实 `AdValue` 更需要用同一广告的 `onAdPaid` 做对照。

## 14. Google 示例广告位

工具通过以下前缀识别 Google 示例广告位：

```text
ca-app-pub-3940256099942544/
```

识别依赖调用方传入 `adUnitId`。如果不传广告位 ID，工具无法把该结果标记为示例广告。

### 14.1 `probe()` 中的处理

展示前即使识别出示例广告位，命中候选时状态仍然是：

```text
PRIVATE_VALUE_UNVERIFIED
```

同时：

```kotlin
result.isGoogleSampleAdUnit == true
```

### 14.2 `inspectPaidEvent()` 中的处理

示例广告位的有效 paid 回调统一返回：

```text
GOOGLE_TEST_VALUE
```

不会返回 `FOUND`。

测试广告值不应进入正式收入统计、正式实验样本或财务数据。

还要注意：工具只按上述示例广告位前缀识别测试值。使用“测试设备 + 正式广告位 ID”发出的测试请求不会因设备测试标记而自动变成 `GOOGLE_TEST_VALUE`，因为工具没有接收测试设备信息。Google 当前还说明，部分出价来源的测试展示可能返回 `UNKNOWN` 精度和 `0` 值；这类情况必须结合请求环境判断，不能只靠本工具的前缀规则。

## 15. 展示前候选的安全使用方式

如果目标项目只需要诊断，建议仅记录以下字段：

```kotlin
status
price?.valueMicros
price?.currencyCode
price?.precisionName
price?.source
price?.confidence
matchedPath
reflectionErrors
adClassName
adUnitId
```

### 15.1 用于展示前本地选择

如果业务确实希望用候选值在多个已加载广告之间做本地选择，应至少满足：

1. 每个候选对应的广告对象都仍然有效；
2. 状态必须是 `PRIVATE_VALUE_UNVERIFIED`；
3. `price` 不为空；
4. `valueMicros > 0`；
5. 所有参与比较的候选币种相同；
6. 没有候选时有明确 fallback；
7. 选择行为不能破坏广告展示频控、有效期和回调；
8. 不把候选写入正式收益系统；
9. 后续用同一广告的 `onAdPaid` 做偏差分析。

示例：

```kotlin
data class LoadedCandidate<T : Any>(
    val ad: T,
    val adUnitId: String,
    val result: AdMobPriceReflectionUtil.Result
)

fun <T : Any> chooseByPrivateCandidate(
    candidates: List<LoadedCandidate<T>>
): LoadedCandidate<T>? {
    val usable = candidates.filter { item ->
        item.result.status ==
            AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED &&
            item.result.price != null &&
            item.result.price.valueMicros > 0L
    }

    if (usable.isEmpty()) return candidates.firstOrNull()

    val currencies = usable.mapNotNull {
        it.result.price?.currencyCode
    }.toSet()

    if (currencies.size != 1) {
        // 不同币种不可直接比较，回到项目既有选择策略。
        return candidates.firstOrNull()
    }

    return usable.maxByOrNull {
        it.result.price?.valueMicros ?: Long.MIN_VALUE
    }
}
```

这个示例只是安全边界示意，不证明候选排序一定能提升实际收入。

### 15.2 推荐记录候选与 paid 的关联

要验证反射值是否有意义，应给每个已加载广告对象生成一次本地 `loadId`：

```kotlin
data class PriceObservation(
    val loadId: String,
    val adUnitId: String,
    val phase: String,
    val status: String,
    val valueMicros: Long?,
    val currencyCode: String?,
    val source: String?,
    val confidence: String?,
    val matchedPath: String?,
    val sdkVersion: String,
    val appVersion: String
)
```

建议记录：

```text
同一个 loadId
  ├─ PRE_SHOW：probe() 结果
  └─ PAID：onAdPaid / inspectPaidEvent() 结果
```

只有同一广告实例、同一加载周期的数据才适合做差异分析。

## 16. 公开 paid 值的正确处理

推荐把工具诊断和正式收益上报分开：

```kotlin
fun handlePaidEvent(
    adValue: AdValue,
    adUnitId: String
) {
    try {
        val result = AdMobPriceReflectionUtil.inspectPaidEvent(
            adValue,
            adUnitId
        )
        savePriceDiagnosis(result)
    } catch (t: Throwable) {
        // 工具自身已经处理普通异常；此处只是业务边界保护。
        // 不要因为诊断失败中断下方的正式收益上报。
    }

    reportOfficialRevenue(adValue)
}
```

展示级收入上报字段应取自参数 `adValue`：

```kotlin
adValue.valueMicros
adValue.currencyCode
adValue.precisionType
```

不要从此前 `probe()` 的结果补写或替换公开 paid 值。

## 17. 线程与生命周期要求

### 17.1 `probe()` 必须在主线程

源码会检查：

```kotlin
Looper.myLooper() == Looper.getMainLooper()
```

否则返回：

```text
WRONG_THREAD
```

`@MainThread` 是声明约束，运行时还有实际检查。

通用封装：

```kotlin
fun probeOnMainThread(
    ad: Any?,
    adUnitId: String?,
    callback: (AdMobPriceReflectionUtil.Result) -> Unit
) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        callback(AdMobPriceReflectionUtil.probe(ad, adUnitId))
    } else {
        Handler(Looper.getMainLooper()).post {
            callback(AdMobPriceReflectionUtil.probe(ad, adUnitId))
        }
    }
}
```

### 17.2 推荐调用时机

最稳妥的调用点是：

```text
广告加载成功
→ 业务状态切换为 Ready
→ 主线程调用 probe 一次
→ 缓存结果
```

不推荐：

- 加载请求发出后立刻调用；
- 对 `null` 不断轮询；
- 已释放广告上调用；
- 广告展示结束后继续用旧对象探测；
- RecyclerView 每次绑定或每帧重复探测；
- 多线程同时对同一对象反复探测。

### 17.3 缓存建议

同一已加载广告通常只需要探测一次：

```kotlin
data class LoadedAdEntry(
    val ad: Any,
    val adUnitId: String,
    val loadedAtElapsedMs: Long,
    val privatePriceResult: AdMobPriceReflectionUtil.Result
)
```

广告被展示、销毁、替换或过期后，应同时清理对应的候选结果。

## 18. 异常和降级

工具会捕获普通反射异常并转换为 `Result`。调用方应遵循：

```text
反射成功 → 可选使用候选信号
反射未命中 → 使用原有广告策略
反射失败 → 使用原有广告策略
线程错误 → 主线程重试一次或直接降级
类型不支持 → 不反射，继续原有流程
```

任何以下状态都不应阻止广告正常展示：

```text
AD_NOT_READY
WRONG_THREAD
UNSUPPORTED_SDK
VALUE_NOT_PRESENT
REFLECTION_FAILED
```

### 18.1 未命中不是零值

错误写法：

```kotlin
val micros = result.price?.valueMicros ?: 0L
```

这会把“没有拿到数据”和“Google 明确报告零值”混成同一个含义。

正确做法：

```kotlin
val micros: Long? = result.price?.valueMicros

when (result.status) {
    AdMobPriceReflectionUtil.Status.ZERO_VALUE_REPORTED -> {
        // 公开回调明确报告 0。
    }

    AdMobPriceReflectionUtil.Status.VALUE_NOT_PRESENT,
    AdMobPriceReflectionUtil.Status.REFLECTION_FAILED -> {
        // 没有可用价格，走无价格 fallback。
    }

    else -> Unit
}
```

## 19. 日志和数据安全

默认日志 TAG：

```text
AdMobPriceProbe
```

日志可能包括：

- 广告位 ID；
- 广告对象类名；
- `valueMicros`；
- 币种；
- eCPM；
- 精度；
- 私有字段路径；
- 扫描对象数量；
- 反射异常数量。

迁移到其他项目时应明确：

1. Release 是否允许输出这些字段；
2. 是否要对广告位 ID 脱敏；
3. 是否只在内部测试版本启用完整日志；
4. 日志是否会被第三方日志平台采集；
5. 测试广告和正式广告是否分流。

工具不主动上传数据，但接入的日志系统可能会上传。

## 20. R8、混淆和 Release 验证

固定路径依赖 SDK 私有字段名。Debug 成功不能替代 Release 验证。

每次发布前至少验证：

- Debug APK 的命中情况；
- Release 或与线上相同混淆配置 APK 的命中情况；
- 五种实际使用广告类型；
- Google 示例广告位回调；
- 至少一个正式广告位的 paid 回调；
- `VALUE_NOT_PRESENT` 和 `REFLECTION_FAILED` 不影响展示。

本文没有验证一组适用于所有项目和所有 SDK 版本的通用 `-keep` 规则，因此不建议盲目保留整个 Google Ads SDK 私有实现。

如果 Release 与 Debug 行为不同，应先记录：

```text
SDK 版本
构建类型
广告类型
adClassName
status
matchedPath
reflectionErrors
visitedObjects
设备型号
Android 版本
```

再决定是否调整混淆配置。

## 21. SDK 升级检查清单

升级 `ads-mobile-sdk` 后不要只做编译检查，应执行：

1. 确认五种 import 仍然存在；
2. 确认 `AdValue` 属性仍能编译；
3. 确认 `PrecisionType` 枚举映射仍完整；
4. 确认 `AdValue` 构造器仍可调用；
5. 确认各广告类型仍有 `adEventCallback`；
6. 确认 `onAdPaid(AdValue)` 签名未变；
7. 真机检查固定路径是否命中；
8. 真机检查递归扫描是否命中；
9. 对照同一广告的展示前候选和 paid 值；
10. 检查扫描耗时和 `visitedObjects`；
11. 检查反射异常是否明显增加；
12. 验证 Release 构建；
13. 验证反射失败不会影响展示；
14. 记录验证结论，不根据字段名猜测新增路径。

只有通过新版本真机验证后，才能在自己的兼容矩阵中标记为“已验证”。

## 22. 推荐的独立封装

以下封装不依赖任何具体广告管理框架：

```kotlin
class AdPriceInspector(
    private val onObservation: (AdMobPriceReflectionUtil.Result) -> Unit
) {

    @MainThread
    fun onAdReady(
        ad: Any,
        adUnitId: String
    ): AdMobPriceReflectionUtil.Result {
        val result = AdMobPriceReflectionUtil.probe(ad, adUnitId)
        onObservation(result)
        return result
    }

    fun onAdNotReady(
        adUnitId: String?
    ): AdMobPriceReflectionUtil.Result {
        val result = AdMobPriceReflectionUtil.notReady(adUnitId)
        onObservation(result)
        return result
    }

    fun onPaid(
        adValue: AdValue,
        adUnitId: String
    ): AdMobPriceReflectionUtil.Result {
        val result = AdMobPriceReflectionUtil.inspectPaidEvent(
            adValue,
            adUnitId
        )
        onObservation(result)
        return result
    }
}
```

初始化：

```kotlin
val priceInspector = AdPriceInspector { result ->
    when (result.status) {
        AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED -> {
            savePreShowObservation(result)
        }

        AdMobPriceReflectionUtil.Status.FOUND,
        AdMobPriceReflectionUtil.Status.ZERO_VALUE_REPORTED,
        AdMobPriceReflectionUtil.Status.GOOGLE_TEST_VALUE -> {
            savePaidObservation(result)
        }

        else -> {
            saveDiagnosticStatus(result)
        }
    }
}
```

加载成功：

```kotlin
@MainThread
fun onLoaded(ad: Any, adUnitId: String) {
    val preShowResult = priceInspector.onAdReady(ad, adUnitId)

    val privateCandidate = preShowResult.price?.takeIf {
        preShowResult.status ==
            AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED
    }

    // 缓存广告对象与候选；不把候选作为最终收益。
    cacheLoadedAd(ad, adUnitId, privateCandidate)
}
```

paid 回调：

```kotlin
override fun onAdPaid(adValue: AdValue) {
    priceInspector.onPaid(adValue, adUnitId)

    // 正式链路仍使用原始 adValue。
    reportOfficialRevenue(adValue)
}
```

## 23. 测试方案

### 23.1 编译测试

确认：

- 工具类包名正确；
- 已移除原工程日志 import；
- GMA 依赖能够解析；
- 五种广告类型 import 能够编译；
- `AdValue` 和 `PrecisionType` 能够编译。

### 23.2 状态测试

至少覆盖：

| 场景 | 预期状态 |
|---|---|
| `probe(null, adUnitId)` | `AD_NOT_READY` |
| 后台线程调用 `probe(validAd)` | `WRONG_THREAD` |
| 传普通业务对象 | `UNSUPPORTED_SDK` |
| 私有候选命中 | `PRIVATE_VALUE_UNVERIFIED` |
| 无候选且无读取异常 | `VALUE_NOT_PRESENT` |
| 无候选且存在读取异常 | `REFLECTION_FAILED` |
| 示例广告位 paid 回调 | `GOOGLE_TEST_VALUE` |
| 正式广告 paid 正值 | `FOUND` |
| 正式广告 paid 零值 | `ZERO_VALUE_REPORTED` |

其中私有候选是否能命中必须真机观察，不能通过文档保证。

### 23.3 对照测试

对每个广告类型记录：

```text
广告类型
SDK 版本
广告位
加载时间
probe status
probe valueMicros
probe currency
probe source
probe confidence
probe matchedPath
paid status
paid valueMicros
paid currency
```

对照时只比较：

- 同一个广告实例；
- 同一个加载周期；
- 相同币种；
- 非测试广告；
- 明确区分未命中和零值。

## 24. 常见错误

### 24.1 用 `isFound` 判断展示前候选

错误：

```kotlin
val result = AdMobPriceReflectionUtil.probe(ad, adUnitId)
if (result.isFound) {
    use(result.price)
}
```

正确：

```kotlin
if (
    result.status ==
        AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED &&
    result.price != null
) {
    useAsUnverifiedCandidate(result.price)
}
```

### 24.2 把候选值上报成正式收益

错误：

```kotlin
reportRevenue(result.price!!.valueMicros)
```

正确：

```kotlin
override fun onAdPaid(adValue: AdValue) {
    AdMobPriceReflectionUtil.inspectPaidEvent(adValue, adUnitId)
    reportOfficialRevenue(adValue)
}
```

### 24.3 不传 `adUnitId`

`probe()` 允许省略 `adUnitId`，但会失去：

- 示例广告位识别；
- 广告位维度诊断；
- 与 paid 结果关联的关键字段。

实际接入应尽量传入。

### 24.4 后台线程直接调用 `probe()`

结果会是 `WRONG_THREAD`，不会执行反射扫描。应切主线程。

### 24.5 把包装对象传给工具

即使包装对象内部持有 `InterstitialAd`，只要传入对象本身不是五种支持类型，仍会返回 `UNSUPPORTED_SDK`。

### 24.6 不同币种直接比较

错误：

```kotlin
usdMicros > eurMicros
```

正确做法是相同币种才直接比较。若要跨币种比较，需要业务方自己的、带时间和来源的汇率系统；该工具不提供汇率换算。

### 24.7 每次需要价格都重新反射

同一广告对象建议加载完成后探测一次并缓存结果。重复扫描不会把私有候选变成公开真值。

### 24.8 用 `message` 做状态判断

错误：

```kotlin
if (result.message.contains("命中")) { ... }
```

正确：

```kotlin
if (
    result.status ==
        AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED
) {
    // ...
}
```

## 25. 上线前检查清单

### 依赖与源码

- [ ] 使用 Next-Gen `ads-mobile-sdk`；
- [ ] 已记录实际 SDK 版本；
- [ ] 工具类 package 已修改；
- [ ] 已移除 `com.xm.framework.ext.util.*` 日志依赖；
- [ ] 工具类能够独立编译；
- [ ] 未把 Legacy 广告对象传给工具。

### 调用位置

- [ ] 只在广告加载成功后调用 `probe()`；
- [ ] `probe()` 在主线程；
- [ ] 传入真实广告对象；
- [ ] 传入真实 `adUnitId`；
- [ ] 同一广告通常只探测一次；
- [ ] 广告失效时同步清理候选缓存。

### 结果处理

- [ ] 展示前命中判断 `PRIVATE_VALUE_UNVERIFIED`；
- [ ] 没有用 `isFound` 判断展示前候选；
- [ ] 没有把未命中解释成 0；
- [ ] 没有把私有候选作为最终收益；
- [ ] 不同币种没有直接比较；
- [ ] 所有失败状态都有 fallback。

### paid 回调

- [ ] 在同一个业务 callback 中处理 `onAdPaid`；
- [ ] 没有为了诊断覆盖已有 callback；
- [ ] 展示级收入上报继续使用原始 `AdValue`；
- [ ] Google 示例广告不进入正式统计；
- [ ] 诊断失败不会阻断正式收益上报。

### 发布验证

- [ ] 已验证 Debug；
- [ ] 已验证 Release/混淆包；
- [ ] 已验证实际使用的每种广告类型；
- [ ] 已记录 `matchedPath` 和 `reflectionErrors`；
- [ ] 已完成同一广告的 pre-show / paid 对照；
- [ ] 已确认生产日志策略。

## 26. 最终结论

在任意项目中正确使用 `AdMobPriceReflectionUtil`，核心只有两条：

### 展示前

```kotlin
val result = AdMobPriceReflectionUtil.probe(ad, adUnitId)

if (
    result.status ==
        AdMobPriceReflectionUtil.Status.PRIVATE_VALUE_UNVERIFIED &&
    result.price != null
) {
    // 只能作为未验证候选。
}
```

### 曝光后

```kotlin
override fun onAdPaid(adValue: AdValue) {
    val diagnosis =
        AdMobPriceReflectionUtil.inspectPaidEvent(adValue, adUnitId)

    // 正式收益仍使用 Google 回调原始值。
    reportOfficialRevenue(adValue)
}
```

工具的价值是：

- 在广告展示前提供一个可观察的私有候选信号；
- 在广告展示后把公开 paid 值转换成统一诊断结构；
- 帮助开发者对照两者，而不是把两者混为一谈。

无法由当前实现证明的内容必须保持为未知：

- 某个私有候选是否就是最终获胜价格；
- 私有字段路径是否能跨 SDK 版本稳定；
- 某次未命中是否意味着内部没有价格；
- 候选排序是否一定提升真实收益。

这些结论只能通过目标项目、目标 SDK 版本、目标构建类型下的真机数据验证，不能靠推测补全。

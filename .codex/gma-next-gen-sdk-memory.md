# GMA 新一代 SDK 记忆笔记

## 文档主题

Android 平台接入 GMA Next-Gen SDK 的基础流程：满足前置条件、在 Gradle 中接入依赖、正确初始化 SDK，然后再选择广告格式继续植入。

## 一句话记忆

先满足版本要求并注册 AdMob 应用，再添加 `ads-mobile-sdk` 依赖，在后台线程调用 `MobileAds.initialize()`，之后才能安全加载广告。

## 使用目标

- 将 GMA Next-Gen SDK 集成到 Android 应用。
- 为后续接入横幅、插页式、原生、激励、插页式激励、开屏广告做准备。
- 避免因为初始化时机错误导致 ANR 或 `UninitializedPropertyAccessException`。

## 前提条件

- `minSdk` 需要是 `24` 或更高。
- `compileSdk` 需要是 `35` 或更高。
- Kotlin 应用最低 Kotlin 版本需要是 `1.9`。
- 需要先在 AdMob 中注册应用，并拿到唯一的 AdMob App ID。

## Gradle 配置要点

### 仓库配置

Gradle 设置文件中需要包含：

- `google()`
- `mavenCentral()`
- `gradlePluginPortal()`

其中依赖解析至少要能从 `google()` 和 `mavenCentral()` 拉取。

### SDK 依赖

应用级 `build.gradle(.kts)` 需要添加：

```kotlin
dependencies {
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1")
}
```

## 初始化规则

### 核心原则

- 初始化完成前，不要加载广告，也不要调用其他 `MobileAds` 方法。
- 唯一例外要以 API 文档明确说明为准，例如 `getVersion()`。
- 如果未初始化就调用相关能力，可能抛出 `UninitializedPropertyAccessException`。

### 正确做法

- 调用 `MobileAds.initialize()`。
- 必须放到后台线程执行，否则可能导致 ANR。
- 该初始化通常在应用启动时执行一次即可。

### 推荐初始化示意

```kotlin
val backgroundScope = CoroutineScope(Dispatchers.IO)
backgroundScope.launch {
    MobileAds.initialize(
        this@MainActivity,
        InitializationConfig.Builder("YOUR_ADMOB_APP_ID").build()
    ) {
        // Adapter initialization is complete.
    }
    // SDK initialization is complete.
}
```

## 初始化完成时机

- 完成回调会在 GMA Next-Gen SDK 和适配器初始化完成后触发。
- 如果超过 `30` 秒，也会超时返回。
- 如果不需要等待竞价适配器全部完成，可以在 SDK 初始化完成后开始加载广告。
- 如果使用的是 AdMob 中介，应该等待完成回调后再加载广告，确保所有中介适配器初始化完毕。

## 合规与特殊注意事项

### UMP / App ID

- 如果使用 Google UMP SDK，需要在 `AndroidManifest.xml` 中配置应用 ID。
- 文档特别提醒要参考“添加应用 ID”的说明。

### 同意与定向标志

- 初始化时，GMA SDK 或中介合作伙伴 SDK 可能会预加载广告。
- 如果涉及 EEA、英国、瑞士用户同意流程，需要在初始化前处理好相关事项。
- 若要设置特定请求标志，例如：
  - `RequestConfiguration.TagForChildDirectedTreatment`
  - `RequestConfiguration.TagForUnderAgeOfConsent`
- 这些也应在初始化 GMA SDK 之前完成。

## 广告格式选择入口

SDK 集成完成后，可以按业务场景继续接入：

- 横幅广告：占据部分页面区域，自动刷新，接入最简单。
- 插页式广告：全屏广告，适合自然停顿点或页面切换点。
- 原生广告：可自定义样式，与应用 UI 融合度最高。
- 激励广告：用户主动观看广告以换取奖励。
- 插页式激励广告：自动展示，但需要先给用户奖励说明和退出选择。
- 开屏广告：在应用启动或切回前台时展示。

## 开屏广告专项记忆

### 适用场景

- 开屏广告适合在应用启动或从后台切回前台时展示。
- 最适合与加载屏幕搭配使用，用来对等待加载的时间进行变现。
- 用户可以随时关闭开屏广告。

### 测试广告要求

- 开发和测试阶段必须使用测试广告，不能直接用真实广告位。
- Android 开屏广告测试广告单元 ID 是：`ca-app-pub-3940256099942544/9257395921`
- 发布前必须替换成自己的正式广告单元 ID。
- 如果测试阶段错误使用真实广告，请求可能导致账号风险甚至被暂停。

### 模式一：手动加载单个开屏广告

这是最直接的方式，适合先跑通最小闭环。

#### 加载

```kotlin
AppOpenAd.load(
    AdRequest.Builder(adUnitId).build(),
    object : AdLoadCallback<AppOpenAd> {
        override fun onAdLoaded(ad: AppOpenAd) {
            appOpenAd = ad
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.e(TAG, "App open ad failed to load: ${adError.message}")
            appOpenAd = null
        }
    },
)
```

#### 展示

```kotlin
private fun showAd(appOpenAd: AppOpenAd, activity: Activity) {
    appOpenAd.show(activity)
}
```

#### 事件监听

- 展示前应先注册 `adEventCallback`。
- 至少要处理展示成功、关闭、展示失败、曝光、点击等事件。
- 在 `onAdDismissedFullScreenContent()` 中通常要把 `appOpenAd = null`，避免重复使用旧实例。

### 模式二：广告预加载（Beta）

文档同时提到，若想优化广告延迟，可考虑改用预加载模式。

#### 预加载优势

- SDK 自动完成广告加载和缓存。
- 在广告被取出后自动补下一条广告。
- 加载失败时会托管重试。
- 会处理广告到期刷新。
- 会对缓存顺序做优化，优先保留更值得投放的广告。

#### 开始预加载

- 应用启动时调用一次 `AppOpenAdPreloader.start()`。
- 可以直接用广告单元 ID 作为 `preloadId`。
- 如果同一广告单元需要多套定向配置，可以传自定义字符串作为 `preloadId`。

```kotlin
val adRequest = AdRequest.Builder(adUnitId).build()
val preloadConfig = PreloadConfiguration(adRequest)
AppOpenAdPreloader.start(adUnitId, preloadConfig)
```

#### 获取并展示预加载广告

- 在准备展示广告时调用 `AppOpenAdPreloader.pollAd(preloadId)`。
- `pollAd()` 会取出当前可用广告，并在后台自动继续预加载下一个广告。
- 如果当前没有广告可用，返回 `null`。
- 在真正准备展示前，不要提前调用 `pollAd()`，否则会把缓存里的下一条广告取走。

```kotlin
private fun pollAndShowAd(activity: Activity, adUnitId: String) {
    val ad = AppOpenAdPreloader.pollAd(adUnitId)
    if (ad == null) {
        Log.e(TAG, "App open ad is not available.")
        return
    }

    ad.adEventCallback = object : AppOpenAdEventCallback {
        override fun onAdImpression() {
            Log.d(TAG, "App open ad recorded an impression.")
        }
    }
    ad.show(activity)
}
```

#### 预加载事件监听

- 可选注册 `PreloadCallback`。
- 可感知三类状态：预加载成功、预加载失败、缓存耗尽。
- 即使失败，SDK 也会根据预加载配置自动继续重试。

#### 广告可用性检查

```kotlin
private fun isAdAvailable(adUnitId: String): Boolean {
    return AppOpenAdPreloader.isAdAvailable(adUnitId)
}
```

#### 缓冲区大小

- `PreloadConfiguration` 可设置 `bufferSize`。
- 文档建议每个 `preloadId` 维持 `2` 条预加载广告。
- 默认由 Google 自动平衡内存占用与广告延迟。

```kotlin
val adRequest = AdRequest.Builder(adUnitId).build()
val preloadConfig = PreloadConfiguration(adRequest, bufferSize = 2)
AppOpenAdPreloader.start(adUnitId, preloadConfig)
```

#### 应用级缓存限制

- 整个应用默认最多缓存 `6` 条预加载广告。
- 该限制跨广告格式和跨 `preloadId` 生效。
- 如果业务确实需要更高限制，需要联系客户经理申请。

#### 停止预加载

- 如果某个 `preloadId` 在当前会话不再需要，可以调用 `destroy()`。
- 这会停止该预加载 ID 的预加载并清空对应缓存。

```kotlin
private fun stopPreloading(adUnitId: String) {
    AppOpenAdPreloader.destroy(adUnitId)
}
```

#### 仅读取响应信息

- 如果只想读取下一个预加载广告的响应信息，而不消耗缓存，可以使用 `peekAdResponseInfo(preloadId)`。
- 这和 `pollAd()` 的区别是不会把广告从缓存中取出。

### 冷启动与加载屏幕约束

- 冷启动时没有现成预加载广告可立即展示。
- 温启动时，可能已有预加载广告可直接使用。
- 冷启动阶段只能在“应用资源仍在加载”的前提下，从加载屏幕中展示开屏广告。
- 如果资源已经加载完成、用户已经到达主要内容，而广告这时才加载完，就不要再展示。
- 广告展示期间，应用资源仍应继续在后台加载。

### 开屏广告最佳实践

- 不要在用户首次启动应用时就展示第一个开屏广告。
- 仅在用户等待应用加载时展示开屏广告。
- 如果加载屏幕在广告展示期间完成，应在广告关闭回调里关闭加载屏幕。
- 开屏广告加载后 `4` 小时会过期，不要展示已超过 `4` 小时的广告。

### 开屏广告易错点

- 测试阶段直接使用真实广告单元 ID。
- 冷启动时，主内容已出现后仍强行弹开屏广告。
- 提前调用 `pollAd()`，导致缓存广告被错误取走。
- 广告关闭后没有清理引用，继续使用旧对象。
- 忽略广告 `4` 小时有效期，展示过期广告。

## 横幅广告专项记忆

### 适用场景

- 横幅广告是占据页面部分区域的矩形广告。
- 锚定自适应横幅广告会固定在屏幕顶部或底部，并在用户与应用交互时持续停留。
- 它适合对常驻页面进行轻量变现，也是最容易接入的广告形式之一。

### 测试广告要求

- 开发和测试阶段必须使用测试广告位。
- Android 横幅广告测试广告单元 ID 是：`ca-app-pub-3940256099942544/9214589741`
- 发布前必须替换成自己的正式广告单元 ID。

### 创建 AdView

- 横幅广告通过 `AdView` 承载。
- 常见做法是在 XML 布局中放置 `AdView`，并把它约束在底部或顶部。
- 布局示例里 `AdView` 的宽高通常先写 `wrap_content`，真正展示尺寸由广告请求中的 `AdSize` 决定。

```xml
<com.google.android.libraries.ads.mobile.sdk.banner.AdView
    android:id="@+id/adView"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent" />
```

### 加载横幅广告

- 需要先构造锚定自适应横幅尺寸 `AdSize`。
- 然后使用 `BannerAdRequest.Builder(adUnitId, adSize)` 构造请求。
- 最后通过 `adView.loadAd(...)` 发起加载。

```kotlin
private fun loadBannerAd(adView: AdView, activity: Activity) {
    val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, 360)
    val adRequest = BannerAdRequest.Builder(AD_UNIT_ID, adSize).build()

    adView.loadAd(
        adRequest,
        object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                Log.d(TAG, "Banner ad loaded.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Banner ad failed to load: $adError")
            }
        },
    )
}
```

### 锚定自适应横幅的关键点

- 文档示例使用 `AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, 360)`。
- 这里的 `360` 表示横幅目标宽度示例值。
- 实际接入时，应根据设备可用宽度选择合适尺寸，而不是盲目写死。

### 横幅刷新策略

- 如果你已经在 AdMob 后台给该广告单元配置了自动刷新，广告加载失败后不需要立刻自己再次请求。
- GMA SDK 会按 AdMob 后台设置的刷新频率自动刷新。
- 自动刷新只有在横幅广告实际显示在屏幕上时才会生效。
- 如果没有启用刷新功能，才需要你自己重新发起请求。

### 释放横幅资源

- 横幅广告使用完后，要从视图层级里移除。
- 然后调用 `adView.destroy()` 释放资源。
- 最后把本地引用清空，防止内存泄漏或继续误用旧对象。

```kotlin
val parentView = adView?.parent
if (parentView is ViewGroup) {
    parentView.removeView(adView)
}

adView?.destroy()
adView = null
```

### 广告事件监听

- 建议在广告展示前就设置广告事件回调。
- 横幅广告可监听的关键事件包括：曝光、点击、全屏内容展示、全屏内容关闭、全屏内容展示失败。
- 这里的“全屏内容”通常对应用户点击横幅后跳出的全屏落地页等行为。

```kotlin
override fun onAdLoaded(ad: BannerAd) {
    ad.adEventCallback = object : BannerAdEventCallback {
        override fun onAdImpression() {}
        override fun onAdClicked() {}
        override fun onAdShowedFullScreenContent() {}
        override fun onAdDismissedFullScreenContent() {}
        override fun onAdFailedToShowFullScreenContent(
            fullScreenContentError: FullScreenContentError
        ) {}
    }
}
```

### 自动刷新回调

- 如果横幅广告启用了自动刷新，可以设置 `bannerAdRefreshCallback`。
- 应在把广告视图加入视图层级前设置好回调。
- 可监听刷新成功和刷新失败。

### 视频横幅与硬件加速

- 如果横幅中包含视频广告，硬件加速必须启用，否则广告可能无法正常展示。
- 硬件加速默认是开启的，但有些应用会全局或局部关闭。
- 如果某个展示广告的 `Activity` 关闭了硬件加速，就不能再针对具体广告视图单独补开，所以必须至少为该 `Activity` 启用。

```xml
<application android:hardwareAccelerated="true">
    <activity android:hardwareAccelerated="true" />
    <activity android:hardwareAccelerated="false" />
</application>
```

### 横幅广告易错点

- 测试阶段使用真实横幅广告位。
- `AdView` 加到了布局里，但没有按正确 `AdSize` 发请求。
- 明明后台已启用自动刷新，却在失败后手动疯狂重试。
- 页面销毁或移除横幅时没有调用 `destroy()`。
- 展示广告的 `Activity` 关闭了硬件加速，导致视频横幅异常。

## 插页式广告专项记忆

### 适用场景

- 插页式广告是覆盖整个应用界面的全屏广告。
- 最适合放在自然过渡点，例如页面切换、流程节点结束、游戏关卡间隙。
- 不适合在用户正在连续操作的核心流程中生硬打断。

### 测试广告要求

- 开发和测试阶段必须使用测试广告。
- Android 插页式广告测试广告单元 ID 是：`ca-app-pub-3940256099942544/1033173712`
- 发布前必须替换为正式广告单元 ID。

### 模式一：手动加载单个插页式广告

- 在 SDK 初始化完成后，调用 `InterstitialAd.load()`。
- 加载成功后保存广告实例，失败时记录错误并清空引用。

```kotlin
InterstitialAd.load(
    AdRequest.Builder(adUnitId).build(),
    object : AdLoadCallback<InterstitialAd> {
        override fun onAdLoaded(ad: InterstitialAd) {
            interstitialAd = ad
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
            interstitialAd = null
        }
    },
)
```

### 展示广告

```kotlin
private fun showAd(interstitialAd: InterstitialAd, activity: Activity) {
    interstitialAd.show(activity)
}
```

### 广告事件监听

- 展示前应先设置 `ad.adEventCallback`。
- 至少要处理展示成功、关闭、展示失败、曝光、点击。
- 在 `onAdDismissedFullScreenContent()` 中通常要把 `interstitialAd = null`，避免重复使用已消费实例。

```kotlin
val ad = interstitialAd
if (ad == null) {
    Log.e(TAG, "Interstitial ad is not ready yet.")
    return
}

ad.adEventCallback = object : InterstitialAdEventCallback {
    override fun onAdShowedFullScreenContent() {}
    override fun onAdDismissedFullScreenContent() {
        interstitialAd = null
    }
    override fun onAdFailedToShowFullScreenContent(
        fullScreenContentError: FullScreenContentError
    ) {}
    override fun onAdImpression() {}
    override fun onAdClicked() {}
}
```

### 模式二：广告预加载（Beta）

- 如果想优化广告延迟，可以使用 `InterstitialAdPreloader`。
- SDK 会自动执行缓存、失败重试、广告补货和到期处理。

#### 开始预加载

- SDK 初始化后调用一次 `InterstitialAdPreloader.start()`。
- 文档建议每种格式只预加载一个广告单元，以优化性能。
- 可以用广告单元 ID 作为 `preloadId`，也可以自定义字符串来标识不同定向配置。

```kotlin
private fun startPreloading(adUnitId: String) {
    val adRequest = AdRequest.Builder(adUnitId).build()
    val preloadConfig = PreloadConfiguration(adRequest)
    InterstitialAdPreloader.start(adUnitId, preloadConfig)
}
```

#### 获取并展示预加载广告

- 真正准备展示时调用 `InterstitialAdPreloader.pollAd(preloadId)`。
- `pollAd()` 会返回当前可用广告，并在后台自动继续加载下一条。
- 如果没有广告可用，返回 `null`。
- 不要在准备展示前提前调用 `pollAd()`。

```kotlin
private fun pollAndShowAd(activity: Activity, adUnitId: String) {
    val ad = InterstitialAdPreloader.pollAd(adUnitId)
    if (ad == null) {
        Log.e(TAG, "Interstitial ad is not available.")
        return
    }

    ad.adEventCallback = object : InterstitialAdEventCallback {
        override fun onAdImpression() {
            Log.d(TAG, "Interstitial ad recorded an impression.")
        }
    }
    ad.show(activity)
}
```

#### 预加载事件监听

- 可以注册 `PreloadCallback`，感知预加载成功、失败、缓存耗尽。
- 文档特别强调：不要在 `PreloadCallback` 内部调用 `start()` 或 `pollAd()`。
- 尤其不要在 `onAdsExhausted()` 中再次触发这些调用。

#### 广告可用性检查

```kotlin
private fun isAdAvailable(adUnitId: String): Boolean {
    return InterstitialAdPreloader.isAdAvailable(adUnitId)
}
```

#### 缓冲区大小与缓存限制

- `PreloadConfiguration` 支持自定义 `bufferSize`。
- 文档建议每个 `preloadId` 缓冲 `2` 条广告。
- 整个应用默认最多缓存 `6` 条预加载广告，且该限制跨广告格式与 `preloadId` 生效。

```kotlin
private fun setBufferSize(adUnitId: String) {
    val adRequest = AdRequest.Builder(adUnitId).build()
    val preloadConfig = PreloadConfiguration(adRequest, bufferSize = 2)
    InterstitialAdPreloader.start(adUnitId, preloadConfig)
}
```

#### 停止预加载

- 当前会话不再需要时，可以调用 `InterstitialAdPreloader.destroy(preloadId)`。
- 这会停止预加载并移除该 `preloadId` 对应的缓存广告。

```kotlin
private fun stopPreloading(adUnitId: String) {
    InterstitialAdPreloader.destroy(adUnitId)
}
```

#### 仅读取响应信息

- 如果只想读取响应信息而不消耗缓存，可使用 `InterstitialAdPreloader.peekAdResponseInfo(preloadId)`。
- 这和 `pollAd()` 的关键区别是不会把广告对象从缓存里取出。

### 插页式广告易错点

- 测试阶段用真实插页式广告位。
- 广告还没准备好就直接 `show()`。
- 广告关闭后没有清空引用，重复展示旧对象。
- 把插页式广告放在生硬、打断感很强的时机。
- 在 `PreloadCallback` 里再次调用 `start()` 或 `pollAd()`，造成流程混乱。

## 原生广告专项记忆

### 适用场景

- 原生广告使用应用原本就有的界面组件来展示广告素材。
- SDK 负责把广告素材加载回来，但广告的最终展示、绑定和布局由应用自己负责。
- 它最适合需要高度融入页面视觉设计的场景，例如信息流、内容流、推荐列表。

### 前提与测试广告

- 使用前必须先初始化 GMA Next-Gen SDK。
- 文档要求 GMA Next-Gen SDK 版本至少为 `1.0.0`。
- 原生广告测试广告单元 ID：`ca-app-pub-3940256099942544/2247696110`
- 原生视频广告测试广告单元 ID：`ca-app-pub-3940256099942544/1044960115`
- 测试阶段必须使用测试广告位，发布前再替换为正式广告位。

### 核心理解

- 原生广告接入分两步：先加载广告，再展示广告。
- `NativeAdLoader.load()` 只负责把 `NativeAd` 对象取回来。
- 真正的 UI 呈现、素材绑定、视图注册、显示隐藏控制都在应用侧完成。

### 加载原生广告

- 使用 `NativeAdRequest.Builder(adUnitId, adTypes)` 构建请求。
- 通过 `NativeAdLoader.load(adRequest, adCallback)` 发起加载。
- 常见类型是 `listOf(NativeAd.NativeAdType.NATIVE)`。

```kotlin
private fun loadAd() {
    val adRequest = NativeAdRequest
        .Builder(AD_UNIT_ID, listOf(NativeAd.NativeAdType.NATIVE))
        .build()

    val adCallback =
        object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                // Called when a native ad has loaded.
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                // Called when a native ad has failed to load.
            }
        }

    NativeAdLoader.load(adRequest, adCallback)
}
```

### 原生广告视图容器

- 原生广告的顶级容器必须是 `NativeAdView`。
- 各个素材视图，例如标题、图标、媒体、按钮等，都必须是 `NativeAdView` 的子视图。
- 如果素材资源呈现在 `NativeAdView` 外部，SDK 会尝试打印警告日志。

```xml
<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout android:orientation="vertical">
        <LinearLayout android:orientation="horizontal">
            <ImageView android:id="@+id/ad_app_icon" />
            <TextView android:id="@+id/ad_headline" />
        </LinearLayout>
    </LinearLayout>
</com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView>
```

### 处理已加载的原生广告

- 在 `onNativeAdLoaded` 中，把广告绑定到自定义布局。
- 先填充素材，再把新的 `NativeAdView` 放进页面占位容器。
- 如果页面里已有旧广告视图，应先移除旧视图再添加新视图。

```kotlin
override fun onNativeAdLoaded(nativeAd: NativeAd) {
    activity?.runOnUiThread {
        val nativeAdBinding = NativeAdBinding.inflate(layoutInflater)
        val adView = nativeAdBinding.root
        val frameLayout = myActivityLayout.nativeAdPlaceholder

        displayNativeAd(nativeAd, nativeAdBinding)

        frameLayout.removeAllViews()
        frameLayout.addView(adView)
    }
}
```

### 展示与注册素材视图

- 需要先把 `NativeAdView` 的各个素材视图属性绑定好，例如：
  - `headlineView`
  - `bodyView`
  - `callToActionView`
  - `iconView`
  - `priceView`
  - `starRatingView`
  - `storeView`
  - `advertiserView`
- 然后把 `NativeAd` 的素材内容填入这些视图。
- 对没有数据的素材要隐藏对应视图。
- 最后调用 `registerNativeAd(nativeAd, mediaView)`，通知 SDK 你已经完成素材注册。

```kotlin
private fun displayNativeAd(nativeAd: NativeAd, nativeAdBinding: NativeAdBinding) {
    val nativeAdView = nativeAdBinding.root
    nativeAdView.advertiserView = nativeAdBinding.adAdvertiser
    nativeAdView.bodyView = nativeAdBinding.adBody
    nativeAdView.callToActionView = nativeAdBinding.adCallToAction
    nativeAdView.headlineView = nativeAdBinding.adHeadline
    nativeAdView.iconView = nativeAdBinding.adAppIcon
    nativeAdView.priceView = nativeAdBinding.adPrice
    nativeAdView.starRatingView = nativeAdBinding.adStars
    nativeAdView.storeView = nativeAdBinding.adStore

    nativeAdBinding.adHeadline.text = nativeAd.headline
    nativeAdBinding.adBody.text = nativeAd.body

    nativeAdView.registerNativeAd(nativeAd, nativeAdBinding.adMedia)
}
```

### `registerNativeAd()` 的意义

- 正确注册素材视图后，SDK 才能自动处理：
  - 点击统计
  - 曝光统计
  - 广告选项叠加层
  - 原生广告展示行为
- 如果不想使用 `MediaView`，调用 `registerNativeAd()` 时可以给媒体参数传 `null`。

### 原生广告事件回调

- 在 `onNativeAdLoaded` 后，可为 `NativeAd` 设置 `NativeAdEventCallback`。
- 常用事件包括：全屏内容展示、关闭、展示失败、曝光、点击。

### 可选：加载多个原生广告

- 可通过 `NativeAdLoader.load(adRequest, numberOfAds, adCallback)` 一次请求多个广告。
- `numberOfAds` 最大为 `5`。
- SDK 可能不会严格返回你请求的固定数量。
- 多广告请求不适用于配置为中介的广告单元 ID，因此使用中介时不要这样调。

```kotlin
NativeAdLoader.load(adRequest, 3, adCallback)
```

### 缓存与预缓存最佳实践

- 列表类页面可以预缓存原生广告列表。
- 预缓存的广告建议在 `1` 小时后清除并重新加载。
- 只缓存真正需要的原生广告，例如当前屏幕即将显示的少量条目。
- 原生广告内存占用较高，缓存过多且不销毁容易造成内存压力。

### 销毁要求

- 不再使用原生广告时，必须调用 `nativeAd.destroy()`。
- 原生广告尤其要重视销毁，否则容易出现内存泄漏或资源占用过高。

```kotlin
nativeAd.destroy()
```

### 点击处理规则

- 不要在原生广告视图之上或内部自行实现自定义点击处理。
- 只要素材视图注册正确，SDK 会自动处理点击。
- 如果需要监听点击，应通过 `NativeAdEventCallback.onAdClicked()` 来做。

### 广告选项叠加层与广告标示

- SDK 会自动向广告视图中添加广告选项叠加层。
- 需要在原生广告视图的某个角落留出可见空间，保证叠加层清晰可辨。
- 还必须显示广告标示，明确告诉用户这是广告。

### MediaView 与媒体内容

- `MediaView` 用于展示图片或视频素材。
- 如果广告有视频，SDK 会缓冲并在 `MediaView` 内播放。
- 如果没有视频，则会使用主图素材。
- 可通过 `setImageScaleType()` 调整图片缩放方式，例如 `CENTER_CROP`。

### 视频原生广告与硬件加速

- 如果原生广告里包含视频，展示广告的 `Activity` 必须启用硬件加速。
- 如果 `Activity` 级别关闭了硬件加速，就不能仅靠单个广告视图补救。

### 可选：广告素材摘要 `creativeSummary`

- `creativeSummary` 是一个受限功能，需要向客户经理申请权限。
- 如果你直接展示 SDK 提供的原始 `creativeSummary` 文本，可以把对应 `TextView` 注册到 `creativeSummaryView`，这样 SDK 才能自动跟踪点击。
- 如果你对摘要做了改写、总结或拼接自定义对话文本，这些修改后的文本必须放在 `NativeAdView` 容器之外。
- `creativeSummary` 和 `creativeSummaryView` API 需要 `@OptIn(ExperimentalApi::class)`。

### 原生广告易错点

- 只加载了 `NativeAd`，却没有把素材正确绑定到 `NativeAdView`。
- 把广告素材放到了 `NativeAdView` 外部。
- 某些素材为空时仍强行展示对应视图，导致 UI 空洞或错乱。
- 自己在广告视图上拦截点击，破坏 SDK 自动处理。
- 列表里缓存太多原生广告，且长期不销毁，造成内存压力。
- 不再使用广告后忘记调用 `nativeAd.destroy()`。
- 视频原生广告所在 `Activity` 关闭了硬件加速。

## 项目广告配置与调度规则

### 规则定位

- 这一部分是项目自身的广告配置、缓存、重试与调度规则。
- 实现广告模块时，除了遵守 GMA SDK 的格式规则，还必须遵守这里的场景级约束。

### 配置结构

- 广告配置以“广告场景”为一级 key。
- 每个场景下对应一个广告配置数组。
- 数组中的每一项代表该场景下的一个候选广告位，用于失败回退。

示例场景包括：

- `ac_launch`
- `ac_backscan_int`
- `ac_result_int`
- `ac_main_nat`
- `ac_main_banner`
- `ac_scan_nat`
- `ac_result_nat`

### 配置字段含义

- `ad_unit_id`：广告位 ID。
- `ad_paltfrom`：广告平台，当前示例值为 `admob`。
- `ad_type`：广告类型缩写。
- `ad_timelimit`：广告缓存过期时长。

### 广告类型缩写映射

- `op`：开屏广告。
- `int`：插页式广告。
- `nat`：原生广告。
- `ban`：横幅广告。

### 场景与广告类型示例映射

- `ac_launch` -> `op`
- `ac_backscan_int` -> `int`
- `ac_result_int` -> `int`
- `ac_main_nat` -> `nat`
- `ac_main_banner` -> `ban`
- `ac_scan_nat` -> `nat`
- `ac_result_nat` -> `nat`

### 多广告位顺序回退规则

- 一个广告场景下可以配置多个广告 ID。
- 每次发起加载时，必须从该场景数组中的第一个广告位开始尝试。
- 如果第一个广告位加载失败，则尝试第二个广告位。
- 如果第二个失败，则继续尝试第三个广告位。
- 依次顺延，直到最后一个广告位。

### 全部失败后的重试规则

- 如果某个场景下本轮所有候选广告位都加载失败，不是立即停止，而是进入重试流程。
- 重试间隔固定按 `1s -> 2s -> 4s` 执行。
- 每次进入新一轮重试时，都必须重新从第一个广告位开始尝试。
- 这意味着重试是“按场景整轮重试”，不是“按单个广告位重试”。

### 场景缓存池规则

- 每一个广告场景都对应一个独立缓存池。
- 缓存维度是“场景”，不是“广告类型”。
- 不同场景之间的缓存不能混用。
- 每个场景缓存池当前最多只允许缓存 `1` 个广告对象。

### 展示前必须走缓存池

- 每次展示广告前，都必须先从对应场景的缓存池中取广告。
- 不能绕过缓存池直接现场加载后立即展示，除非后续另有明确业务规则。
- 展示入口必须统一承担缓存读取和有效性校验职责。

### 过期时间规则

- `ad_timelimit` 表示广告缓存有效期，不是加载超时时间。
- 每个缓存广告都必须记录成功加载时间或写入缓存时间。
- 每次展示前，都必须根据当前时间与 `ad_timelimit` 判断该缓存广告是否过期。
- 如果广告已过期，则不能展示。

### 过期广告处理规则

- 一旦判定缓存广告过期，必须立即销毁广告对象。
- 销毁后必须立刻清空该场景缓存池。
- 清空后应重新进入该场景的加载流程，以便补出新的缓存广告。

### 展示即消费规则

- 广告从场景缓存池中取出用于展示时，视为该缓存已被消费。
- 这意味着缓存中的这条广告不能再被其他调用重复使用。

### 展示后的补缓存规则

- 广告一旦被取出展示，必须立即触发该场景的补缓存流程。
- 目的不是立刻展示第二条广告，而是尽快重新为该场景缓存池补入一个新的广告对象。
- 补缓存流程仍然遵守该场景的多广告 ID 顺序回退和 `1s/2s/4s` 重试规则。

### 业务层加载调用的防重入规则

- 广告加载行为由业务层触发。
- 但同一个广告场景只要已经处于“加载链路进行中”，后续重复调用 `load` 都必须无效。
- 这里的“加载中”不仅指当前正在请求某一个广告位，也包括失败后的回退和重试等待阶段。

### 何时算“正在加载中”

下列状态都属于“正在加载中”，此时业务层重复调用必须直接忽略：

- 正在尝试第 1 个广告位。
- 第 1 个失败后，正在尝试第 2 个广告位。
- 正在尝试后续候选广告位。
- 所有广告位失败后，正在等待 `1s`、`2s` 或 `4s` 的下一轮重试。
- 定时等待结束后，准备从第一个广告位重新开始的新一轮加载。

### 防重入的目的

- 避免同一场景并发发起多条广告请求链路。
- 避免多个重试计时器同时存在。
- 避免后一次调用覆盖前一次成功加载得到的缓存广告。
- 避免同一场景同时消耗多组候选广告位。

### 推荐状态模型

- `idle`：空闲，可发起加载。
- `loading`：正在尝试加载，包括在候选广告位之间回退。
- `retry_waiting`：本轮全失败，正在等待下一轮 `1s/2s/4s` 重试。
- `ready`：已有未过期缓存广告可供展示。
- `showing`：可选，表示广告正在展示中。

### 业务层调用 `load(sceneKey)` 的规则

- 如果当前场景状态为 `loading`，则直接忽略本次调用。
- 如果当前场景状态为 `retry_waiting`，也直接忽略本次调用。
- 只有在场景处于 `idle`，或者当前缓存已失效并被清空后，才允许启动新的加载链路。
- 如果广告展示完成后需要补缓存，则补缓存前也要先检查当前是否已经存在进行中的加载链路。

### 推荐加载流程

1. 根据 `sceneKey` 读取该场景的广告配置数组。
2. 如果当前场景已处于 `loading` 或 `retry_waiting`，直接忽略调用。
3. 将场景状态置为 `loading`，从数组索引 `0` 开始加载。
4. 当前广告位成功时，写入场景缓存池，记录 `adUnitId`、广告对象、加载时间和过期时间，并将状态置为 `ready`。
5. 当前广告位失败时，继续尝试下一个广告位。
6. 如果本轮所有广告位都失败，则进入 `retry_waiting`，按 `1s`、`2s`、`4s` 的顺序等待后，再从第一个广告位重新开始。
7. 任意一轮只要有一个广告位成功，就终止当前回退链路并保留该缓存广告。

### 推荐展示流程

1. 根据 `sceneKey` 获取该场景缓存池中的广告。
2. 如果没有缓存广告，则本次不展示，并确保后台存在有效加载链路。
3. 如果有缓存广告，则先校验是否过期。
4. 如果已过期，立即销毁广告对象并清空缓存池，不展示，同时重新触发加载。
5. 如果未过期，则从缓存池取出广告并执行展示。
6. 广告一旦被取出展示，就立即触发该场景补缓存。
7. 展示结束或广告被消费后，不再复用刚刚展示过的旧广告对象。

### 推荐缓存记录字段

- `sceneKey`
- `adType`
- `adUnitId`
- `adPlatform`
- `adObject`
- `loadedAt`
- `expireAt`
- `currentIndex`
- `retryStage`
- `state`

### 项目调度层易错点

- 把 `ad_timelimit` 误解成请求超时时间。
- 场景下明明配置了多个广告位，但没有按顺序从第一个开始回退。
- 全部失败后没有按 `1s/2s/4s` 退避，而是直接停止或高频重试。
- 同一个场景缓存池里缓存了多个广告对象，破坏“单场景单缓存”约束。
- 展示前没有做过期判断，直接使用缓存广告。
- 广告从缓存池取出展示后，没有立即补缓存。
- 同一个场景已经在加载或等待重试，却又被业务层重复触发新一轮加载。
- 没有记录成功缓存广告对应的 `adUnitId`，导致线上排查困难。

## 易错点

- 只加依赖、不初始化就开始加载广告。
- 在主线程调用 `MobileAds.initialize()`。
- 没有先在 AdMob 注册应用，导致没有可用的 App ID。
- 使用中介却没有等待初始化完成回调。
- 需要用户同意或年龄相关标志时，初始化顺序放反了。

## 记忆问题

1. GMA Next-Gen SDK 在 Android 上的最低 `minSdk`、`compileSdk` 和 Kotlin 版本要求分别是什么？
2. 在 AdMob 接入流程里，为什么必须先注册应用？最终拿到的关键标识是什么？
3. Gradle 仓库配置里至少要保证哪两个仓库可用？
4. 应用级构建文件里需要添加的 GMA Next-Gen SDK 依赖坐标是什么？
5. 为什么不能在初始化前调用广告加载或其他 `MobileAds` 方法？
6. `MobileAds.initialize()` 为什么必须放到后台线程执行？
7. SDK 初始化通常应该在应用生命周期的什么阶段执行？需要执行几次？
8. 如果项目使用 AdMob 中介，广告加载应该发生在什么时机之后？
9. 文档为什么提醒在初始化前先处理 UMP、儿童定向、未成年同意等配置？
10. 激励广告和插页式激励广告的核心区别是什么？
11. 哪种广告格式最容易接入，且会在用户停留同一页面时自动刷新？
12. 开屏广告适合在什么场景展示？
13. Android 开屏广告测试广告单元 ID 是什么？为什么测试阶段必须使用它？
14. 手动加载单个开屏广告时，核心 API 是哪个？加载成功后通常把广告对象保存到哪里？
15. 展示开屏广告前，为什么建议先注册 `adEventCallback`？
16. `AppOpenAdPreloader.start()` 和 `AppOpenAdPreloader.pollAd()` 分别负责什么？
17. 为什么文档特别强调不要在真正要展示之前提前调用 `pollAd()`？
18. 预加载模式下，`peekAdResponseInfo()` 和 `pollAd()` 的关键差别是什么？
19. 文档建议每个 `preloadId` 的缓冲区大小设置为多少？整个应用默认最多缓存多少条预加载广告？
20. 冷启动时，什么情况下应该放弃展示开屏广告？
21. 为什么不建议在用户首次启动应用时就展示开屏广告？
22. 开屏广告超过多长时间会过期？
23. Android 横幅广告测试广告单元 ID 是什么？
24. 横幅广告在 GMA Next-Gen SDK 中通常由哪个视图类承载？
25. 横幅广告示例中使用的广告请求类型是什么？
26. 锚定自适应横幅广告的尺寸通常通过哪个 API 获取？
27. 如果 AdMob 后台已经配置自动刷新，横幅加载失败后是否一定要立刻手动再次请求？
28. 自动刷新在什么前提下才会发生？
29. 横幅广告使用完后，完整的释放动作应该包含哪三步？
30. `BannerAdEventCallback` 适合监听哪些关键事件？
31. 为什么展示广告的 `Activity` 如果关闭了硬件加速，视频横幅就可能出问题？
32. 插页式广告最适合放在什么样的业务时机里？
33. Android 插页式广告测试广告单元 ID 是什么？
34. 单次加载插页式广告的核心 API 是哪个？
35. 展示插页式广告时调用哪个方法？
36. 为什么要在展示前先设置 `InterstitialAdEventCallback`？
37. `onAdDismissedFullScreenContent()` 里为什么通常要把广告引用置空？
38. 预加载模式下，`InterstitialAdPreloader.start()` 和 `pollAd()` 分别负责什么？
39. 为什么不能在真正展示前提前调用 `pollAd()`？
40. `peekAdResponseInfo()` 和 `pollAd()` 的区别是什么？
41. 为什么不建议在 `PreloadCallback` 里再次调用 `start()` 或 `pollAd()`？
42. 每个 `preloadId` 推荐缓冲多少条广告？应用级默认总缓存上限是多少？
43. 原生广告接入为什么通常被分成“加载”和“展示”两个阶段？
44. Android 原生广告和原生视频广告的测试广告单元 ID 分别是什么？
45. 原生广告的顶级容器为什么必须是 `NativeAdView`？
46. 为什么所有原生广告素材视图都应该作为 `NativeAdView` 的子视图？
47. `NativeAdLoader.load()` 返回的是可直接显示的 View，还是广告数据对象？
48. `registerNativeAd(nativeAd, mediaView)` 的核心作用是什么？
49. 如果某个原生广告素材没有数据，正确处理方式是什么？
50. 一次最多可以请求多少条原生广告？为什么使用中介时不应调用多广告加载版本？
51. 为什么原生广告只建议缓存真正需要展示的少量内容？
52. 原生广告预缓存多久后建议清理并重新加载？
53. 原生广告不再使用时为什么必须调用 `destroy()`？
54. 为什么不应该在原生广告视图内部自己实现点击处理？
55. `creativeSummary` 如果被修改过，为什么修改后的文本要放在 `NativeAdView` 外部？
56. 广告配置为什么要按“场景 key -> 候选广告位数组”的结构组织？
57. 一个广告场景下面为什么允许配置多个广告 ID？它们的用途是什么？
58. 每次加载某个场景广告时，为什么必须从第一个广告位开始尝试？
59. 如果某个场景下本轮所有广告位都失败，下一轮应按什么时间间隔重试？
60. 为什么重试时要重新从第一个广告位开始，而不是只重试最后一个失败的广告位？
61. 为什么本地缓存应该按“场景”管理，而不是按“广告类型”管理？
62. 每个广告场景为什么都要有自己的独立缓存池？
63. 每个场景缓存池最多允许缓存几个广告对象？
64. `ad_timelimit` 在这套规则里表示的是什么？
65. 每次展示前为什么必须从缓存池取广告并校验是否过期？
66. 如果缓存广告过期，正确处理步骤是什么？
67. 为什么广告一旦从缓存池取出展示，就要立即重新触发该场景的补缓存逻辑？
68. “加载中”为什么要包含候选广告位切换和 `1s/2s/4s` 重试等待阶段？
69. 如果业务层在同一场景已经 `loading` 或 `retry_waiting` 时再次调用 `load`，正确行为应该是什么？
70. 为什么这条防重入规则对避免重复请求、重复定时器和缓存覆盖很重要？
71. 一个场景缓存记录里，至少应该保存哪些元信息，才能支持调试、过期判断和回退管理？

## 以后怎么用这份笔记

- 需要回忆 Android GMA 新 SDK 的基础接入步骤时，先读这份文件。
- 需要继续接入某一种广告格式时，把这份文件当作“总入口”，再补充对应广告格式的专项实现文档。
- 如果后面还有 GMA、AdMob、UMP、中介适配器相关文档，可以继续追加到同一目录下形成知识库。

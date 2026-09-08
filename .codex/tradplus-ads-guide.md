# TradPlus 聚合平台广告接入总文档

## 文档主题

Android 接入 TradPlus 聚合平台时，开屏广告、插屏广告、原生广告、横幅广告的统一接入说明、展示规则、回调监听与注意事项。

## 一句话记忆

同一个广告位 ID 尽量只维护一个广告对象并复用；加载阶段通常只调用一次 `loadAd()`，展示前优先用 `isReady()` 判断；不要在失败回调里手动重试；页面级广告在不再使用时要及时清理容器或释放资源。

## 文档覆盖范围

- 开屏广告 `TPSplash`
- 插屏广告 `TPInterstitial`
- 原生广告 `TPNative`
- 横幅广告 `TPBanner`

## 当前可验证来源说明

- 本文档当前已经根据你提供的 **插屏广告 / 开屏广告 / 原生广告 / 横幅广告 Demo 源码** 补充了可直接验证的精确包名、`listener` 接口来源、回调参数类型导入路径，以及 `TPInterstitial`、`TPSplash`、`TPNative`、`TPBanner` 的部分构造 / 生命周期 / 扩展 API 细节。
- 其中仍有少量 API 只在“业务封装层”或“资料说明”中出现、未在单个 Demo 里直接调用，我已在各小节里明确标注验证级别，避免误导。
- 如果你后续再提供 Rewarded / Draw / Native 列表等示例，也可以继续沿用这份总文档追加。

## TradPlus 关键类型导入路径（当前已确认部分）

以下导入路径来自你提供的 `InterstitialActivity` 示例，可直接视为当前项目示例中已验证路径：

```java
import com.tradplus.ads.base.adapter.nativead.TPNativeAdView;
import com.tradplus.ads.base.bean.TPAdError;
import com.tradplus.ads.base.bean.TPAdInfo;
import com.tradplus.ads.base.bean.TPBaseAd;
import com.tradplus.ads.base.common.TPImageLoader;
import com.tradplus.ads.base.util.SegmentUtils;
import com.tradplus.ads.mgr.interstitial.TPCustomInterstitialAd;
import com.tradplus.ads.mgr.nativead.TPCustomNativeAd;
import com.tradplus.ads.mgr.nativead.TPNativeAdRenderImpl;
import com.tradplus.ads.open.LoadAdEveryLayerListener;
import com.tradplus.ads.open.banner.BannerAdListener;
import com.tradplus.ads.open.banner.TPBanner;
import com.tradplus.ads.open.interstitial.InterstitialAdListener;
import com.tradplus.ads.open.interstitial.TPInterstitial;
import com.tradplus.ads.open.nativead.NativeAdListener;
import com.tradplus.ads.open.nativead.TPNative;
import com.tradplus.ads.open.nativead.TPNativeAdRender;
import com.tradplus.ads.open.splash.SplashAdListener;
import com.tradplus.ads.open.splash.TPSplash;
```

### 当前已确认的核心类型

- `TPAdError`
  - 导入路径：`com.tradplus.ads.base.bean.TPAdError`
- `TPAdInfo`
  - 导入路径：`com.tradplus.ads.base.bean.TPAdInfo`
- `TPBaseAd`
  - 导入路径：`com.tradplus.ads.base.bean.TPBaseAd`
- `LoadAdEveryLayerListener`
  - 导入路径：`com.tradplus.ads.open.LoadAdEveryLayerListener`
- `BannerAdListener`
  - 导入路径：`com.tradplus.ads.open.banner.BannerAdListener`
- `TPBanner`
  - 导入路径：`com.tradplus.ads.open.banner.TPBanner`
- `InterstitialAdListener`
  - 导入路径：`com.tradplus.ads.open.interstitial.InterstitialAdListener`
- `TPInterstitial`
  - 导入路径：`com.tradplus.ads.open.interstitial.TPInterstitial`
- `NativeAdListener`
  - 导入路径：`com.tradplus.ads.open.nativead.NativeAdListener`
- `TPNative`
  - 导入路径：`com.tradplus.ads.open.nativead.TPNative`
- `SplashAdListener`
  - 导入路径：`com.tradplus.ads.open.splash.SplashAdListener`
- `TPSplash`
  - 导入路径：`com.tradplus.ads.open.splash.TPSplash`
- `TPCustomNativeAd`
  - 导入路径：`com.tradplus.ads.mgr.nativead.TPCustomNativeAd`
- `TPNativeAdRenderImpl`
  - 导入路径：`com.tradplus.ads.mgr.nativead.TPNativeAdRenderImpl`
- `TPNativeAdRender`
  - 导入路径：`com.tradplus.ads.open.nativead.TPNativeAdRender`
- `TPNativeAdView`
  - 导入路径：`com.tradplus.ads.base.adapter.nativead.TPNativeAdView`

## 通用接入原则

### 1. 提前加载

- 广告加载都需要时间，建议在真正展示前提前发起加载。
- 如果业务有明确展示时机，应在展示点之前预热广告对象。

### 2. 单广告位单实例优先

- 同一个广告位 ID 在全局维度上通常按单例思路使用。
- 建议同一个广告位只创建一个广告对象并复用。
- 不要在每次展示前重新创建对象并重复初始化。

### 3. 不要依赖失败回调手动重试

- **禁止** 在广告失败回调中再次主动请求广告。
- 这样容易引发很多无用请求，也可能导致应用卡顿。
- TradPlus SDK 内部通常已经具备自动加载或自动调度能力，业务层高频手动重试容易与 SDK 机制冲突。

### 4. 展示前优先检查可用性

- 对需要显式展示的广告类型，优先通过 `isReady()` 判断广告是否可用。
- 不要只依赖 `onAdLoaded()` 来判断当前一定可以展示。

### 5. Activity / 容器 / 生命周期要匹配

- 部分广告平台要求在加载或展示时传入有效 `Activity`，否则可能加载失败或展示失败。
- 需要容器的广告类型，必须准备好有效的广告容器。
- 页面离开且广告不再使用时，要及时清理容器或释放资源，避免页面残留和潜在泄漏风险。

---

## 一、开屏广告（TPSplash）

### 源码已验证的包名与导入路径

以下内容来自你提供的 `SplashActivity` 示例，可视为当前版本示例中已确认信息：

```java
import com.tradplus.ads.base.GlobalTradPlus;
import com.tradplus.ads.base.bean.TPAdError;
import com.tradplus.ads.base.bean.TPAdInfo;
import com.tradplus.ads.base.bean.TPBaseAd;
import com.tradplus.ads.open.splash.SplashAdListener;
import com.tradplus.ads.open.splash.TPSplash;
```

### 当前版本中已验证的构造与生命周期 API 细节

#### 构造方法

从你提供的示例可直接确认：

```java
tpSplash = new TPSplash(SplashActivity.this, TestAdUnitId.SPLASH_ADUNITID);
```

因此当前至少已验证到以下构造形式：

```java
TPSplash(Activity activity, String adUnitId)
```

示例注释还明确说明：

- 快手相关场景要求传入的 `activity` 是 `FragmentActivity`，否则可能无法展示快手开屏。

#### 已验证的初始化后常见调用

```java
tpSplash.setAdListener(new SplashAdListener() { ... });
tpSplash.loadAd(null);
tpSplash.setDefaultConfig("xxxxxxxxxxxxxxxxxxxxxxxxx");
GlobalTradPlus.getInstance().refreshContext(this);
```

可确认当前示例里至少使用到了以下 API：

- `setAdListener(SplashAdListener listener)`
- `loadAd(ViewGroup container)` 或兼容 `null` 容器的 `loadAd(null)` 用法
- `setDefaultConfig(String config)`
- `GlobalTradPlus.getInstance().refreshContext(Activity activity)`

#### 已验证的展示与状态查询

从示例可直接确认：

```java
tpSplash.showAd(findViewById(R.id.splash_container));
```

以及：

```java
if (tpSplash.isReady()) {
    tpSplash.showAd(findViewById(R.id.splash_container));
}
```

因此当前至少已验证到以下 API：

- `showAd(View container)`
- `isReady()`

> 注意：你之前提供的资料里还有 `showAd(activity, adContainer, adSceneId)` 用法，但这个重载当前**尚未在你提供的开屏 Demo 中再次出现**，所以文档里仍把它视为资料级确认，而不是本次源码级确认。

#### 已验证的释放 API

从 `onDestroy()` 可直接确认：

```java
if (tpSplash != null) {
    tpSplash.onDestroy();
}
```

因此当前版本中，`TPSplash` 已明确存在：

```java
onDestroy()
```

建议做法：

- 开屏页销毁且该广告对象不再使用时调用 `onDestroy()`。
- 调用后不要继续使用旧实例。

### 适用特点

- 适合应用冷启动或热启动切回前台时展示。
- 冷启动和热启动的展示策略不同，需要区别处理。

### 加载广告

#### 基本规则

- 开屏广告加载需要时间，建议在真正展示前尽早发起加载。
- `loadAd()` 时不需要传入广告容器，容器可以在展示阶段再传。
- 同一个广告位 ID 建议只创建一个 `TPSplash` 对象并复用。
- 创建广告对象 `TPSplash` 时，国内部分广告平台要求传入 `Activity`，否则可能无法成功加载。

#### 需要特别注意的广告平台

以下平台在部分场景下要求传入 `Activity`，否则可能加载失败：

- TapTap
- BeiZi
- Mintegral-CN
- Vivo

#### 标准写法

```java
TPSplash tpSplash = new TPSplash(context, "在TP平台创建的广告位ID");
tpSplash.setAdListener(new SplashAdListener());
tpSplash.loadAd(null);
```

### 展示广告

#### 冷启动展示策略

- 冷启动时，应尽快调用 `loadAd()`。
- 当监听到 `onAdLoaded()` 回调后，应立即展示广告。

#### 热启动展示策略

- 热启动时，可以提前把广告加载好。
- 当监听到设备或应用切回前台事件时，调用 `isReady()` 检查是否有可用广告。
- 如果 `isReady()` 返回 `true`，再调用展示方法。

#### 容器要求

- 开发者需要提供广告容器，推荐使用 `FrameLayout`。
- 部分第三方广告平台会以 `View` 的方式返回广告内容，因此加载或展示时需要传入容器。
- 当监听到 `onAdClosed()` 后，应及时清空该容器。

#### 标准写法

```java
if (tpSplash.isReady()) {
    tpSplash.showAd(adContainer);
}
```

#### 展示时传入 Activity

- 从 `V16.8.0.1+` 开始支持展示时传入 `Activity`。
- AdMob 相关、Pangle 和 Yandex 在展示时需要传入 `Activity`。

```java
if (tpSplash.isReady()) {
    tpSplash.showAd(activity, adContainer, adSceneId);
}
```

#### `adSceneId` 说明

- `adSceneId` 表示广告场景 ID。
- 如果当前业务不使用该功能，可按 SDK 约定传 `null`。

### 回调监听

#### 重要限制

- **禁止** 在 `onAdLoadFailed()` 回调里再次主动请求广告。
- 在监听到 `onAdClosed()` 后，应清空 `adContainer`。

#### 回调示例

```java
tpSplash.setAdListener(new SplashAdListener() {

    @Override // 广告加载完成：首个广告源加载成功时回调；一次加载流程只会回调一次
    public void onAdLoaded(TPAdInfo tpAdInfo, TPBaseAd tpBaseAd) {}

    @Override // 广告被点击
    public void onAdClicked(TPAdInfo tpAdInfo) {}

    @Override // 广告成功展示在页面上
    public void onAdImpression(TPAdInfo tpAdInfo) {}

    @Override // 广告加载失败
    public void onAdLoadFailed(TPAdError error) {}

    @Override // 广告展示失败（部分广告源支持）
    public void onAdShowFailed(TPAdInfo tpAdInfo, TPAdError error) {}

    @Override // 广告被关闭
    public void onAdClosed(TPAdInfo tpAdInfo) {
        adContainer.removeAllViews();
    }
});
```

### 接入参考

- 官方示例可参考：`SplashActivity`
- 接入完成后可使用 TP 的测试模式或第三方平台测试 ID 进行测试

### 开屏广告详细说明

- 如果有其他需求不满足，可以进一步参考“开屏广告详细集成说明”

### 易错点

- 在 `onAdLoadFailed()` 中手动再次请求广告
- 冷启动加载完成后没有及时展示
- 热启动切前台时没有先判断 `isReady()`
- 展示结束后没有清空 `adContainer`
- 某些广告平台需要 `Activity` 时只传了 `Context`

### `SplashAdListener` 完整回调签名（基于当前示例确认）

```java
public interface SplashAdListener {
    void onAdClicked(TPAdInfo tpAdInfo);
    void onAdImpression(TPAdInfo tpAdInfo);
    void onAdClosed(TPAdInfo tpAdInfo);
    void onAdLoaded(TPAdInfo tpAdInfo, TPBaseAd tpBaseAd);
    void onAdLoadFailed(TPAdError tpAdError);
}
```

#### 回调参数类型导入路径

- `TPAdInfo`
  - `import com.tradplus.ads.base.bean.TPAdInfo;`
- `TPAdError`
  - `import com.tradplus.ads.base.bean.TPAdError;`
- `TPBaseAd`
  - `import com.tradplus.ads.base.bean.TPBaseAd;`

### 当前高级用法中已验证的额外细节

根据 `loadCustomSplashAd()` 示例，当前还可以确认：

- 预加载场景中，`loadAd(null)` 后可在 `onAdLoaded()` 中结合 `isReady()` 再决定是否展示。
- 可通过 `setDefaultConfig(String config)` 预置一份默认 TradPlus 配置，用于提升首次安装后的冷启动加载速度。
- 如果开屏用于热启动预加载场景，可在进入展示场景时调用：

```java
GlobalTradPlus.getInstance().refreshContext(this);
```

该调用在示例注释中用于刷新 `context`，避免特定广告平台（示例里提到快手）在热启动预加载展示时出现问题。

---

## 二、插屏广告（TPInterstitial）

### 源码已验证的包名与导入路径

以下内容来自你提供的 `InterstitialActivity` 示例，可视为当前版本示例中已确认信息：

```java
import com.tradplus.ads.base.bean.TPAdError;
import com.tradplus.ads.base.bean.TPAdInfo;
import com.tradplus.ads.open.LoadAdEveryLayerListener;
import com.tradplus.ads.open.interstitial.InterstitialAdListener;
import com.tradplus.ads.open.interstitial.TPInterstitial;
```

### 当前版本中已验证的构造与生命周期 API 细节

#### 构造方法

从你提供的示例可直接确认：

```java
mTPInterstitial = new TPInterstitial(this, TestAdUnitId.INTERSTITIAL_ADUNITID);
```

因此当前至少已验证到以下构造形式：

```java
TPInterstitial(Activity activity, String adUnitId)
```

#### 已验证的初始化后常见调用

```java
mTPInterstitial.entryAdScenario(TestAdUnitId.ENTRY_AD_INTERSTITIAL);
mTPInterstitial.setAdListener(new InterstitialAdListener() { ... });
mTPInterstitial.setAllAdLoadListener(new LoadAdEveryLayerListener() { ... });
mTPInterstitial.setCustomParams(mLocalExtras);
```

可确认当前示例里至少使用到了以下 API：

- `entryAdScenario(String adSceneId)`
- `setAdListener(InterstitialAdListener listener)`
- `setAllAdLoadListener(LoadAdEveryLayerListener listener)`
- `setCustomParams(HashMap<String, Object> localExtras)`

#### 已验证的展示与状态查询

你的示例中，实际展示走了外部封装 `videoUtils.showInterstitial(InterstitialActivity.this)`，但在同文件的 `customNativeAdRender()` 中可以直接看到：

```java
mTPInterstitial.showAd(InterstitialActivity.this, "");
```

因此当前至少已验证到以下展示形式：

```java
showAd(Activity activity, String adSceneId)
```

同时从业务判断逻辑可确认存在：

- `isReady()`：用于判断广告是否可展示（通过 `videoUtils.isReadyInterstitial()` 的使用场景可侧面印证原文规则，但当前示例未直接展示 `mTPInterstitial.isReady()` 调用）

#### 已验证的释放 API

从 `onDestroy()` 可直接确认：

```java
if(mTPInterstitial != null){
    mTPInterstitial.onDestroy();
}
```

因此当前版本中，`TPInterstitial` 已明确存在：

```java
onDestroy()
```

建议做法：

- `Activity` / `Fragment` 销毁且该广告对象不再使用时调用 `onDestroy()`。
- 调用后不要继续使用旧实例。

#### 已验证的插屏扩展 API

从示例还可以确认以下扩展能力：

- `getInterstitialAd()`
  - 用于拿到底层三方插屏对象。
- `setCustomNativeAdRender(TPNativeAdRender render)`
  - 用于“原生拼插屏”等需要自定义渲染的场景。

示例片段：

```java
Object interstitialAd = mTPInterstitial.getInterstitialAd();
...
mTPInterstitial.setCustomNativeAdRender(new TPNativeAdRender() { ... });
```

### 加载广告

#### 基本规则

- 插屏广告加载需要时间，应该在真正展示前提前发起加载。
- 创建广告对象时，部分广告平台要求传入 `Activity`，否则可能无法成功加载。
- `TPInterstitial` 在 SDK 内部已经带有自动加载能力，因此通常只需要调用一次 `loadAd()`。
- 同一个广告位 ID 建议只创建一个广告对象并复用。

#### 标准写法

```java
TPInterstitial tpInterstitial = new TPInterstitial(activity, "在TP平台创建的广告位ID");
tpInterstitial.setAdListener(new InterstitialAdListener());
tpInterstitial.loadAd();
```

### 展示广告

#### 展示前判断

- 建议先调用 `isReady()` 检查当前是否有可用广告。
- 当 `isReady()` 返回 `true` 时，再执行展示。
- 不要依赖 `onAdLoaded()` 回调来直接判断“此刻一定可以展示”。

#### Activity 传参

- 部分广告平台要求展示时传入 `Activity`，否则可能无法成功展示。
- `showAd()` 的第二个参数 `adSceneId` 是广告场景 ID。
- 如果当前业务不使用广告场景功能，可以直接传 `null`。

#### 标准写法

```java
if (tpInterstitial.isReady()) {
    tpInterstitial.showAd(activity, null);
}
```

### 回调监听

#### 重要限制

- **禁止** 在 `onAdFailed()` 回调里再次主动请求广告。

#### 回调示例

```java
tpInterstitial.setAdListener(new InterstitialAdListener() {
    @Override // 广告加载完成：首个广告源加载成功时回调；一次加载流程只会回调一次
    public void onAdLoaded(TPAdInfo tpAdInfo) {}

    @Override // 广告被点击
    public void onAdClicked(TPAdInfo tpAdInfo) {}

    @Override // 广告成功展示在页面上
    public void onAdImpression(TPAdInfo tpAdInfo) {}

    @Override // 广告加载失败
    public void onAdFailed(TPAdError error) {}

    @Override // 广告被关闭
    public void onAdClosed(TPAdInfo tpAdInfo) {}

    @Override // 视频播放开始（部分广告源支持）
    public void onAdVideoStart(TPAdInfo tpAdInfo) {}

    @Override // 视频播放结束（部分广告源支持）
    public void onAdVideoEnd(TPAdInfo tpAdInfo) {}

    @Override // 视频播放失败（部分广告源支持）
    public void onAdVideoError(TPAdInfo tpAdInfo, TPAdError error) {}
});
```

### `InterstitialAdListener` 完整回调签名（基于当前示例确认）

```java
public interface InterstitialAdListener {
    void onAdLoaded(TPAdInfo tpAdInfo);
    void onAdClicked(TPAdInfo tpAdInfo);
    void onAdImpression(TPAdInfo tpAdInfo);
    void onAdFailed(TPAdError tpAdError);
    void onAdClosed(TPAdInfo tpAdInfo);
    void onAdVideoError(TPAdInfo tpAdInfo, TPAdError tpAdError);
    void onAdVideoStart(TPAdInfo tpAdInfo);
    void onAdVideoEnd(TPAdInfo tpAdInfo);
}
```

#### 回调参数类型导入路径

- `TPAdInfo`
  - `import com.tradplus.ads.base.bean.TPAdInfo;`
- `TPAdError`
  - `import com.tradplus.ads.base.bean.TPAdError;`

### `LoadAdEveryLayerListener` 完整回调签名（基于当前示例确认）

```java
public interface LoadAdEveryLayerListener {
    void onAdAllLoaded(boolean success);
    void oneLayerLoadFailed(TPAdError tpAdError, TPAdInfo tpAdInfo);
    void oneLayerLoaded(TPAdInfo tpAdInfo);
    void onAdStartLoad(String placementId);
    void oneLayerLoadStart(TPAdInfo tpAdInfo);
    void onBiddingStart(TPAdInfo tpAdInfo);
    void onBiddingEnd(TPAdInfo tpAdInfo, TPAdError tpAdError);
    void onAdIsLoading(String placementId);
}
```

#### 回调参数类型导入路径

- `TPAdInfo`
  - `import com.tradplus.ads.base.bean.TPAdInfo;`
- `TPAdError`
  - `import com.tradplus.ads.base.bean.TPAdError;`

### 接入参考

- 官方示例可参考：`InterstitialActivity`
- 接入完成后可使用 TP 的测试模式或第三方平台测试 ID 进行测试

### 插屏广告详细说明

- 如果有其他需求不满足，可以进一步参考“插屏广告详细集成说明”

### 易错点

- 在 `onAdFailed()` 中手动再次请求广告
- 只依赖 `onAdLoaded()`，而不是在展示前再次判断 `isReady()`
- 加载或展示时没有传入有效 `Activity`

---

## 三、原生广告（TPNative）

### 源码已验证的包名与导入路径

以下内容来自你提供的 `NativeActivity` 示例，可视为当前版本示例中已确认信息：

```java
import com.tradplus.ads.base.adapter.nativead.TPNativeAdView;
import com.tradplus.ads.base.bean.TPAdError;
import com.tradplus.ads.base.bean.TPAdInfo;
import com.tradplus.ads.base.bean.TPBaseAd;
import com.tradplus.ads.base.common.TPImageLoader;
import com.tradplus.ads.mgr.nativead.TPCustomNativeAd;
import com.tradplus.ads.mgr.nativead.TPNativeAdRenderImpl;
import com.tradplus.ads.open.LoadAdEveryLayerListener;
import com.tradplus.ads.open.nativead.NativeAdListener;
import com.tradplus.ads.open.nativead.TPNative;
import com.tradplus.ads.open.nativead.TPNativeAdRender;
```

### 当前版本中已验证的构造与生命周期 API 细节

#### 构造方法

从你提供的示例可直接确认：

```java
tpNative = new TPNative(NativeActivity.this, TestAdUnitId.NATIVE_ADUNITID);
```

因此当前至少已验证到以下构造形式：

```java
TPNative(Activity activity, String adUnitId)
```

#### 已验证的初始化后常见调用

```java
tpNative.setAdListener(new NativeAdListener() { ... });
tpNative.setCustomParams(setLocalCustomParams());
```

可确认当前示例里至少使用到了以下 API：

- `setAdListener(NativeAdListener listener)`
- `setCustomParams(Map<String, Object> localExtras)`

#### 已验证的加载 / 展示 / 状态查询

当前 `NativeActivity` 里实际加载和展示经过了 `NativeUtils` 封装：

```java
nativeUtils.loadNative(tpNative);
nativeUtils.showNative(adContainer);
nativeUtils.isReady();
```

因此这份示例**没有直接把 `tpNative.loadAd()` / `tpNative.isReady()` / `tpNative.showAd(...)` 写出来**。

不过结合你前面给的原生广告接入资料，当前仍可保留以下结论：

- 资料级确认：`loadAd()`
- 资料级确认：`isReady()`
- 资料级确认：`showAd(adContainer, layoutId)`

而这次源码级能明确确认的是：

- `TPNative` 确实是可长期持有并传给业务封装层使用的广告对象
- `adContainer` 使用 `ViewGroup` 作为承载容器

#### 已验证的释放 API

从 `onDestroy()` 可直接确认：

```java
tpNative.onDestroy();
```

因此当前版本中，`TPNative` 已明确存在：

```java
onDestroy()
```

需要注意：

- 你给的 Demo 里这里没有做空判断，说明示例默认 `loadNormalNative()` 会在 `onCreate()` 中先初始化。
- 如果业务里存在延迟初始化或条件初始化，实际项目中建议先判空再调用 `onDestroy()`，避免空指针风险。

### 当前已从插屏源码旁证到的原生相关类型

虽然还没有拿到 `TPNative` 的 Demo，但你给的插屏示例已经确认了以下原生渲染相关类型：

- `TPNativeAdRender`
  - `import com.tradplus.ads.open.nativead.TPNativeAdRender;`
- `TPNativeAdView`
  - `import com.tradplus.ads.base.adapter.nativead.TPNativeAdView;`

这说明在 TradPlus 当前示例体系中，自定义原生渲染至少会涉及以上两个类型。

### 加载广告

#### 基本规则

- 原生广告加载需要时间，建议在真正展示前提前发起加载。
- 创建广告对象 `TPNative` 后，因为 SDK 内部有自动加载功能，通常只需要调用一次 `loadAd()`。
- 同一个广告位 ID 建议只创建一个广告对象并复用。
- 部分国内广告平台以及海外 Mintegral 的原生自动渲染场景要求传入 `Activity`，否则可能无法成功加载广告。

#### 标准写法

```java
TPNative tpNative = new TPNative(context, "在TP平台创建的广告位ID");
tpNative.setAdListener(new NativeAdListener());
tpNative.loadAd();
```

### 展示广告

#### 展示前判断

- 需要展示广告时，建议通过 `isReady()` 检查当前是否有可用广告。
- 当 `isReady()` 返回 `true` 时，再执行展示。
- 不要依赖 `onAdLoaded()` 回调来直接判断“当前一定可以展示”。

#### 容器与布局说明

- `adContainer` 是展示广告的容器，TP 会将加载好的广告添加到该容器中。
- `layoutId` 是布局文件 ID。
- SDK 提供了默认布局，开发者可以修改布局样式。
- **但是不能修改布局中的 `android:id` 资源 ID**，否则可能导致广告素材无法正确绑定或展示异常。

#### 标准写法

```java
if (tpNative.isReady()) {
    tpNative.showAd(adContainer, layoutId);
}
```

### 回调监听

#### 重要限制

- **禁止** 在 `onAdLoadFailed()` 和 `onAdShowFailed()` 回调里再次主动请求广告。

#### 回调示例

```java
tpNative.setAdListener(new NativeAdListener() {

    @Override // 广告加载完成：首个广告源加载成功时回调；一次加载流程只会回调一次
    public void onAdLoaded(TPAdInfo tpAdInfo, TPBaseAd tpBaseAd) {}

    @Override // 广告被点击
    public void onAdClicked(TPAdInfo tpAdInfo) {}

    @Override // 广告成功展示在页面上
    public void onAdImpression(TPAdInfo tpAdInfo) {}

    @Override // 广告加载失败
    public void onAdLoadFailed(TPAdError tpAdError) {}

    @Override // 广告展示失败（部分广告支持）
    public void onAdShowFailed(TPAdError tpAdError, TPAdInfo tpAdInfo) {}

    @Override // 广告被关闭
    public void onAdClosed(TPAdInfo tpAdInfo) {}
});
```

### 接入参考

- 官方示例可参考：`NativeActivity`
- 接入完成后可使用 TP 的测试模式或第三方平台测试 ID 进行测试

### 原生广告详细说明

- 广告 SDK 的资源不能被混淆
- 如果使用第三方资源优化框架，请为 SDK 配置资源白名单
- 如果有其他需求不满足，可以进一步参考“标准原生详细集成说明”
- 集成原生广告派生出来的 Draw 信息流时，请参考“Draw 信息流”

### 易错点

- 在 `onAdLoadFailed()` 或 `onAdShowFailed()` 中手动再次请求广告
- 修改了原生广告布局中的默认 `android:id`
- `adContainer` 不是有效容器
- 对需要 `Activity` 的广告平台只传了 `Context`
- 使用资源优化框架时没有为广告 SDK 配置资源白名单

### `NativeAdListener` 完整回调签名（基于当前示例确认）

```java
public interface NativeAdListener {
    void onAdLoaded(TPAdInfo tpAdInfo, TPBaseAd tpBaseAd);
    void onAdClicked(TPAdInfo tpAdInfo);
    void onAdImpression(TPAdInfo tpAdInfo);
    void onAdShowFailed(TPAdError tpAdError, TPAdInfo tpAdInfo);
    void onAdLoadFailed(TPAdError tpAdError);
    void onAdClosed(TPAdInfo tpAdInfo);
}
```

#### 回调参数类型导入路径

- `TPAdInfo`
  - `import com.tradplus.ads.base.bean.TPAdInfo;`
- `TPAdError`
  - `import com.tradplus.ads.base.bean.TPAdError;`
- `TPBaseAd`
  - `import com.tradplus.ads.base.bean.TPBaseAd;`

### 当前高级用法中已验证的额外细节

从 `NativeActivity` 的 import 与参数设置可进一步确认：

- 原生自定义渲染相关类型已在当前版本示例中出现：
  - `TPNativeAdView`
  - `TPNativeAdRender`
  - `TPCustomNativeAd`
  - `TPNativeAdRenderImpl`
- 原生广告支持通过 `setCustomParams(...)` 注入本地自定义参数，例如：
  - `huawei_close_position`
  - `huawei_native_template_type`
  - `huawei_autoinstall`

这些参数说明当前版本的原生广告接入中，至少部分广告平台（如华为）支持通过本地参数影响模板样式、关闭按钮位置和下载行为。

---

## 四、横幅广告（TPBanner）

### 源码已验证的包名与导入路径

以下内容来自你提供的 `BannerActivity` 示例，可视为当前版本示例中已确认信息：

```java
import com.tradplus.ads.base.adapter.nativead.TPNativeAdView;
import com.tradplus.ads.base.bean.TPAdError;
import com.tradplus.ads.base.bean.TPAdInfo;
import com.tradplus.ads.base.common.TPImageLoader;
import com.tradplus.ads.open.banner.BannerAdListener;
import com.tradplus.ads.open.banner.TPBanner;
import com.tradplus.ads.open.nativead.TPNativeAdRender;
```

### 当前版本中已验证的构造与生命周期 API 细节

#### 构造方法

从你提供的示例可直接确认：

```java
tpBanner = new TPBanner(this);
```

因此当前至少已验证到以下构造形式：

```java
TPBanner(Activity activity)
```

#### 已验证的初始化后常见调用

```java
tpBanner.closeAutoShow();
tpBanner.setAdListener(new BannerAdListener() { ... });
tpBanner.setCustomParams(mLocalExtras);
tpBanner.setNativeAdRender(new TPNativeAdRender() { ... });
```

可确认当前示例里至少使用到了以下 API：

- `closeAutoShow()`
- `setAdListener(BannerAdListener listener)`
- `setCustomParams(HashMap<String, Object> localExtras)`
- `setNativeAdRender(TPNativeAdRender render)`

示例注释中还出现了以下 API，说明它们至少存在于当前版本体系中：

- `setAllAdLoadListener(LoadAdEveryLayerListener listener)`
- `setAutoDestroy(boolean autoDestroy)`
- `onDestroy()`

其中这三个接口在你给的 `BannerActivity` 中是**注释状态**，因此可视为“源码中可见、但未在本 Demo 主流程实际执行”的确认级别。

#### 已验证的加载 / 展示 / 状态查询

当前 `BannerActivity` 里实际加载和展示经过了 `BannerUtils` 封装：

```java
bannerUtils.loadBanner(tpBanner, TestAdUnitId.BANNER_ADUNITID);
bannerUtils.showBanner(adContainer);
bannerUtils.isReady();
```

同时在高级示例中可以直接确认：

```java
adContainer.addView(tpBanner);
tpBanner.loadAd(TestAdUnitId.BANNER_ADUNITID);
```

因此当前至少已验证到以下 API：

- `loadAd(String adUnitId)`

此外，还能确认以下行为：

- `TPBanner` 本身就是 View，需要先添加到容器中。
- 当关闭自动展示能力后，业务层可以通过自己的封装控制展示时机。

#### 已验证的释放 API 与生命周期细节

你提供的 `BannerActivity` 主流程 `onDestroy()` 里**没有**直接调用 `tpBanner.onDestroy()`，但在高级示例注释中明确写出：

```java
// tpBanner.onDestroy();
```

以及：

```java
// 在TPBanner被remove后会释放广告资源（TPBanner的onDetachedFromWindow中会是否资源）
// tpBanner.setAutoDestroy(false);
```

因此当前可确认：

- `TPBanner` 存在 `onDestroy()` API。
- `TPBanner` 默认存在自动释放机制。
- 当 `TPBanner` 从窗口移除时，会在 `onDetachedFromWindow` 相关流程中释放广告资源。
- 如果调用 `setAutoDestroy(false)`，则需要业务侧自行管理生命周期并在合适时机调用 `onDestroy()`。

需要注意：

- 这里“自动释放”与“手动释放”的组合行为，是根据示例注释确认的，虽然没有在该 Demo 的主流程中执行，但已经比纯资料说明更接近源码级结论。

### 加载广告

#### 基本规则

- 横幅广告加载需要时间，建议在真正展示前提前发起加载。
- `TPBanner` 本身是一个 `ViewGroup`，开发者可以自定义它的大小和位置。
- 开发者需要主动把 `TPBanner` 添加到指定展示位置。
- 创建广告对象 `TPBanner` 时，部分广告平台要求传入 `Activity`，否则可能无法成功加载广告。
- 同一个广告位 ID 建议只创建一个广告对象并复用。

#### 标准写法

```java
TPBanner tpBanner = new TPBanner(activity);
tpBanner.setAdListener(new BannerAdListener());
tpBanner.loadAd("在TP平台创建的广告位ID");

// 建议使用 FrameLayout；如果使用 LinearLayout，需要在 addView 时同时设置 layoutParams
adContainer.addView(tpBanner);
```

### 展示广告

#### 自动展示规则

- 横幅广告加载成功后，TP 会把广告直接添加到 `TPBanner` 中。
- **不需要** 调用 `showAd()` 方法。

#### 容器尺寸要求

- Mintegral、InMobi、IronSource 平台中，存放 Banner 素材的父容器建议给定明确高度。
- **不要** 使用 `wrap_content` 作为这类父容器高度，否则可能因为广告素材问题导致展示异常。

#### Yandex 特殊要求

- Yandex 平台需要在请求广告前就把 `tpBanner` 添加到展示容器中。
- 否则即使加载成功，也可能无法展示。

### 释放资源

#### 释放时机

- 离开页面并且不再使用该广告位时，应调用 `onDestroy()` 释放资源。
- 下次再次使用时，需要重新 `new TPBanner(...)`。

#### 标准写法

```java
tpBanner.onDestroy();
tpBanner = null;
```

### 回调监听

#### 重要限制

- **禁止** 在 `onAdLoadFailed()` 回调里再次主动请求广告。

#### 回调示例

```java
tpBanner.setAdListener(new BannerAdListener() {
    @Override // 广告加载完成：首个广告源加载成功时回调；一次加载流程只会回调一次
    public void onAdLoaded(TPAdInfo tpAdInfo) {}

    @Override // 广告被点击
    public void onAdClicked(TPAdInfo tpAdInfo) {}

    @Override // 广告成功展示在页面上
    public void onAdImpression(TPAdInfo tpAdInfo) {}

    @Override // 广告加载失败
    public void onAdLoadFailed(TPAdError error) {}

    @Override // 广告被关闭
    public void onAdClosed(TPAdInfo tpAdInfo) {}
});
```

### 接入参考

- 官方示例可参考：`BannerActivity`
- 接入完成后可使用 TP 的测试模式或第三方平台测试 ID 进行测试

### 横幅广告详细说明

- 如果有其他需求不满足，可以进一步参考“横幅广告详细集成说明”

### 易错点

- 误以为横幅广告还需要手动调用 `showAd()`
- `TPBanner` 没有提前添加到页面容器
- Yandex 场景下没有在请求前把 `TPBanner` 加到容器中
- Mintegral、InMobi、IronSource 的 Banner 父容器高度使用 `wrap_content`
- 页面离开后没有调用 `onDestroy()`

### `BannerAdListener` 完整回调签名（基于当前示例确认）

```java
public interface BannerAdListener {
    void onAdClicked(TPAdInfo tpAdInfo);
    void onAdImpression(TPAdInfo tpAdInfo);
    void onAdLoaded(TPAdInfo tpAdInfo);
    void onAdLoadFailed(TPAdError error);
    void onAdClosed(TPAdInfo tpAdInfo);
}
```

#### 回调参数类型导入路径

- `TPAdInfo`
  - `import com.tradplus.ads.base.bean.TPAdInfo;`
- `TPAdError`
  - `import com.tradplus.ads.base.bean.TPAdError;`

### 当前高级用法中已验证的额外细节

从 `BannerActivity` 示例还能确认：

- 横幅广告支持 `closeAutoShow()`，说明当前版本允许关闭自动展示，由业务层接管展示节奏。
- 横幅广告支持 `setNativeAdRender(TPNativeAdRender render)`，说明某些 Banner 场景支持“原生拼 Banner”或下载类广告自定义渲染。
- 横幅广告支持 `setCustomParams(...)`，示例中用于华为下载类广告的：
  - `huawei_autoinstall`
- 横幅广告示例中使用了 `TPNativeAdView` 与 `TPNativeAdRender`，说明 Banner 在某些广告源下可走原生素材自定义渲染模式，而不仅仅是纯标准 Banner View。

---

## 建议接入顺序

1. 先确定广告类型与广告位 ID。
2. 为每个广告位维护单个可复用广告对象。
3. 在合适的生命周期中尽早调用 `loadAd()`。
4. 需要展示时，优先判断 `isReady()`（横幅广告除外，横幅加载成功后会自动展示）。
5. 根据广告类型传入正确的 `Activity`、容器和布局。
6. 页面离开或广告关闭后，做好容器清理与资源释放。

## 测试建议

- 接入完成后，可优先使用 TP 的测试模式进行验证。
- 也可以使用第三方广告平台的测试 ID 做联调。
- 不建议在接入初期直接使用正式广告位排查问题。

## 统一易错点清单

- 同一个广告位 ID 反复创建多个广告对象
- 在失败回调中手动再次请求广告
- 只依赖 `onAdLoaded()`，不在展示前校验广告可用性
- 需要 `Activity` 的场景没有传有效 `Activity`
- 容器未准备好就尝试展示广告
- 页面销毁后未清理容器或未释放广告资源

## 以后怎么用这份文档

- 需要快速查 TradPlus 各广告类型接入方式时，优先阅读这份总文档。
- 如果后续继续补充激励广告、Draw 信息流或其他派生能力，可以继续追加到本文件中。
- 如果项目内要统一广告接入规范，这份文档可以作为总入口文档使用。

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# ── Gson ──────────────────────────────────────────────────────────
# 保留泛型签名，TypeToken 需要在运行时读取泛型参数
-keepattributes Signature
-keepattributes *Annotation*

# 保留 TypeToken 及其所有子类（包括匿名内部类），防止 R8 擦除泛型信息
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# 保留通过 Gson 反序列化的数据类字段
-keep class com.ysdc.aidpdf.reminder.notice.PopRefresh { *; }
-keep class com.ysdc.aidpdf.ad.remote.NatConfig { *; }

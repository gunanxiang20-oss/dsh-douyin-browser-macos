# Xposed 入口类由 assets/xposed_init 里写死的类名反射创建，必须保留
-keep class com.byxiaopeng.dytiao.AdSkipModule { *; }
-keepnames class com.byxiaopeng.dytiao.AdSkipModule

# 模块里被 Xposed 框架通过接口回调的类
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# Xposed API 是 compileOnly，运行时由框架提供
-dontwarn de.robv.android.xposed.**
-dontwarn android.app.AndroidAppHelper

# 混淆强度：短名 + 自定义字典 + 全部打散到同一个包
-repackageclasses 'o'
-allowaccessmodification
-optimizationpasses 5
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt
-overloadaggressively
-adaptclassstrings

# 去掉调试信息
-renamesourcefileattribute a
-keepattributes RuntimeVisibleAnnotations

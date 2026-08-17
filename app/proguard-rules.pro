# Glide
-keep class com.bumptech.glide.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# Xposed(不打包,但保留入口类名)
-keep class com.laoli.hooktools.hook.MomentHook { *; }
-keep class com.laoli.hooktools.BuildConfig { *; }

# 反射用到的类
-keep class com.laoli.hooktools.** { *; }

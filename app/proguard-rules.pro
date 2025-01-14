# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\YiBin\eclipse\Android_SDK_windows/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class key to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ======================================================================================== #
# 系统通用混淆配置, 参见 android-sdk/tools/proguard/proguard-android-optimize.txt
# ======================================================================================== #
# ProGuard 的介绍以及参数说明, 参阅 https://blog.csdn.net/hibit521/article/details/79709659
# -keep 指定需要被保留的类和成员
# -keepclassmembers 指定需要被保留的类成员, 如果他们的类也有被保留. 如保留一个序列化类中的所有成员和方法
# -keepclasseswithmembers 指定保留那些含有指定类成员的类, 如保留所有包含 main 方法的类
# -keepnames 指定那些需要被保留名字的类和类成员, 如保留那些实现了 Serializable 接口的类的名字
# -keepclassmembernames 指定那些希望被保留的类成员的名字
# -keepclasseswithmembernames 保留含有指定名字的类和类成员
# class 可以指向任何接口或类, interface 只能指向接口, enum 只能指向枚举
# 接口或者枚举前面的 ! 表示相对应的非枚举或者接口
# 每个类都必须是全路径指定或者由 ?, *, ** 这三个通配符指定
# 类通配符 ? 任意匹配类名中的一个字符, 但是不匹配包名分隔符
# 类通配符 * 匹配类名的任何部分除了包名分隔符
# 类通配符 ** 匹配所有类名的所有部分, 包括报名分隔符
# <init> 匹配任何构造函数, <fields> 匹配任何域, <methods> 匹配任何方法, * 匹配任何方法和域
# 方法和域通配符 ? 任意匹配方法名中的单个字符, * 匹配方法命中的任意部分
# 数据类型通配符 % 匹配任何原生类型, ? 任意匹配单个字符, * 匹配类名的任何部分除了包名分隔符
# 数据类型通配符 ** 匹配所有类名的所有部分, 包括报名分隔符，*** 匹配任何类型，... 匹配任意参数个数
# ======================================================================================== #

-dontwarn org.mozilla.javascript.**
-dontwarn com.makeramen.**
-dontwarn org.junit.**
-dontwarn junit.**
-dontwarn jackpal.androidterm.**
-dontwarn com.iwebpp.nodeandroid.**
-dontwarn com.pushtorefresh.storio.**
-dontwarn java.lang.invoke.*
-dontwarn **$$Lambda$*

# autojs

-keep class org.mozilla.javascript.** { *; }
-keep class org.autojs.autojs.core.automator.** { *; }
-keep class org.autojs.autojs.** { *; }

-keep class com.stardust.automator.** { *; }
-keep class com.stardust.autojs.** { *; }
-dontwarn com.stardust.**

-keepattributes *Annotation*,SourceFile,LineNumberTable

-keepclassmembers class ** {
    @org.autojs.autojs.annotation.ScriptInterface <methods>;
    @com.stardust.autojs.annotation.ScriptInterface <methods>;
}

# Event bus

-keep class org.greenrobot.eventbus.** { *; }

-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# gson

-keep class * extends org.json.JSONObject {
    <fields>;
}

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# common

-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepattributes EnclosingMethod,Signature

# Required to preserve the Flurry SDK

-keep class com.flurry.** { *; }
-dontwarn com.flurry.**
-keepattributes *Annotation*,EnclosingMethod,Signature

-keepclasseswithmembers class * {
	public <init>(android.content.Context, android.util.AttributeSet, int);
	public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# Google Play Services library

-keep class * extends java.util.ListResourceBundle {
    protected java.lang.Object[][] getContents();
}

-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *

-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Bugly

-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

# Toaster

-keep class com.hjq.toast.** {*;}
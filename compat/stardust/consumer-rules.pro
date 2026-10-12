# Frozen com.stardust compatibility package (docs/dev/autojs4-runtime-plugin-roadmap.md D18 / P7.2).
# Scripts reach these classes by name through Rhino, so the consumer keeps them exactly as app/proguard-rules.pro does.
# zh-CN: 冻结的 com.stardust 兼容包: 脚本经 Rhino 按名称访问这些类, 消费者沿用 app/proguard-rules.pro 的 keep 规则.

-keep class com.stardust.automator.** { *; }
-keep class com.stardust.autojs.** { *; }
-dontwarn com.stardust.**

-keepclassmembers class ** {
    @com.stardust.autojs.annotation.ScriptInterface <methods>;
}

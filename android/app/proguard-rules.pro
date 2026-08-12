# ============================================================================
# ProGuard / R8 规则配置
# 项目：HeartRateMonitorMobile
# 目标：开启 isMinifyEnabled + isShrinkResources 后保证运行时稳定
# ============================================================================

# ----------------------------------------------------------------------------
# 1. 通用：保留调试堆栈信息（崩溃日志可读）
# ----------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

# ----------------------------------------------------------------------------
# 2. Kotlin 元数据保留（Kotlin 反射 / 协程内部依赖）
#    - kotlin.Metadata：R8 默认保留（mapping.txt 已验证未改名）
#    - **$$serializer：仅 kotlinx.serialization 需要，本项目未使用
# ----------------------------------------------------------------------------
-keepclassmembers class kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin 协程
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ----------------------------------------------------------------------------
# 3. Room 3 数据库（关键：实体字段名被生成的 _Impl 类按名访问）
#    包路径：com.github.heartratemonitor_compose.data.db
# ----------------------------------------------------------------------------
-keep class com.github.heartratemonitor_compose.data.db.** { *; }
-keep class androidx.room3.** { *; }
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room3.* <fields>;
    @androidx.room3.* <methods>;
}
-dontwarn androidx.room3.paging.**

# ----------------------------------------------------------------------------
# 4. NanoHTTPD（内置 HTTP 服务器）
#    NanoHTTPD 2.3.1 实际包名为 fi.iki.elonen（org.nanohttpd 仅是 Maven 坐标，
#    运行时不存在该包；已通过 mapping.txt 验证 0 匹配）。
# ----------------------------------------------------------------------------
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ----------------------------------------------------------------------------
# 6. Kable（蓝牙 LE 库）
# ----------------------------------------------------------------------------
-keep class com.juul.kable.** { *; }
-keepclassmembers class com.juul.kable.** { *; }
-dontwarn com.juul.kable.**

# ----------------------------------------------------------------------------
# 7. ColorPickerView（颜色选择器）
#    已移除：项目改用 Compose Canvas 实现色环，无此依赖。
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# 8. PermissionX（权限请求库）
# ----------------------------------------------------------------------------
-keep class com.permissionx.** { *; }
-keep class com.guolindev.permissionx.** { *; }
-keepclassmembers class com.permissionx.** { *; }
-keepclassmembers class com.guolindev.permissionx.** { *; }
-dontwarn com.permissionx.**
-dontwarn com.guolindev.permissionx.**

# ----------------------------------------------------------------------------
# 9. 项目数据类：Webhook / WebhookTrigger
#    - Webhook：通过 org.json.JSONObject put/get 显式按字符串字面量访问
#      （put("name", name) / getString("name")），字段名被混淆不影响 JSON
#      key（key 是源码中的字符串字面量）。无需 keep 字段。
#    - WebhookTrigger：valueOf(String) 和 .name 走反射，必须保留枚举常量名。
# ----------------------------------------------------------------------------
-keep class com.github.heartratemonitor_compose.data.WebhookTrigger { *; }
-keepclassmembers enum com.github.heartratemonitor_compose.data.WebhookTrigger {
    <fields>;
}

# ----------------------------------------------------------------------------
# 10. AndroidX / Compose 通用
#     AndroidX 自带 consumer-rules，无需手动 -keep；仅保留 -dontwarn 兜底。
#     移除原 -keep class androidx.** { *; }：该规则使 R8 对全部 AndroidX
#     停止裁剪，严重削弱混淆效果。Material Components (XML) 已不在此项目中。
# ----------------------------------------------------------------------------
-dontwarn androidx.**

# ViewBinding 已不使用（纯 Compose 项目），移除对应 keep 规则。

# ----------------------------------------------------------------------------
# 11. Manifest 声明的组件入口（Activity / Service / Application / Provider）
#     R8 通常自动保留 Manifest 引用的类，这里显式确保 Application 和
#     ContentProvider Initializer 的子类不被混淆。
#     注意：不再保留整个 ui/service/data 包，仅保留入口类，以最大化混淆效果。
# ----------------------------------------------------------------------------
-keep class com.github.heartratemonitor_compose.HeartRateApp { *; }
-keep class com.github.heartratemonitor_compose.ui.main.MainActivity { *; }
-keep class com.github.heartratemonitor_compose.service.BleService { *; }
-keep class com.github.heartratemonitor_compose.service.FloatingWindowService { *; }
-keep class com.github.heartratemonitor_compose.service.StatusBarResidentService { *; }
-keep class com.github.heartratemonitor_compose.service.HeartRateAlarmService { *; }
-keep class com.github.heartratemonitor_compose.service.FairMemoryReceiver { *; }
-keep class com.github.heartratemonitor_compose.service.FlushRecordsWorker { *; }
-keep class com.github.heartratemonitor_compose.init.** { *; }

# ----------------------------------------------------------------------------
# 12. JNI / Native 调用（如有）
# ----------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ----------------------------------------------------------------------------
# 13. WebView JS 接口（项目暂未使用，保留模板）
# ----------------------------------------------------------------------------
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#    public *;
#}

# ----------------------------------------------------------------------------
# 14. 枚举通用保留
# ----------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ----------------------------------------------------------------------------
# 15. Parcelable / Serializable（Intent 传递）
# ----------------------------------------------------------------------------
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# 17. Release 构建剥离日志（debug 构建不经过 R8，日志自动保留）
# ============================================================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ----------------------------------------------------------------------------
# 16. R 文件（资源 ID 引用）
#     R8 默认会将 R 字段内联为编译期常量并移除 R 类本身（mapping.txt 已验证
#     heartratemonitor_compose.R 不存在）。无需手动 keep。
# ----------------------------------------------------------------------------

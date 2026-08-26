# ============================================================================
# ProGuard / R8 规则配置
# 项目：HeartRateMonitor
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
#    - **$$serializer：kotlinx.serialization 需要，库自带 consumer-rules 已覆盖，
#      项目特定类名保留见下方第 18 节
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
# 9. 项目数据类：Webhook / WebhookTrigger / PostureCalibration
#    - Webhook：通过 kotlinx.serialization 序列化到 DataStore，
#      $$serializer 由库自带 consumer-rules 覆盖，
#      此处保留类名与字段名（序列化输出的 JSON key 需稳定）。
#    - WebhookTrigger：kotlinx.serialization 自定义序列化器输出小写枚举名，
#      通用枚举规则（第 14 节）保留 valueOf 方法签名，此处显式保留常量名。
#    - PostureCalibration / PostureFeatures：同理通过 kotlinx.serialization
#      序列化到 DataStore，保留类名与字段名。
# ----------------------------------------------------------------------------
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.data.Webhook { *; }
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.data.WebhookTrigger { *; }
-keepclassmembers enum com.github.heartratemonitor_compose.data.WebhookTrigger {
    <fields>;
}
# kotlinx.serialization 自定义序列化器 object（被 @Serializable(with = ...) 引用）
-keep class com.github.heartratemonitor_compose.data.ImmutableWebhookTriggerListSerializer { *; }
-keep class com.github.heartratemonitor_compose.data.WebhookTriggerSerializer { *; }
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.service.posture.PostureCalibration { *; }
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.service.posture.PostureFeatures { *; }
-keep class com.github.heartratemonitor_compose.service.posture.ImmutablePostureFeaturesListSerializer { *; }

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
# 11a. Hilt / Dagger 生成代码（迁移 Phase 1~8 后经 R8 release 实证，无需额外规则）
#     - @HiltViewModel（16 个 ViewModel，含 MainViewModel / HistoryViewModel /
#       ChartViewModel / FavoriteDevicesViewModel / HeartRateAlarmViewModel /
#       ServerSettingsViewModel / LanTransferViewModel / WebhookViewModel /
#       ThemeSettingsViewModel / StatusBarSettingsViewModel / NavStyleViewModel /
#       LanguageSettingsViewModel / FunctionSettingsViewModel /
#       FullscreenSoundViewModel / FloatingWindowSettingsViewModel /
#       AboutDetailsViewModel）：Hilt KSP 为每个 @HiltViewModel 生成
#       META-INF/proguard/*_LazyClassKeys.pro（-keep,allowobfuscation,allowshrinking），
#       R8 重命名后运行时 getName() 自洽。
#     - @HiltWorker（FlushRecordsWorker）：androidx.hilt:hilt-work 自带
#       -keepnames @HiltWorker class * extends ListenableWorker；上方第 11 节另有
#       显式 keep 双保险（Worker 类名是 Hilt multibinding map key，禁止混淆）。
#     - @EntryPoint：已全部移除（MainDependencies / ServerDependencies /
#       SettingsDependencies cast 链已删除），当前无使用方，hilt-android 自带的
#       -keep,allowobfuscation,allowshrinking @EntryPoint class * 仍作兜底。
#     - @AndroidEntryPoint 组件基类（Hilt_*）：manifest 引用 + 上方第 11 节保留。
#     - 生成组件（Dagger*_HiltComponents / *_Factory）：R8 正常处理，未用子组件
#       （Fragment/View）自动收缩，usage.txt 已验证为成员级优化。
#     若升级 Hilt 后 release 运行出现 ClassNotFoundException，优先查 seeds.txt
#     是否缺失对应类，再按官方 consumer-rules 补充。
# ----------------------------------------------------------------------------

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

# ----------------------------------------------------------------------------
# 18. kotlinx.serialization（navigation3 返回栈持久化：AppNavKey 为 @Serializable）
#     AppNavKey 及其 18 个子类型被 navigation3 rememberNavBackStack 用于持久化
#     返回栈，运行时通过编译器生成的 $$serializer 反射序列化/反序列化。
#
#     kotlinx.serialization 自带的 consumer-rules（rules/common.pro）已覆盖：
#     - @Serializable 类的 Companion / INSTANCE / serializer() 方法
#     - $$serializer 的 descriptor 字段
#     - RuntimeVisibleAnnotations / AnnotationDefault 属性
#
#     此处仅补充项目特定的 AppNavKey sealed 接口及其全部嵌套子类型，
#     确保多态序列化的类名（type discriminator）不被混淆——
#     官方规则保留 Companion 和 $$serializer，但不保留类名本身，
#     而密封类的序列化输出以 "companion_qualified_name" 作为子类型标签。
# ----------------------------------------------------------------------------
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.ui.AppNavKey { *; }
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.ui.AppNavKey$* { *; }
-keep,includedescriptorclasses class com.github.heartratemonitor_compose.ui.AppNavKey$*$* { *; }

# ----------------------------------------------------------------------------
# 19. Coil 3（图片加载，自带 consumer-rules，仅 dontwarn 兜底）
#     用于关于页维护者头像加载，Coil 3 内部依赖 OkHttp，其自带 consumer-rules
#     已覆盖 OkHttp 与 Okio 核心类，此处仅防止传递依赖警告。
# ----------------------------------------------------------------------------
-dontwarn coil3.**

# ----------------------------------------------------------------------------
# 19a. Backdrop / Capsule / Shapes（io.github.kyant0 液态玻璃 / 连续曲率形状）
#      纯 Kotlin Compose 库，无反射、无注解处理、无 consumer-rules.pro，
#      通过 Modifier 扩展函数 + RenderNode 工作；R8 正常混淆不影响运行。
#      仅 -dontwarn 兜底防止传递依赖（androidx compose 等）的 missing class 警告。
# ----------------------------------------------------------------------------
-dontwarn com.kyant.backdrop.**
-dontwarn com.kyant.capsule.**
-dontwarn com.kyant.shapes.**

# ----------------------------------------------------------------------------
# 19b. Vico 图表（com.patrykandpatrick.vico，心率历史折线图）
#      纯 Kotlin Compose 库，KMP 打包无 consumer-rules.pro，
#      Compose 层经 rememberXxx() 创建状态对象，无反射；R8 正常处理。
#      仅 -dontwarn 兜底。
# ----------------------------------------------------------------------------
-dontwarn com.patrykandpatrick.vico.**

# ----------------------------------------------------------------------------
# 20. MaterialKolor PaletteStyle：枚举名被持久化到 DataStore
#     ThemeState 通过 PaletteStyle.valueOf(String) 反射读取持久化的枚举名，
#     通用枚举规则（第 14 节）只保留 valueOf 方法签名，不保留枚举常量字段名，
#     需显式保留常量名，否则重命名后 valueOf 抛 IllegalArgumentException。
# ----------------------------------------------------------------------------
-keepclassmembers enum com.materialkolor.PaletteStyle {
    <fields>;
}

# ----------------------------------------------------------------------------
# 21. kotlin.uuid（Kable 0.44.x 使用 Kotlin 2.x 标准 UUID）
#     项目 Kotlin 2.4.10，kotlin.uuid 为标准库内联类，不会缺失；
#     第 2 节 -dontwarn kotlin.** 已覆盖此包，无需重复声明。
#     Kable 自带 consumer-rules 已覆盖核心 BLE 类，第 6 节另有显式 keep。
# ----------------------------------------------------------------------------

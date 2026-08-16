plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.heartratemonitor_compose.ui.theme"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    // 3.3.3 唯一特例：designsystem → data:settings（ThemeState/LiquidGlassState 的持久化源，
    // Phase 6 迁入后生效）。implementation 足够：SettingsRepository 不在本模块公开 API 上。
    implementation(project(":data:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.materialkolor)

    // Capsule：ExpressShapes 连续曲率圆角（G2 continuity，与底部导航栏 ContinuousCapsule 同源）
    implementation(libs.capsule)

    // Hilt：ThemeState / LiquidGlassState / CustomSchemeCache @Inject 构造
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 单元测试：ThemeState / LiquidGlassState 配置快照与写回（Robolectric + DataStore）
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.heartratemonitor_compose.ui.widgets"
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
    // api：FloatingBottomBar 公开参数暴露 LiquidGlassConfig（:core:designsystem）；
    // SoundModeUtils 公开参数暴露 SettingsRepository（:data:settings，3.3.1 允许的 core→data:settings）
    api(project(":core:designsystem"))
    api(project(":data:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.backdrop)
    implementation(libs.capsule)
    implementation(libs.kotlinx.coroutines.android)
    // MviViewModel 基类（ui/mvi）依赖 lifecycle-viewmodel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // 单元测试：MviViewModel 基类（CAS 并发 / dispatch 串行性）
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

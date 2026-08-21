plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.heartratemonitor_compose.data.repository"
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
    // api：Repository 公开 API（方法返回类型 / 构造参数）暴露 :core:model、:data:settings、:data:database 类型，
    // :app 组合根装配时需要可见（3.5.5 规则）
    api(project(":core:model"))
    api(project(":data:settings"))
    api(project(":data:database"))

    // Hilt：各 Repository / Provider @Inject 构造
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 单元测试：FavoriteDeviceRepository 迁移逻辑（Robolectric + Room 内存库 + DataStore）
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.room3:room3-testing:3.0.0")
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation("org.json:json:20240303")
}

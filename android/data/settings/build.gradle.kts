plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.heartratemonitor_compose.data.settings"
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
    // api：SettingsKeys / SettingsRepository 公开 API 暴露 Preferences.Key / DataStore 类型，
    // 使用方（:app 的 KillStateSaver、ServiceBootInitializer 与测试）需在编译类路径看到 datastore
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)

    // Hilt：SettingsModule @Provides + @AppScope Qualifier
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

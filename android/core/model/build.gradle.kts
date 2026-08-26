plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.heartratemonitor_compose.data.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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
    // ImmutableList：Webhook.triggers 稳定化，使 ImmutableList<Webhook> 被 Compose 编译器识别为稳定类型
    api(libs.kotlinx.collections.immutable)
    // kotlinx-serialization：Webhook 持久化到 DataStore 时序列化/反序列化
    implementation(libs.kotlinx.serialization.json)

    // 单元测试：Webhook 序列化 round-trip 与旧格式兼容
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

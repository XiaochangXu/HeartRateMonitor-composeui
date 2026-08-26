plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.heartratemonitor_compose.service"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    api(project(":core:model"))
    api(project(":core:designsystem"))
    api(project(":data:settings"))
    api(project(":data:database"))
    api(project(":data:repository"))

    // kotlinx-serialization：PostureCalibration 持久化到 DataStore 时序列化/反序列化
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kable.core.android) {
        exclude(group = "io.opencensus", module = "opencensus-api")
        exclude(group = "io.opencensus", module = "opencensus-proto")
    }

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)

    // Hilt：ServiceController 等 6 个单例 @Inject 构造 + ServiceLauncher @Binds
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Phase 6：FlushRecordsWorker @HiltWorker（androidx.hilt:hilt-work + 专用 compiler）
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    testImplementation("androidx.room3:room3-testing:3.0.0")
    testImplementation(libs.androidx.sqlite.framework)
}

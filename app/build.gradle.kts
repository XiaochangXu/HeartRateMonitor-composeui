plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("../.key/key")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    namespace = "com.github.heartratemonitor_compose"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.heartratemonitor_compose"
        minSdk = 27
        targetSdk = 37
        versionCode = 20
        versionName = "2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-v8a：现代设备（minSdk 27）几乎全部支持
        // x86_64：模拟器调试支持
        ndk {
            abiFilters += "arm64-v8a"
            abiFilters += "x86_64"
        }
    }

    buildTypes {
        release {
            // 开启代码混淆和压缩
            isMinifyEnabled = true
            // 开启资源压缩
            isShrinkResources = true
            // release 构建使用专用签名
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Vico 纯 Compose 图表库（替代 MPAndroidChart）
    implementation(libs.vico.compose.m3)

    // Navigation Compose（单 Activity 架构）
    implementation(libs.androidx.navigation.compose)

    // LifecycleService（服务覆盖层 ComposeView 需要）
    implementation(libs.androidx.lifecycle.service)

    // SavedStateRegistry（ServiceViewTreeOwners 需要）
    implementation(libs.androidx.savedstate)

    // FragmentActivity 基类（PermissionX 要求）
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // LiveData 与 Compose 互操作（observeAsState）
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    // lifecycle-runtime-compose 提供 observeAsState 等
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ViewModel and LiveData for modern UI development
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)

    // Kable for Bluetooth LE
    implementation(libs.kable.core.android)

    // Permissions
    implementation(libs.permissionx)

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
}

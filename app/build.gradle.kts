plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

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
        minSdk = 24
        targetSdk = 37
        versionCode = 27
        versionName = "3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.vico.compose.m3)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.savedstate)

    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.kable.core.android) {
        exclude(group = "io.opencensus", module = "opencensus-api")
        exclude(group = "io.opencensus", module = "opencensus-proto")
    }

    implementation(libs.permissionx)

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)

    implementation(libs.materialkolor)
}

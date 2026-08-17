plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            storeFile = file("../.key/app-release.jks")
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
        versionCode = 45
        versionName = "4.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

    }

    androidResources {
        localeFilters += listOf("en", "zh")
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
            ndk {
                debugSymbolLevel = "none"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

project.afterEvaluate {
    
    val localProps = project.rootProject.file("local.properties")
    val sdkDir: File? = if (localProps.exists()) {
        localProps.readLines()
            .firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("sdk.dir=")
            ?.trim()
            ?.let { File(it) }
    } else null
        ?: System.getenv("ANDROID_HOME")?.let { File(it) }
        ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }

    if (sdkDir == null || !sdkDir.isDirectory) {
        logger.warn("未找到 Android SDK 目录，跳过 --strip-all")
        return@afterEvaluate
    }

    val exeSuffix =
        if (System.getProperty("os.name").lowercase().startsWith("win")) ".exe" else ""
    val objcopy = sdkDir.resolve("ndk")
        .listFiles()
        ?.filter { it.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstNotNullOfOrNull { ndk ->
            ndk.resolve("toolchains/llvm/prebuilt")
                .listFiles()?.firstOrNull()
                ?.resolve("bin/llvm-objcopy$exeSuffix")
                ?.takeIf { it.exists() }
        }

    if (objcopy == null) {
        logger.warn("llvm-objcopy 未在 NDK 中找到，跳过 --strip-all")
        return@afterEvaluate
    }

    val rootDir = project.rootDir
    tasks.matching { it.name == "stripReleaseDebugSymbols" }.configureEach {
        doLast {
            val libDir = layout.buildDirectory
                .dir("intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib")
                .get().asFile
            if (!libDir.isDirectory) return@doLast
            libDir.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .forEach { so ->
                    val proc = ProcessBuilder(objcopy.absolutePath, "--strip-all", so.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val exitCode = proc.waitFor()
                    if (exitCode == 0) {
                        logger.lifecycle("已完全剥离符号: ${so.relativeTo(rootDir)}")
                    } else {
                        logger.warn("剥离失败 (exit $exitCode): ${so.name} — ${proc.inputStream.bufferedReader().readText()}")
                    }
                }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {

    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":data:settings"))
    implementation(project(":data:database"))
    implementation(project(":data:repository"))
    implementation(project(":service"))
    implementation(project(":feature:favorite"))
    implementation(project(":feature:webhook"))
    implementation(project(":feature:history"))
    implementation(project(":feature:alarm"))
    implementation(project(":feature:server"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:main"))

    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.profileinstaller)

    implementation(libs.vico.compose.m3)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.savedstate)

    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material.ripple)

    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room3:room3-testing:3.0.0")
    testImplementation("androidx.sqlite:sqlite-framework:2.7.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.kable.core.android) {
        exclude(group = "io.opencensus", module = "opencensus-api")
        exclude(group = "io.opencensus", module = "opencensus-proto")
    }

    implementation(libs.permissionx)

    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)

    implementation(libs.materialkolor)

    implementation(libs.backdrop)
    implementation(libs.capsule)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    // Phase 6：HeartRateApp 实现 Configuration.Provider 注入 HiltWorkerFactory
    implementation(libs.hilt.work)
}

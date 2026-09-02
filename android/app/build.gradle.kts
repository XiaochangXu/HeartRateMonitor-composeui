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
        versionCode = 55
        versionName = "4.9.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

    }

    androidResources {
        localeFilters += listOf(
            "en", "zh", "zh-rTW", "zh-rHK",
            "ja", "ko", "de", "ru", "fr", "es", "pt", "it", "pl", "nl", "tr",
            "in", "hi", "vi", "th", "fil", "ms", "bn", "ar", "ne"
        )
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
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
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

val localProps = rootProject.file("local.properties")
val sdkDir: File? = if (localProps.exists()) {
    localProps.readLines()
        .firstOrNull { it.startsWith("sdk.dir=") }
        ?.substringAfter("sdk.dir=")
        ?.trim()
        ?.let { File(it) }
} else null
    ?: System.getenv("ANDROID_HOME")?.let { File(it) }
    ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }

val exeSuffix =
    if (System.getProperty("os.name").lowercase().startsWith("win")) ".exe" else ""
val objcopyPath: File? = sdkDir?.takeIf { it.isDirectory }
    ?.resolve("ndk")
    ?.listFiles()
    ?.filter { it.isDirectory }
    ?.sortedByDescending { it.name }
    ?.firstNotNullOfOrNull { ndk ->
        ndk.resolve("toolchains/llvm/prebuilt")
            .listFiles()?.firstOrNull()
            ?.resolve("bin/llvm-objcopy$exeSuffix")
            ?.takeIf { it.exists() }
    }

if (objcopyPath == null) {
    logger.warn("llvm-objcopy 未在 NDK 中找到，跳过 --strip-all")
} else {
    val rootDirFile = rootDir
    val objcopyFile = objcopyPath
    val libDirProvider = layout.buildDirectory.dir(
        "intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib"
    )
    tasks.matching { it.name == "stripReleaseDebugSymbols" }.configureEach {
        doLast {
            val libDir = libDirProvider.get().asFile
            if (!libDir.isDirectory) return@doLast
            libDir.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .forEach { so ->
                    val proc = ProcessBuilder(objcopyFile.absolutePath, "--strip-all", so.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val exitCode = proc.waitFor()
                    if (exitCode == 0) {
                        logger.lifecycle("已完全剥离符号: ${so.relativeTo(rootDirFile)}")
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

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

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
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.sqlite.framework)
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
    implementation(libs.hilt.work)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.happycola233.TimeShot.Renamer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.happycola233.TimeShot.Renamer"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 仅打包需要的语言（按需调整；比如只支持英文就保留 "en"）
        // 不会移除依赖，只是裁剪其中多余的本地化资源
        resourceConfigurations += setOf("en")
    }

    buildTypes {
        release {
            // 关键：启用 R8 混淆/压缩
            isMinifyEnabled = true

            // 关键：资源收缩（会在 R8 后移除未被引用的 res）
            isShrinkResources = true

            // 可选：压 PNG（若项目大量 PNG，会有帮助；矢量图影响很小）
            isCrunchPngs = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 确保 release 不是可调试构建
            isDebuggable = false
        }
        debug {
            // 保持 debug 行为不变
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // 进一步减少生成物：去掉不必要的 BuildConfig
    buildFeatures {
        compose = true
        buildConfig = false
    }

    // 打包阶段删除无用元数据/许可文件/调试探针，能明显瘦身
    packaging {
        resources {
            // 常见 LICENSE/NOTICE/版本元数据等（对运行无影响）
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                // Kotlin 协程调试探针（运行不需要）
                "DebugProbesKt.bin"
            )
        }
    }

    // 如需发布“最小”APK，启用多 APK 拆分（ABI/密度）。
    // 这不会减少依赖，但会为每种 ABI/密度生成更小的单独 APK。
    // 如果你只发一个通用 APK，请删除此块。
    splits {
        abi {
            isEnable = true
            // 若仅支持 arm64，可只包含 arm64-v8a；若有 JNI 则收益更大
            reset()
            include("arm64-v8a")
            // 不生成通用 APK（否则体积会变大）
            isUniversalApk = false
        }
        density {
            // 按屏幕密度拆分资源（矢量图多时收益有限）
            isEnable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        // 不建议用激进编译器开关换体积，保持运行时语义稳定
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview) // 仅包含预览注解 stub，R8 会去除未引用代码
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aiclassroom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiclassroom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 44
        versionName = "4.0.3"
    }

    // 签名：debug 与 release 共用同一把 developer 钥匙。
    // 此前 debug 走 Android Studio 默认的 debug.keystore、release 走 developer 钥，
    // 两把钥匙互不兼容，导致先装过 debug 版的设备无法直接覆盖安装 release 版
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE)。
    // CI/release machines can provide their own signing material through
    // environment variables; local builds fall back to the developer keystore
    // kept outside version control.
    val localKeystore = rootProject.file("signing/ai-classroom-2x.jks")
    val localPasswordFile = rootProject.file("signing/keystore-2x-password.txt")
    val localPassword = localPasswordFile
        .takeIf { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val environmentSigningPath = providers.environmentVariable("AI_CLASSROOM_KEYSTORE").orNull
        ?.takeIf { it.isNotBlank() }
    val environmentSigningPassword = providers
        .environmentVariable("AI_CLASSROOM_KEYSTORE_PASSWORD")
        .orNull
        ?.takeIf { it.isNotBlank() }
    val environmentSigningAlias = providers.environmentVariable("AI_CLASSROOM_KEY_ALIAS").orNull
        ?.takeIf { it.isNotBlank() }
    val hasCompleteEnvironmentSigning =
        !environmentSigningPath.isNullOrBlank() &&
            !environmentSigningPassword.isNullOrBlank() &&
            !environmentSigningAlias.isNullOrBlank()

    val signingPath = if (hasCompleteEnvironmentSigning) {
        environmentSigningPath
    } else {
        localKeystore.absolutePath.takeIf { localKeystore.isFile }
    }
    val signingPassword = if (hasCompleteEnvironmentSigning) {
        environmentSigningPassword
    } else {
        localPassword
    }
    val signingAlias = if (hasCompleteEnvironmentSigning) {
        environmentSigningAlias
    } else {
        "androiddebugkey".takeIf { !localPassword.isNullOrBlank() }
    }

    // 签名材料齐全时，debug / release 共用这一把钥匙；
    // 缺失时 debug 退回默认 debug.keystore，release 按原样出未签名包。
    val sharedDeveloperSigning = if (
        !signingPath.isNullOrBlank() &&
        !signingPassword.isNullOrBlank() &&
        !signingAlias.isNullOrBlank() &&
        file(signingPath).isFile
    ) {
        signingConfigs.create("releaseDeveloper") {
            storeFile = file(signingPath)
            storePassword = signingPassword
            keyAlias = signingAlias
            keyPassword = signingPassword
            // Keep legacy JAR (v1) and modern APK (v2/v3) signatures so
            // OEM installers and older Android tooling recognize the package.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    } else {
        logger.warn(
            "Signing material is not configured; debug falls back to the default " +
                "debug.keystore and the Release artifact will be unsigned. " +
                "Set AI_CLASSROOM_KEYSTORE, AI_CLASSROOM_KEYSTORE_PASSWORD and " +
                "AI_CLASSROOM_KEY_ALIAS for a custom signing key."
        )
        null
    }

    buildTypes {
        debug {
            // Android Studio 跑出的 debug 版用同一把钥匙，保证能与 release 直接覆盖安装。
            sharedDeveloperSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            sharedDeveloperSigning?.let { signingConfig = it }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 开启后 BuildConfig.VERSION_NAME 由 versionName 自动生成，
        // 避免再出现「APP_VERSION 常量忘了跟着版本号改」导致误报更新。
        buildConfig = true
    }
}

dependencies {
    implementation(project(":feature-exam"))
    implementation(project(":feature-tools-fs"))
    implementation(project(":feature-tools-net"))
    implementation(project(":feature-plugin"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui:1.7.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.4")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.4")
    implementation("dev.chrisbanes.haze:haze:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.4")
    // 纯 JVM 单测：视频抽帧的差异计算 / 选帧算法都只依赖 IntArray / List<Float>，
    // 不需要 Robolectric，直接在 JVM 上跑。
    testImplementation("junit:junit:4.13.2")
}

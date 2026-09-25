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
        versionCode = 37
        versionName = "3.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // CI/release machines can provide their own signing material through
            // environment variables. For local Android Studio builds, fall back
            // to the developer keystore kept outside version control.
            val localKeystore = rootProject.file("signing/ai-classroom-developer.jks")
            val localPasswordFile = rootProject.file("signing/keystore-password.txt")
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
                "ai-classroom-developer".takeIf { !localPassword.isNullOrBlank() }
            }

            if (!signingPath.isNullOrBlank() &&
                !signingPassword.isNullOrBlank() &&
                !signingAlias.isNullOrBlank() &&
                file(signingPath).isFile
            ) {
                signingConfig = signingConfigs.create("releaseDeveloper")
                signingConfig?.storeFile = file(signingPath)
                signingConfig?.storePassword = signingPassword
                signingConfig?.keyAlias = signingAlias
                signingConfig?.keyPassword = signingPassword
                // Keep legacy JAR (v1) and modern APK (v2/v3) signatures so
                // OEM installers and older Android tooling recognize the package.
                signingConfig?.enableV1Signing = true
                signingConfig?.enableV2Signing = true
                signingConfig?.enableV3Signing = true
            } else {
                logger.warn(
                    "Release signing is not configured; the Release artifact will be unsigned. " +
                        "Set AI_CLASSROOM_KEYSTORE, AI_CLASSROOM_KEYSTORE_PASSWORD and " +
                        "AI_CLASSROOM_KEY_ALIAS for a custom signing key."
                )
            }
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui:1.7.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.4")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.4")
}

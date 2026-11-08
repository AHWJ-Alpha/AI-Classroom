plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

// 纯 Kotlin JVM 模块：不含 Android / Compose 依赖，只放文件工具实现。
// :app 是 Android 模块，依赖 JVM 模块由 AGP 直接支持（jar 打进 APK 的 dex）。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // ToolHandler / ToolResult / ToolRegistry 由 feature-exam 提供（考/文件/网络三条线共用一套契约）。
    implementation(project(":feature-exam"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

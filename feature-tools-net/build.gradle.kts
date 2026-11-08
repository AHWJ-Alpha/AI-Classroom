plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

// 纯 Kotlin JVM 模块：不含 Android / Compose 依赖，只放考试数据层与工具契约。
// :app 是 Android 模块，依赖 JVM 模块由 AGP 直接支持（jar 打进 APK 的 dex）。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 网络工具实现的是 :feature-exam 里的 ToolHandler / ToolResult 契约。
dependencies {
    implementation(project(":feature-exam"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

// 纯 Kotlin JVM 模块：课堂的领域模型、规划契约、状态机与验证器。
// 不含 Android / Compose / Room 依赖 —— 宿主(Android)负责实现存储与调度，
// 领域只通过接口依赖 model gateway / repository / clock。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    // 网关是 suspend 的，需要协程；版本对齐 Gradle 缓存里已有的 1.8.0。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

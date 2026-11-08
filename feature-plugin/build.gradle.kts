plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

// 纯 Kotlin/JVM 模块：插件包格式（.aip/.zip）+ Rhino JS 引擎 + 宿主能力契约 + prompt 钩子 + 事件/定时器分发。
// 不含 Android / Compose 依赖；界面与宿主能力由 :app 实现 PluginHostApi 后注入。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Mozilla Rhino：纯 Java 的 ES5 引擎，Android 可直接使用（无 NDK 依赖）。
    implementation("org.mozilla:rhino:1.7.14")
    // PluginEventBus.startTimers 的签名里有 CoroutineScope，属于本模块的公开 API 面，所以用 api 而不是 implementation。
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

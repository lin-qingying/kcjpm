plugins {
    // 应用来自约定插件的共享构建逻辑。
    // 共享代码位于 `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`。
    id("buildsrc.convention.kotlin-jvm")
    // 从 `gradle/libs.versions.toml` 应用 Kotlin Serialization 插件。
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // 从版本目录（`gradle/libs.versions.toml`）应用 kotlinx 依赖包。
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}
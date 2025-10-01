plugins {
    // Kotlin DSL 插件提供了一种便捷的方式来开发约定插件。
    // 约定插件位于 `src/main/kotlin` 中，文件扩展名为 `.gradle.kts`，
    // 并根据需要在项目的 `build.gradle.kts` 文件中应用。
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // 添加对 Kotlin Gradle 插件的依赖，以便约定插件可以应用它。
    implementation(libs.kotlinGradlePlugin)
}

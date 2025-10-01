// settings 文件是每个 Gradle 构建的入口点。
// 它的主要目的是定义子项目。
// 它还用于项目范围配置的某些方面，如管理插件、依赖项等。
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // 在所有子项目中使用 Maven Central 作为默认仓库（Gradle 将从此处下载依赖项）。
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    // 使用 Foojay Toolchains 插件自动下载子项目所需的 JDK。
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// 在构建中包含 `app` 和 `utils` 子项目。
// 如果只有其中一个项目发生更改，Gradle 将仅重新构建已更改的项目。
// 了解更多关于使用 Gradle 构建项目的信息 - https://docs.gradle.org/8.7/userguide/multi_project_builds.html
include(":core")
include(":utils")
include(":config-toml")

rootProject.name = "kcjpm"
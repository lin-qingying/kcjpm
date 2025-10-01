dependencyResolutionManagement {

    // 在共享构建逻辑（`buildSrc`）项目中使用 Maven Central 和 Gradle 插件门户来解析依赖项。
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    // 重用主构建中的版本目录。
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"
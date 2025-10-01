# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码仓库中工作时提供指导。

## 构建命令

这是一个使用 Gradle Wrapper 的 Kotlin JVM 项目。

- **构建**: `./gradlew build`
- **运行**: `./gradlew run`
- **测试**: `./gradlew check` (运行所有检查，包括测试)
- **清理**: `./gradlew clean`

在 Windows 上，使用 `gradlew.bat` 而非 `./gradlew`。

## 架构

### 多模块结构

此项目遵循 Gradle 的多模块模式，包含两个子项目：

- **core** (`:core`): 主应用程序模块。
- **utils** (`:utils`): 共享工具模块，支持 Kotlin 序列化。

### 约定插件系统

共享构建逻辑被提取到 `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` 中的约定插件。两个子项目都通过 `id("buildsrc.convention.kotlin-jvm")` 应用此插件。

约定插件的关键设置：
- Kotlin JVM 工具链：Java 21
- 测试框架：JUnit Platform
- 测试日志：显示 PASSED、FAILED 和 SKIPPED 事件

### 依赖管理

- 版本目录：`gradle/libs.versions.toml` 集中管理依赖声明
- 仓库：Maven Central (在 `settings.gradle.kts` 中配置)
- `utils` 模块使用 `kotlinxEcosystem` 依赖包 (kotlinx-datetime, kotlinx-serialization-json, kotlinx-coroutines-core)

### 性能特性

在 `gradle.properties` 中启用：
- 构建缓存 (`org.gradle.caching=true`)
- 配置缓存 (`org.gradle.configuration-cache=true`)
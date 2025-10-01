# KCJPM

**仓颉语言构建与包管理工具 (Cangjie Language Build & Package Manager)**

KCJPM 是一个使用 Kotlin 编写的仓颉语言构建与包管理工具，提供核心编译逻辑支持。虽然 Kotlin 实现可能在性能上不如原生语言，但本项目的设计目标是实现高扩展性，使得功能模块可以灵活组合和扩展。

## 特性

- 仓颉语言编译支持
- 包依赖管理
- 模块化架构设计
- 基于 Kotlin 的高扩展性实现

## 快速开始

本项目使用 [Gradle](https://gradle.org/) 构建系统和 Gradle Wrapper。

### 构建与运行

```bash
# 构建并运行应用
./gradlew run

# 仅构建应用
./gradlew build

# 运行所有检查和测试
./gradlew check

# 清理构建输出
./gradlew clean
```

**Windows 用户**：使用 `gradlew.bat` 替代 `./gradlew`

## 项目架构

### 多模块结构

- **core** (`:core`): 主应用程序模块，包含核心编译逻辑和入口点
- **utils** (`:utils`): 共享工具模块，提供序列化和通用工具支持

### 技术栈

- **语言**: Kotlin (JVM Target: Java 21)
- **构建工具**: Gradle 8.x with Kotlin DSL
- **依赖管理**: Version Catalog (`gradle/libs.versions.toml`)
- **性能优化**: Build Cache & Configuration Cache

### 构建配置

共享构建逻辑通过约定插件 (`buildSrc`) 统一管理，确保各模块配置一致性。。

## 开发

使用 IntelliJ IDEA 或其他 Kotlin IDE 打开项目，Gradle 会自动导入依赖。

## 了解更多

- [Gradle Wrapper 使用指南](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle 任务参考](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks)

## 许可

Apache License 2.0
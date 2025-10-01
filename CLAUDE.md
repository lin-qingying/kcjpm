# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码仓库中工作时提供指导。

## 项目概述

KCJPM (Kotlin-based Cangjie Package Manager) 是一个使用 Kotlin 编写的仓颉语言构建与包管理工具。项目采用模块化架构设计，优先考虑扩展性，使得功能模块可以灵活组合和扩展。

## 构建命令

这是一个使用 Gradle Wrapper 的 Kotlin JVM 项目，**要求 Java 21**。

### 基本命令
- **构建项目**: `./gradlew build`
- **运行应用**: `./gradlew run`
- **运行所有测试**: `./gradlew check`
- **清理构建输出**: `./gradlew clean`
- **运行特定模块测试**: `./gradlew :core:test` 或 `./gradlew :config-toml:test`
- **运行单个测试类**: `./gradlew :core:test --tests "org.cangnova.kcjpm.dependency.DependencyManagerTest"`

在 Windows 上，使用 `gradlew.bat` 而非 `./gradlew`。

### 常见问题
- 如果遇到 "Dependency requires at least JVM runtime version 21" 错误，确保使用 Java 21 或更高版本
- 配置缓存已启用，如遇到问题可使用 `--no-configuration-cache` 禁用

## 架构设计

### 多模块结构

项目包含三个主要模块：

- **`:core`**: 核心模块，包含主应用逻辑、数据模型和接口定义
  - 依赖管理系统（接口和实现）
  - 编译上下文和构建配置
  - 配置解析接口（不含具体实现）
  
- **`:config-toml`**: TOML 配置解析实现模块
  - 实现 `ConfigParser` 接口
  - 包含 TOML 特定的解析逻辑
  - 依赖于 `:core` 模块
  
- **`:utils`**: 共享工具模块
  - 通用工具函数
  - Kotlin 序列化支持

### 依赖管理系统架构

依赖管理系统采用**策略模式 + 门面模式**设计，分为三层：

1. **门面层** (`DependencyManager`)
   - 统一入口，提供高级 API
   - 处理传递依赖解析
   - 版本冲突检测
   - 缓存管理

2. **解析层** (`DependencyResolver`)
   - 协调多个拉取器
   - 根据依赖类型选择合适的拉取器
   - 批量依赖解析

3. **拉取层** (`DependencyFetcher`)
   - **策略模式**：每种依赖类型一个拉取器
   - `PathDependencyFetcher`: 本地路径依赖
   - `GitDependencyFetcher`: Git 仓库依赖（浅克隆）
   - `RegistryDependencyFetcher`: 远程仓库依赖

**依赖类型**：
- **PATH**: 本地路径依赖（工作空间开发）
- **GIT**: Git 仓库依赖（支持 tag、branch、commit）
- **REGISTRY**: 远程仓库依赖（默认：https://repo.cangjie-lang.cn）

**传递依赖解析流程**：
1. 解析直接依赖
2. 递归读取每个依赖的 `cjpm.toml`
3. 使用 visited 集合避免循环依赖
4. `distinctBy { it.name }` 去重
5. 版本冲突检测（同名依赖不同版本会抛出异常）

### 配置解析系统架构

配置解析系统采用**策略模式**，支持多种配置格式：

1. **接口层** (`ConfigParser`)
   - 定义统一的配置加载接口
   - 方法：`loadConfig()`, `loadFromProjectRoot()`, `loadAndConvert()`, `getConfigFileName()`

2. **门面层** (`ConfigLoader`)
   - 单例对象，作为统一入口
   - 通过 `setParser()` 设置具体实现
   - 所有方法委托给注册的 parser

3. **实现层** (独立模块)
   - `:config-toml` 模块：`TomlConfigParser` 实现
   - 未来可扩展：config-json, config-yaml 等

**使用方式**：
```kotlin
// 在测试或应用初始化时设置 parser
ConfigLoader.setParser(TomlConfigParser())

// 使用统一接口加载配置
val config = ConfigLoader.loadFromProjectRoot(projectRoot).getOrThrow()
```

**扩展新格式**：
1. 创建新模块（如 `:config-json`）
2. 实现 `ConfigParser` 接口
3. 调用 `ConfigLoader.setParser()` 注册

### 编译上下文 (CompilationContext)

`CompilationContext` 封装了编译所需的所有信息：
- 项目根目录
- 构建配置（目标平台、优化级别、并行编译等）
- 依赖列表
- 源文件列表
- 输出路径

**Builder 模式构建**：
```kotlin
DefaultCompilationContext.builder()
    .projectRoot(projectRoot)
    .buildConfig(BuildConfig(CompilationTarget.LINUX_X64))
    .addSourceFile(sourceFile)
    .addDependency(dependency)
    .outputPath(outputDir)
    .build()
```

**编译目标平台** (`CompilationTarget`):
- LINUX_X64, LINUX_ARM64
- WINDOWS_X64
- MACOS_X64, MACOS_ARM64
- 使用 `CompilationTarget.current()` 检测当前平台

### 数据模型 (CjpmConfig)

`CjpmConfig` 使用 kotlinx.serialization 和 tomlkt 序列化：
- **Package Info**: name, version, cjc-version, output-type, authors, license 等
- **Dependencies**: 支持简写字符串（版本号）或详细对象（path, git, registry）
- **Registry**: 仓库配置（default, mirrors, private-url）
- **Build**: 构建配置（source-dir, output-dir, parallel, incremental 等）
- **Profile**: 构建配置文件（debug, release, release-lto）
- **Workspace**: 工作空间配置（members, default-members）

**特殊注意**：
- `DependencyMapSerializer` 只支持反序列化，不支持序列化
- 测试中需要手动构建 TOML 字符串（见 `BaseTest.kt` 的 `writeConfig` 扩展函数）

## 测试框架

使用 **Kotest 5.9.1** 和 **FunSpec** 风格：

```kotlin
class MyTest : BaseTest() {
    init {
        test("测试描述") {
            // 测试代码
            result.isSuccess shouldBe true
        }
    }
}
```

**测试工具** (`BaseTest.kt`):
- `createTestProject()`: 创建临时测试项目
- `createTempDir()`: 创建临时目录
- `simpleToml()`: 生成基本 TOML 配置
- `tomlWithDependencies()`: 生成带依赖的 TOML 配置
- `TestProject.createSourceFile()`: 创建源文件
- `TestProject.createDependency()`: 创建依赖目录
- `Path.writeConfig(CjpmConfig)`: 手动构建 TOML 配置文件

**测试初始化**：
- 所有使用 `ConfigLoader` 的测试必须先调用 `ConfigLoader.setParser(TomlConfigParser())`
- 路径比较使用 `normalize()` 确保跨平台兼容性

**真实 Git 仓库测试**：
- Git 测试使用真实仓库：`https://gitcode.com/Cangjie-TPC/markdown4cj.git`
- 版本：`v1.1.2` (tag), `master` (branch)

## 约定插件系统

共享构建逻辑在 `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`：
- Kotlin JVM 工具链：**Java 21**（必需）
- 测试框架：JUnit Platform
- 测试日志：显示 PASSED、FAILED 和 SKIPPED 事件
- 所有模块通过 `id("buildsrc.convention.kotlin-jvm")` 应用

## 依赖管理

- **版本目录**: `gradle/libs.versions.toml` 集中管理所有依赖版本
- **仓库**: Maven Central (在 `settings.gradle.kts` 中配置)
- **主要依赖**:
  - kotlinx-serialization-json
  - kotlinx-datetime, kotlinx-coroutines-core
  - tomlkt 0.4.0 (TOML 解析)
  - kotest 5.9.1 (测试框架)

## 性能优化

在 `gradle.properties` 中启用：
- 构建缓存: `org.gradle.caching=true`
- 配置缓存: `org.gradle.configuration-cache=true`

## 代码风格

- **测试名称使用中文**
- **不添加注释**（除非明确要求）
- 优先使用 Kotlin 惯用法（如 Result、sealed class、data class）
- 使用 `runCatching` 进行错误处理
- 路径操作使用 `java.nio.file.Path`

## 常见开发模式

### 添加新的依赖类型

1. 在 `DependencyType` 枚举中添加新类型
2. 创建新的 `DependencyFetcher` 实现
3. 在 `DefaultDependencyManager.createDefaultResolver()` 中注册
4. 在 `CjpmConfig` 的 `DependencyConfig` 中添加相应字段

### 添加新的配置格式

1. 创建新模块（如 `:config-json`）
2. 添加依赖到 `:core` 模块
3. 实现 `ConfigParser` 接口
4. 在应用启动时通过 `ConfigLoader.setParser()` 注册
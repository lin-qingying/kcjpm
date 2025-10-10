# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码仓库中工作时提供指导。

## 项目概述

KCJPM (Kotlin-based Cangjie Package Manager) 是一个使用 Kotlin 编写的仓颉语言构建与包管理工具。项目采用模块化架构设计，优先考虑扩展性，使得功能模块可以灵活组合和扩展。

## 架构分层原则

### Core 模块职责边界

**`:core` 模块是编译核心**，遵循以下设计原则：

1. **纯业务逻辑层**
   - 只关注编译流程、依赖管理、配置解析等核心业务
   - 不包含任何控制台输出（如 `println`）
   - 不处理控制台输入输出
   - 不包含 UI 相关逻辑

2. **适配层设计**
   - **CLI 适配层**（后期开发）：处理命令行参数解析、控制台输出格式化、用户交互
   - **IDE 适配层**（后期开发）：处理 IDE 集成、图形界面交互、进度通知

3. **通信机制**
   - Core 通过回调、事件或 Result 类型返回编译状态
   - 适配层负责将状态转换为具体的输出形式（CLI 文本、IDE 通知等）
   - 所有日志、进度、错误信息通过结构化数据传递，不直接打印

**示例**：
```kotlin
// ✅ 正确：Core 返回结构化结果
fun compile(): Result<CompilationResult> {
    return runCatching {
        CompilationResult.Success(outputPath = "...")
    }
}

// ❌ 错误：Core 直接打印输出
fun compile() {
    println("Compiling...")  // 不应该在 Core 中出现
}
```

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

项目包含四个主要模块：

- **`:core`**: 核心模块，包含主应用逻辑、数据模型和接口定义
  - 依赖管理系统（接口和实现）
  - 编译上下文和构建配置
  - 配置解析接口（不含具体实现）
  - **工作空间管理系统**（新增）
  
- **`:config-toml`**: TOML 配置解析实现模块
  - 实现 `ConfigParser` 接口
  - 包含 TOML 特定的解析逻辑
  - 依赖于 `:core` 模块

- **`:config-official`**: 官方配置格式支持模块
  - 实现官方配置格式解析
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

## 工作空间系统

### 概念

**工作空间 (Workspace)** 是包管理器层面的概念，允许在同一仓库中管理多个相关的仓颉项目。

### 工作空间类型

1. **纯工作空间 (Virtual Workspace)**
   - 根目录只包含工作空间配置，自身不是一个包
   - 仅用于组织成员项目
   ```toml
   [workspace]
   members = ["pkg-a", "pkg-b"]
   ```

2. **混合工作空间 (Mixed Workspace)**
   - 根目录既是工作空间，也是一个可编译的包
   - 通常用于主应用 + 子库的场景
   ```toml
   [package]
   name = "my-app"
   version = "1.0.0"
   cjc-version = "1.0.0"
   output-type = "executable"
   
   [workspace]
   members = [".", "libs/core", "libs/utils"]
   default-members = ["."]
   
   [dependencies]
   core = { path = "libs/core" }
   ```

### 核心组件

#### 1. WorkspaceManager
工作空间管理器，提供工作空间加载和构建的统一入口。

```kotlin
interface WorkspaceManager {
    suspend fun loadWorkspace(rootPath: Path): Result<Workspace>
    suspend fun buildWorkspace(workspace: Workspace, members: List<String>?): Result<WorkspaceResult>
    suspend fun buildMember(workspace: Workspace, memberName: String): Result<MemberBuildResult>
    fun isWorkspaceRoot(path: Path): Boolean
}
```

**主要功能**：
- 加载工作空间配置和成员列表
- 支持通配符模式 (`packages/*`)
- 检测是否为工作空间根目录
- 构建整个工作空间或指定成员

#### 2. WorkspaceDependencyGraph
工作空间依赖图，负责分析成员间的依赖关系。

```kotlin
class WorkspaceDependencyGraph(workspace: Workspace) {
    fun topologicalSort(): Result<List<WorkspaceMember>>
    fun detectCycles(): List<List<String>>
    fun getDependents(memberName: String): List<String>
    fun getDependencies(memberName: String): List<String>
    fun getIndependentMembers(): List<WorkspaceMember>
}
```

**主要功能**：
- 拓扑排序（确定编译顺序）
- 循环依赖检测
- 查询成员的依赖和依赖者
- 识别独立成员（无依赖）

#### 3. WorkspaceCompilationCoordinator
工作空间编译协调器，负责并行或串行编译成员。

```kotlin
class WorkspaceCompilationCoordinator(workspace: Workspace) {
    suspend fun buildAll(parallel: Boolean, targetPlatform: CompilationTarget?): Result<Map<String, MemberBuildResult>>
    suspend fun buildMember(memberName: String, targetPlatform: CompilationTarget?): Result<MemberBuildResult>
    suspend fun buildDefaultMembers(parallel: Boolean, targetPlatform: CompilationTarget?): Result<Map<String, MemberBuildResult>>
}
```

**主要功能**：
- 按依赖顺序编译所有成员
- 支持并行编译（无依赖成员）
- 支持串行编译（保证依赖顺序）
- 编译默认成员列表

### 工作空间构建流程

```
1. 加载工作空间
   └─ 解析根 cjpm.toml
   └─ 读取 workspace.members
   └─ 加载每个成员的 cjpm.toml
   
2. 依赖分析
   └─ 构建依赖图
   └─ 拓扑排序确定编译顺序
   └─ 检测循环依赖
   
3. 并行/串行编译
   └─ 无依赖关系的成员可并行编译
   └─ 有依赖关系的成员按拓扑顺序编译
   └─ 使用 Kotlin Coroutines 实现并发
   
4. 结果聚合
   └─ 收集每个成员的编译结果
   └─ 返回 WorkspaceResult
```

### 工作空间成员依赖

工作空间成员通过 `path` 依赖相互引用：

```toml
# libs/utils/cjpm.toml
[package]
name = "utils"
version = "0.1.0"

[dependencies]
core = { path = "../core" }  # 引用同工作空间的成员
```

`PathDependencyFetcher` 已扩展支持工作空间成员依赖：
- 自动从成员的 `cjpm.toml` 读取版本号
- 在工作空间模式下优先使用构建产物

### 配置文件示例

```toml
# 根目录 cjpm.toml（混合工作空间）
[package]
name = "my-app"
version = "1.0.0"
cjc-version = "1.0.0"
output-type = "executable"

[workspace]
members = [".", "libs/*"]      # 支持通配符
default-members = ["."]        # 默认只构建根包

[dependencies]
core = { path = "libs/core" }
utils = { path = "libs/utils" }
```

### 测试

工作空间相关测试位于：
- `WorkspaceManagerTest`: 测试工作空间加载、成员管理
- `WorkspaceDependencyGraphTest`: 测试依赖图、拓扑排序、循环检测

运行测试：
```bash
./gradlew :core:test --tests "org.cangnova.kcjpm.workspace.*"
```
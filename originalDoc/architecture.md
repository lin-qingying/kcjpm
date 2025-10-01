# CJPM 架构设计文档

## 概述
CJPM (Cangjie Package Manager) 是仓颉语言的官方包管理工具，负责仓颉项目的依赖管理、构建、测试、发布等工作流程。

## 核心架构

### 1. 分层架构设计

```
┌─────────────────────────────────────────┐
│          命令行接口层 (CLI Layer)        │
│  - 命令解析                              │
│  - 参数处理                              │
│  - 用户交互                              │
├─────────────────────────────────────────┤
│        命令处理层 (Command Layer)        │
│  - 命令工厂                              │
│  - 具体命令实现                          │
│  - 命令验证                              │
├─────────────────────────────────────────┤
│        业务逻辑层 (Business Layer)       │
│  - 构建管理                              │
│  - 依赖解析                              │
│  - 测试执行                              │
├─────────────────────────────────────────┤
│        配置管理层 (Config Layer)         │
│  - TOML解析                              │
│  - 配置验证                              │
│  - 环境变量处理                          │
├─────────────────────────────────────────┤
│        数据持久层 (Storage Layer)        │
│  - 文件系统操作                          │
│  - 缓存管理                              │
│  - 锁文件管理                            │
└─────────────────────────────────────────┘
```

### 2. 模块组成

#### 2.1 命令模块 (command)
- **功能**: 处理用户命令和参数
- **核心类**: 
  - `CommandFactory`: 命令工厂，根据用户输入创建对应命令处理器
  - `Handle`: 命令处理器基类
  - 具体命令类: `BuildCommand`, `TestCommand`, `RunCommand`, `InitCommand`等

#### 2.2 配置模块 (config) 
- **功能**: 管理项目配置和依赖
- **核心组件**:
  - `Package`: 包信息管理
  - `Dependencies`: 依赖管理
  - `WorkSpace`: 工作空间管理
  - `Profile`: 构建配置文件
  - `Target`: 目标平台配置

#### 2.3 实现模块 (implement)
- **功能**: 核心业务逻辑实现
- **核心组件**:
  - `build.cj`: 构建实现
  - `dep_model.cj`: 依赖模型
  - `topological_sorting.cj`: 拓扑排序
  - `build_parallel.cj`: 并行构建
  - `isolate.cj`: 隔离环境

#### 2.4 TOML解析模块 (toml)
- **功能**: 解析和序列化TOML配置文件
- **核心组件**:
  - `Lexer`: 词法分析器
  - `Parser`: 语法分析器
  - `Encoder/Decoder`: 编码解码器
  - `TomlValue`: TOML值类型系统

## 核心流程

### 1. 命令执行流程

```
main.cj (入口)
    ↓
CommandFactory.createCommandHandler()
    ↓
命令预处理 (runScriptPreCommand)
    ↓
命令执行 (handleCommand)
    ↓
命令后处理 (runScriptPostCommand)
```

### 2. 构建流程

```
doBuild()
    ↓
加载配置 (loadModuleFile)
    ↓
解析依赖 (generateResolveData)
    ↓
准备构建环境 (prepareBuild)
    ↓
增量编译分析 (analyseIncrementalCache)
    ↓
启动构建 (startBuild)
    ↓
执行后处理脚本
```

### 3. 依赖解析流程

```
依赖声明 (cjpm.toml)
    ↓
依赖类型判断 (Path/Git)
    ↓
版本冲突检测
    ↓
依赖下载/链接
    ↓
拓扑排序
    ↓
生成依赖图
```

## 关键设计模式

### 1. 工厂模式
- `CommandFactory`: 根据命令字符串创建具体命令处理器
- 扩展性强，易于添加新命令

### 2. 策略模式
- 不同命令实现不同的 `handleCommand()` 策略
- 支持多种构建配置策略 (debug/release/mock)

### 3. 模板方法模式
- 命令执行的标准流程：预处理→执行→后处理
- 各命令重写具体执行逻辑

### 4. 建造者模式
- 配置对象的构建采用建造者模式
- 支持链式调用和复杂对象构建

## 关键数据结构

### 1. Package 包结构
```cangjie
class Package {
    var cjcVersion: String      // 编译器版本
    var name: String            // 包名
    var version: String         // 版本号
    var outputType: String      // 输出类型
    var compileOption: String   // 编译选项
    var linkOption: String      // 链接选项
}
```

### 2. DepInfo 依赖信息
```cangjie
class DepInfo {
    var path: ?String           // 本地路径
    var git: ?String            // Git仓库
    var commitId: ?String       // 提交ID
    var branch: ?String         // 分支
    var tag: ?String            // 标签
    var outputType: ?String     // 输出类型
}
```

### 3. ModuleResolve 模块解析结果
```cangjie
class ModuleResolve {
    var resolves: ArrayList<ResolveItem>  // 解析项
    var binDeps: HashMap                  // 二进制依赖
    var crossBinDeps: HashMap             // 跨平台依赖
}
```

## 配置文件格式

### cjpm.toml 示例
```toml
[package]
cjc-version = "1.0.0"
name = "my-project"
version = "1.0.0"
output-type = "executable"

[dependencies]
std = { path = "../std" }
lib = { git = "https://github.com/example/lib.git", tag = "v1.0" }

[profile.release]
compile-option = "-O2"

[target.x86_64-unknown-linux-gnu]
compile-option = "-march=native"
```

## 平台支持

### 支持的目标平台
- x86_64-w64-mingw32 (Windows)
- x86_64-unknown-linux-gnu (Linux x64)
- aarch64-unknown-linux-gnu (Linux ARM64)
- x86_64-apple-darwin (macOS x64)
- aarch64-apple-darwin (macOS ARM64)

### 平台相关处理
- 动态库命名: Linux (.so), macOS (.dylib), Windows (.dll)
- 路径分隔符处理
- 环境变量处理 (LD_LIBRARY_PATH/DYLD_LIBRARY_PATH)

## 并发与性能

### 并行构建
- 使用 `maxParallelSize` 控制并发数
- 原子计数器 `CUR_PARALLEL_SIZE` 控制当前并发任务
- 支持模块级并行编译

### 增量编译
- 缓存文件: `cjpm-cache.json`
- 检测源文件修改时间
- 只重编译变更的模块

### 缓存机制
- `.cjpm-history`: 构建历史记录
- `.bin-cache`: 二进制缓存
- 依赖缓存: 避免重复下载

## 错误处理

### 错误级别
1. 致命错误: 程序立即退出
2. 构建错误: 中止当前构建
3. 警告: 提示但继续执行

### 错误恢复
- 构建失败自动回滚
- 锁文件保护并发操作
- 事务性文件操作

## 扩展机制

### 构建脚本
- pre-build: 构建前脚本
- post-build: 构建后脚本
- 支持自定义构建步骤

### 插件系统
- 通过 FFI 支持 C/C++ 扩展
- 支持自定义命令注册
- 钩子机制

## 安全考虑

### 依赖安全
- 锁文件固定依赖版本
- 校验依赖完整性
- 隔离构建环境

### 权限控制
- 文件权限检查
- 安全的命令执行
- 环境变量过滤
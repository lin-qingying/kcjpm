# Kotlin 特性重构示例

## 重构前后对比

### 1. 编译上下文构建

**重构前（传统方式）**：
```kotlin
val context = DefaultCompilationContext.builder()
    .projectRoot(projectPath)
    .buildConfig(BuildConfig(CompilationTarget.LINUX_X64, OptimizationLevel.RELEASE))
    .addSourceFile(sourceFile)
    .addDependency(dependency)
    .outputPath(outputPath)
    .build()
```

**重构后（Kotlin DSL）**：
```kotlin
val context = buildCompilationContext {
    projectRoot(projectPath)
    buildConfig {
        target(CompilationTarget.LINUX_X64)
        optimizationLevel(OptimizationLevel.RELEASE)
        debugInfo(true)
        parallel(true, 8)
    }
    sourceFile(sourceFile)
    dependency(dependency)
    outputPath(outputPath)
}
```

### 2. 编译流水线配置

**重构前**：
```kotlin
val pipeline = DefaultCompilationPipeline()
val customStage = object : CompilationStage {
    override val name = "custom"
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
        // 自定义逻辑
        return Result.success(context)
    }
}
val newPipeline = pipeline.addStage(customStage)
```

**重构后（DSL + 协程）**：
```kotlin
val pipeline = buildCompilationPipeline {
    stage("validation") { context ->
        // 验证逻辑
        Result.success(context)
    }
    
    stage("custom") { context ->
        // 自定义逻辑
        Result.success(context)
    }
    
    onError { error ->
        logger.error("编译错误: ${error.message}")
    }
    
    onProgress { stageName, current, total ->
        println("[$current/$total] 执行 $stageName")
    }
}
```

### 3. 命令构建

**重构前**：
```kotlin
val builder = CompilationCommandBuilder()
val command = builder.buildCommand(context)
```

**重构后（上下文接收器）**：
```kotlin
with(context) {
    val command = buildCompilationCommand()
    // 或者使用命令 DSL
    val customCommand = buildCommand {
        command("cjc")
        option("--package", "src")
        flag("-g")
        arguments("--target", buildConfig.target.triple)
    }
}
```

## 主要 Kotlin 特性应用

### 1. 上下文接收器 (Context Receivers)

```kotlin
// 在编译上下文中执行操作
context(CompilationContext)
fun buildCommand(): List<String> = buildList {
    add("cjc")
    // 直接访问上下文属性
    if (isDebugBuild) add("-g")
    if (isParallelEnabled) {
        add("--jobs")
        add(buildConfig.maxParallelSize.toString())
    }
}

// 使用上下文接收器的扩展属性
context(CompilationContext) 
val isDebugBuild: Boolean
    get() = buildConfig.optimizationLevel == OptimizationLevel.DEBUG
```

### 2. DSL 标记注解和类型安全 DSL

```kotlin
@DslMarker
annotation class CompilationDsl

@CompilationDsl
class CompilationContextBuilder {
    fun buildConfig(block: BuildConfigBuilder.() -> Unit) = apply {
        this.buildConfig = BuildConfigBuilder().apply(block).build()
    }
}
```

### 3. 内联函数和契约

```kotlin
@OptIn(ExperimentalContracts::class)
inline fun buildCompilationContext(
    crossinline block: CompilationContextBuilder.() -> Unit
): CompilationContext {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return CompilationContextBuilder().apply(block).build()
}
```

### 4. 协程和 Flow

```kotlin
// 使用 Flow 进行异步处理
stages.asFlow()
    .onEachIndexed { index, stage ->
        progressReporter?.invoke(stage.name, index + 1, totalStages)
    }
    .map { stage -> stage to currentContext }
    .collect { (stage, ctx) ->
        // 处理每个阶段
    }

// 进度流
fun CompilationPipeline.progressFlow(): Flow<CompilationProgress> = flow {
    stages.forEachIndexed { index, stage ->
        emit(CompilationProgress(stage.name, index + 1, stages.size))
    }
}
```

### 5. 作用域函数链式调用

```kotlin
sourceFiles.takeIf { it.isNotEmpty() }
    ?.let { files -> findPackageDirectory(files, projectRoot) }
    ?.also { packageDir ->
        add("--package")
        add(packageDir.toString())
    } ?: run {
        addAll(sourceFiles.map(Path::toString))
    }
```

### 6. 序列操作优化性能

```kotlin
fun discoverPackages(sourceFiles: List<Path>, projectRoot: Path): Sequence<PackageInfo> =
    sourceFiles.asSequence()
        .groupBy { sourceFile -> findPackageRoot(sourceFile, projectRoot) }
        .mapNotNull { (packageRoot, files) ->
            packageRoot?.let { root ->
                val packageName = extractPackageName(files.first())
                PackageInfo(packageName, root, files)
            }
        }
```

### 7. 扩展函数

```kotlin
// 优化级别到命令行参数
private fun OptimizationLevel.toArgs(): List<String> = when (this) {
    OptimizationLevel.DEBUG -> listOf("-O0")
    OptimizationLevel.RELEASE -> listOf("-O2")
    OptimizationLevel.SIZE -> listOf("-Os")
    OptimizationLevel.SPEED -> listOf("-O3")
}

// 依赖到命令行参数
private fun Dependency.toCommandArgs(): List<String> = when (this) {
    is Dependency.PathDependency -> path.toCommandArgs()
    is Dependency.GitDependency -> localPath?.toCommandArgs() ?: emptyList()
    is Dependency.RegistryDependency -> localPath?.let { path ->
        listOf("--library-path", path.toString(), "--library", name)
    } ?: emptyList()
}
```

### 8. 尾递归优化

```kotlin
private tailrec fun findCommonParent(path1: Path, path2: Path): Path {
    val normalized1 = path1.normalize()
    val normalized2 = path2.normalize()
    
    return when {
        normalized2.startsWith(normalized1) -> normalized1
        else -> {
            val parent = normalized1.parent ?: return normalized1.root
            findCommonParent(parent, normalized2)
        }
    }
}
```

### 9. 数据类增强

```kotlin
data class PackageInfo(
    val name: String,
    val packageRoot: Path,
    val sourceFiles: List<Path>
) {
    val isMainPackage: Boolean get() = name == "main"
    val hasMainFunction: Boolean get() = sourceFiles.any { it.containsMainFunction() }
}
```

## 使用示例

### 完整的编译流程

```kotlin
// 使用现代 Kotlin 特性的完整编译流程
suspend fun modernCompile() {
    val context = buildCompilationContext {
        projectRoot(Paths.get("my-project"))
        buildConfig {
            target(CompilationTarget.LINUX_X64)
            optimizationLevel(OptimizationLevel.RELEASE)
            parallel(true, 8)
            verbose(true)
        }
        sourceFiles(
            Paths.get("src/main.cj"),
            Paths.get("src/utils.cj")
        )
        dependencies(
            Dependency.PathDependency("mylib", path = Paths.get("libs/mylib.a")),
            Dependency.GitDependency("gitlib", url = "https://github.com/example/lib.git", 
                reference = Dependency.GitReference.Tag("v1.0.0"))
        )
        outputPath(Paths.get("build/myapp"))
    }
    
    val pipeline = buildCompilationPipeline {
        stage(ValidationStage())
        stage(DependencyResolutionStage())
        stage(PackageCompilationStage())
        stage(LinkingStage())
        
        onProgress { stageName, current, total ->
            val percentage = (current.toDouble() / total * 100).toInt()
            println("[$percentage%] $stageName")
        }
        
        onError { error ->
            when (error.severity) {
                CompilationError.Severity.WARNING -> println("警告: ${error.message}")
                CompilationError.Severity.ERROR -> println("错误: ${error.message}")
                CompilationError.Severity.FATAL -> println("致命错误: ${error.message}")
            }
        }
    }
    
    // 执行编译，使用上下文接收器
    with(context) {
        when (val result = pipeline.executeWith()) {
            is CompilationResult.Success -> {
                println("编译成功: ${result.outputPath}")
                println("生成的文件: ${result.artifacts}")
            }
            is CompilationResult.Failure -> {
                println("编译失败，错误数量: ${result.errors.size}")
                result.errors.forEach { error ->
                    println("${error.severity}: ${error.message}")
                }
            }
        }
    }
}
```

### 自定义编译阶段

```kotlin
class CustomOptimizationStage : CompilationStage {
    override val name = "custom-optimization"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
        // 使用上下文接收器
        with(context) {
            if (isDebugBuild) {
                println("跳过优化（调试模式）")
                return Result.success(context)
            }
            
            // 执行自定义优化
            val optimizedFiles = sourceFiles.map { file ->
                optimizeFile(file)
            }
            
            // 返回更新后的上下文
            return Result.success(context)
        }
    }
    
    override suspend fun onComplete(context: CompilationContext) {
        println("自定义优化完成")
    }
    
    override suspend fun onFailure(context: CompilationContext, error: Throwable) {
        println("自定义优化失败: ${error.message}")
    }
}
```

这些重构充分利用了 Kotlin 的现代特性，让代码更加简洁、类型安全、高性能，并提供了更好的开发体验。
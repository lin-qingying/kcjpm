package org.cangnova.kcjpm.build

import org.cangnova.kcjpm.config.OutputType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * [CompilationContext] 的默认实现。
 *
 * 提供完整的编译上下文验证和编译器参数转换功能。
 * 使用 Builder 模式构建实例以提供流式 API。
 *
 * 示例用法：
 * ```kotlin
 * val context = DefaultCompilationContext.builder()
 *     .projectRoot(Path.of("/path/to/project"))
 *     .buildConfig(BuildConfig(CompilationTarget.LINUX_X64))
 *     .addSourceFile(Path.of("src/main.cj"))
 *     .build()
 *
 * context.validate().getOrThrow()
 * val compilerArgs = context.toCompilerArgs()
 * ```
 */
data class DefaultCompilationContext(
    override val projectRoot: Path,
    override val buildConfig: BuildConfig,
    override val dependencies: List<Dependency>,
    override val sourceFiles: List<Path>,
    override val outputPath: Path,
    override val outputType: OutputType = OutputType.EXECUTABLE,
    override val sourceDir: String = "src",
    override val linkLibraries: List<String> = emptyList(),
    override val includeDirs: List<Path> = emptyList(),
    override val eventBus: CompilationEventBus? = null
) : CompilationContext {
    
    /**
     * 验证编译上下文的完整性和有效性。
     *
     * 检查项包括：
     * - 项目根目录存在且为目录
     * - 至少有一个源文件
     * - 所有源文件存在且为常规文件
     * - 路径依赖存在且为目录
     * - Git 依赖仓库 URL 非空
     * - 远程仓库依赖配置有效
     * - 输出路径可创建（不存在时自动创建）
     * - 并行任务数大于 0
     *
     * @return 验证结果，失败时包含详细错误信息
     */
    override fun validate(): Result<Unit> = runCatching {
        require(projectRoot.exists() && projectRoot.isDirectory()) {
            "Project root does not exist or is not a directory: $projectRoot"
        }
        
        require(sourceFiles.isNotEmpty()) {
            "No source files specified"
        }
        
        sourceFiles.forEach { source ->
            require(source.exists() && source.isRegularFile()) {
                "Source file does not exist or is not a regular file: $source"
            }
        }
        
        dependencies.forEach { dep ->
            when (dep) {
                is Dependency.PathDependency -> {
                    require(dep.path.exists() && dep.path.isDirectory()) {
                        "Path dependency does not exist or is not a directory: ${dep.path}"
                    }
                }
                is Dependency.GitDependency -> {
                    require(dep.url.isNotBlank()) {
                        "Git repository URL cannot be blank for dependency: ${dep.name}"
                    }
                }
                is Dependency.RegistryDependency -> {
                    require(dep.version.isNotBlank()) {
                        "Registry dependency version cannot be blank for: ${dep.name}"
                    }
                    require(dep.registryUrl.isNotBlank()) {
                        "Registry URL cannot be blank for dependency: ${dep.name}"
                    }
                }
            }
        }
        
        if (!outputPath.exists()) {
            Files.createDirectories(outputPath)
        }
        
        require(buildConfig.maxParallelSize > 0) {
            "Maximum parallel size must be greater than 0"
        }
    }
    
    fun toBuilder(): Builder {
        return Builder().apply {
            projectRoot(this@DefaultCompilationContext.projectRoot)
            buildConfig(this@DefaultCompilationContext.buildConfig)
            addDependencies(this@DefaultCompilationContext.dependencies)
            addSourceFiles(this@DefaultCompilationContext.sourceFiles)
            outputPath(this@DefaultCompilationContext.outputPath)
            outputType(this@DefaultCompilationContext.outputType)
            this@DefaultCompilationContext.linkLibraries.forEach { addLinkLibrary(it) }
            this@DefaultCompilationContext.includeDirs.forEach { addIncludeDir(it) }
        }
    }


    companion object {
        /**
         * 创建 Builder 实例用于构建 [DefaultCompilationContext]。
         *
         * @return 新的 Builder 实例
         */
        fun builder() = Builder()
    }
    
    /**
     * 用于构建 [DefaultCompilationContext] 的构建器类。
     *
     * 提供流式 API 以方便配置编译上下文。
     */
    class Builder {
        private var projectRoot: Path? = null
        private var buildConfig: BuildConfig = BuildConfig( )
        private val dependencies = mutableListOf<Dependency>()
        private val sourceFiles = mutableListOf<Path>()
        private var outputPath: Path? = null
        private var outputType: OutputType = OutputType.EXECUTABLE
        private val linkLibraries = mutableListOf<String>()
        private val includeDirs = mutableListOf<Path>()
        private var eventBus: CompilationEventBus? = null
        
        /**
         * 设置项目根目录。
         *
         * @param path 项目根目录路径
         * @return 当前 Builder 实例
         */
        fun projectRoot(path: Path) = apply { this.projectRoot = path }
        
        /**
         * 设置构建配置。
         *
         * @param config 构建配置
         * @return 当前 Builder 实例
         */
        fun buildConfig(config: BuildConfig) = apply { this.buildConfig = config }
        
        /**
         * 添加单个依赖。
         *
         * @param dependency 依赖项
         * @return 当前 Builder 实例
         */
        fun addDependency(dependency: Dependency) = apply { dependencies.add(dependency) }
        
        /**
         * 批量添加依赖。
         *
         * @param deps 依赖项集合
         * @return 当前 Builder 实例
         */
        fun addDependencies(deps: Collection<Dependency>) = apply { dependencies.addAll(deps) }
        
        /**
         * 添加单个源文件。
         *
         * @param file 源文件路径
         * @return 当前 Builder 实例
         */
        fun addSourceFile(file: Path) = apply { sourceFiles.add(file) }
        
        /**
         * 批量添加源文件。
         *
         * @param files 源文件路径集合
         * @return 当前 Builder 实例
         */
        fun addSourceFiles(files: Collection<Path>) = apply { sourceFiles.addAll(files) }
        
        /**
         * 设置输出路径。
         *
         * @param path 输出路径
         * @return 当前 Builder 实例
         */
        fun outputPath(path: Path) = apply { this.outputPath = path }
        
        fun outputType(type: OutputType) = apply { this.outputType = type }
        
        fun addLinkLibrary(library: String) = apply { linkLibraries.add(library) }
        
        fun addIncludeDir(dir: Path) = apply { includeDirs.add(dir) }
        
        fun eventBus(bus: CompilationEventBus?) = apply { this.eventBus = bus }
        
        /**
         * 构建 [DefaultCompilationContext] 实例。
         *
         * @return 编译上下文实例
         * @throws IllegalStateException 如果项目根目录未设置
         */
        fun build(): DefaultCompilationContext {
            val root = checkNotNull(projectRoot) { "Project root must be specified" }
            val output = outputPath ?: root.resolve("target")
            
            return DefaultCompilationContext(
                projectRoot = root,
                buildConfig = buildConfig,
                dependencies = dependencies.toList(),
                sourceFiles = sourceFiles.toList(),
                outputPath = output,
                outputType = outputType,
                linkLibraries = linkLibraries.toList(),
                includeDirs = includeDirs.toList(),
                eventBus = eventBus
            )
        }
    }
}
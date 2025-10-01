package org.cangnova.kcjpm.build

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
    override val outputPath: Path
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
    
    /**
     * 将编译上下文转换为仓颉编译器命令行参数。
     *
     * 参数生成规则：
     * - 目标平台：`--target <triple>`
     * - 优化级别：`-O0`（DEBUG）、`-O2`（RELEASE）、`-Os`（SIZE）、`-O3`（SPEED）
     * - 调试信息：`-g`
     * - 详细输出：`-v`
     * - 输出路径：`-o <path>`
     * - 路径依赖：`--path-dependency <name>=<path>`
     * - Git 依赖：`--git-dependency <name>=<repo>#<ref>`
     * - 远程仓库依赖：`--registry-dependency <name>=<version>@<registry>`
     * - 源文件：追加在参数末尾
     *
     * @return 完整的编译器参数列表
     */
    override fun toCompilerArgs(): List<String> = buildList {
        // 目标平台
        add("--target")
        add(buildConfig.target.triple)
        
        // 优化级别
        when (buildConfig.optimizationLevel) {
            OptimizationLevel.DEBUG -> add("-O0")
            OptimizationLevel.RELEASE -> add("-O2")
            OptimizationLevel.SIZE -> add("-Os")
            OptimizationLevel.SPEED -> add("-O3")
        }
        
        // 调试信息
        if (buildConfig.debugInfo) {
            add("-g")
        }
        
        // 详细输出
        if (buildConfig.verbose) {
            add("-v")
        }
        
        // 输出路径
        add("-o")
        add(outputPath.toString())
        
        // 依赖项
        dependencies.forEach { dep ->
            when (dep) {
                is Dependency.PathDependency -> {
                    add("--path-dependency")
                    add("${dep.name}=${dep.path}")
                }
                is Dependency.GitDependency -> {
                    add("--git-dependency")
                    val ref = when (val gitRef = dep.reference) {
                        is Dependency.GitReference.Tag -> "tag=${gitRef.name}"
                        is Dependency.GitReference.Branch -> "branch=${gitRef.name}"
                        is Dependency.GitReference.Commit -> "commit=${gitRef.hash}"
                    }
                    add("${dep.name}=${dep.url}#$ref")
                }
                is Dependency.RegistryDependency -> {
                    add("--registry-dependency")
                    add("${dep.name}=${dep.version}@${dep.registryUrl}")
                }
            }
        }
        
        // 源文件
        addAll(sourceFiles.map { it.toString() })
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
        private var buildConfig: BuildConfig = BuildConfig(CompilationTarget.current())
        private val dependencies = mutableListOf<Dependency>()
        private val sourceFiles = mutableListOf<Path>()
        private var outputPath: Path? = null
        
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
                outputPath = output
            )
        }
    }
}
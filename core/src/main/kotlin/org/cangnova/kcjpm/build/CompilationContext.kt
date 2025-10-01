package org.cangnova.kcjpm.build

import java.nio.file.Path

/**
 * 编译上下文接口，封装编译所需的所有配置信息和依赖关系。
 *
 * 该接口提供了构建系统核心的数据抽象，包括项目结构、构建配置、依赖管理和源文件信息。
 * 实现类需要提供验证逻辑和编译器参数转换功能。
 *
 */
interface CompilationContext {
    /**
     * 项目根目录路径
     */
    val projectRoot: Path
    
    /**
     * 构建配置，包含编译目标、优化级别等设置
     */
    val buildConfig: BuildConfig
    
    /**
     * 项目依赖列表，支持路径依赖和 Git 依赖
     */
    val dependencies: List<Dependency>
    
    /**
     * 待编译的源文件列表
     */
    val sourceFiles: List<Path>
    
    /**
     * 编译输出路径
     */
    val outputPath: Path
    
    /**
     * 验证编译上下文的有效性。
     *
     * 检查项目路径、源文件、依赖配置等是否符合要求。
     *
     * @return 验证结果，失败时包含详细错误信息
     */
    fun validate(): Result<Unit>
    
    /**
     * 将编译上下文转换为编译器命令行参数。
     *
     * @return 编译器参数列表
     */
    fun toCompilerArgs(): List<String>
}

/**
 * 构建配置数据类，包含编译过程的各项设置。
 *
 * @property target 目标平台架构
 * @property optimizationLevel 优化级别，默认为 RELEASE
 * @property debugInfo 是否生成调试信息，默认为 false
 * @property parallel 是否启用并行编译，默认为 true
 * @property maxParallelSize 最大并行任务数，默认为 CPU 核心数
 * @property incremental 是否启用增量编译，默认为 true
 * @property verbose 是否输出详细日志，默认为 false
 */
data class BuildConfig(
    val target: CompilationTarget,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.RELEASE,
    val debugInfo: Boolean = false,
    val parallel: Boolean = true,
    val maxParallelSize: Int = Runtime.getRuntime().availableProcessors(),
    val incremental: Boolean = true,
    val verbose: Boolean = false
)

/**
 * 编译目标平台枚举，支持跨平台编译。
 *
 * @property triple 目标平台的三元组标识符（遵循 LLVM 规范）
 */
enum class CompilationTarget(val triple: String) {
    /** Windows x86-64 平台 */
    WINDOWS_X64("x86_64-w64-mingw32"),
    
    /** Linux x86-64 平台 */
    LINUX_X64("x86_64-unknown-linux-gnu"),
    
    /** Linux ARM64 平台 */
    LINUX_ARM64("aarch64-unknown-linux-gnu"),
    
    /** macOS x86-64 平台（Intel） */
    MACOS_X64("x86_64-apple-darwin"),
    
    /** macOS ARM64 平台（Apple Silicon） */
    MACOS_ARM64("aarch64-apple-darwin");
    
    companion object {
        /**
         * 根据当前运行环境自动检测目标平台。
         *
         * @return 当前系统对应的编译目标
         * @throws UnsupportedOperationException 当前平台不受支持
         */
        fun current(): CompilationTarget {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            
            return when {
                os.contains("win") -> WINDOWS_X64
                os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm")) MACOS_ARM64 else MACOS_X64
                os.contains("nux") -> if (arch.contains("aarch64") || arch.contains("arm")) LINUX_ARM64 else LINUX_X64
                else -> throw UnsupportedOperationException("Unsupported platform: $os on $arch")
            }
        }
    }
}

/**
 * 编译优化级别枚举。
 */
enum class OptimizationLevel {
    /** 调试模式，不优化（-O0） */
    DEBUG,
    
    /** 发布模式，平衡优化（-O2） */
    RELEASE,
    
    /** 优化二进制体积（-Os） */
    SIZE,
    
    /** 优化执行速度（-O3） */
    SPEED
}

/**
 * 依赖抽象接口，支持多种依赖类型。
 */
sealed interface Dependency {
    /**
     * 依赖名称
     */
    val name: String
    
    /**
     * 依赖版本，可选
     */
    val version: String?
    
    /**
     * 路径依赖，指向本地文件系统中的依赖项。
     *
     * 适用于本地开发和工作空间场景。
     *
     * @property path 依赖项的文件系统路径
     */
    data class PathDependency(
        override val name: String,
        override val version: String? = null,
        val path: Path
    ) : Dependency
    
    /**
     * Git 仓库依赖，从远程 Git 仓库获取依赖。
     *
     * @property url Git 仓库 URL
     * @property reference Git 引用（标签、分支或提交）
     * @property localPath 本地克隆路径（可选，在拉取后设置）
     */
    data class GitDependency(
        override val name: String,
        override val version: String? = null,
        val url: String,
        val reference: GitReference,
        val localPath: Path? = null
    ) : Dependency
    
    /**
     * 远程仓库依赖，从包仓库获取依赖（类似 Maven）。
     *
     * @property registryUrl 仓库 URL
     * @property localPath 本地缓存路径（可选，在下载后设置）
     */
    data class RegistryDependency(
        override val name: String,
        override val version: String,
        val registryUrl: String,
        val localPath: Path? = null
    ) : Dependency
    
    /**
     * Git 引用类型，支持标签、分支和提交哈希。
     */
    sealed interface GitReference {
        /** Git 标签引用 */
        data class Tag(val name: String) : GitReference
        
        /** Git 分支引用 */
        data class Branch(val name: String) : GitReference
        
        /** Git 提交哈希引用 */
        data class Commit(val hash: String) : GitReference
    }
}
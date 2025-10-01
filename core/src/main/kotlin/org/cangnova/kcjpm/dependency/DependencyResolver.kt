package org.cangnova.kcjpm.dependency

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import java.nio.file.Path

/**
 * 依赖解析器接口，负责将依赖配置转换为具体的依赖对象。
 *
 * 依赖解析是依赖管理的核心环节，它根据配置文件中的依赖声明，
 * 确定依赖类型并调用相应的拉取器获取依赖。
 */
interface DependencyResolver {
    /**
     * 批量解析依赖配置。
     *
     * 遍历所有依赖配置，将每个依赖转换为具体的 [Dependency] 对象。
     * 如果任何一个依赖解析失败，整个操作失败。
     *
     * @param dependencies 依赖配置映射表，键为依赖名称，值为依赖配置
     * @param projectRoot 项目根目录，用于解析相对路径
     * @param registry 远程仓库配置，可选
     * @return 包含所有已解析依赖的 Result，失败时包含错误信息
     */
    fun resolve(
        dependencies: Map<String, DependencyConfig>,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<List<Dependency>>
    
    /**
     * 解析单个依赖配置。
     *
     * 根据依赖配置自动检测依赖类型（路径、Git、远程仓库），
     * 并调用相应的拉取器获取依赖。
     *
     * @param name 依赖名称
     * @param config 依赖配置
     * @param projectRoot 项目根目录
     * @param registry 远程仓库配置
     * @return 包含已解析依赖对象的 Result，失败时包含错误信息
     */
    fun resolveSingle(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency>
}

/**
 * [DependencyResolver] 的默认实现。
 *
 * 使用策略模式，根据依赖类型选择合适的 [DependencyFetcher] 进行处理。
 * 支持三种依赖类型：本地路径依赖、Git 仓库依赖、远程注册表依赖。
 *
 * 示例用法：
 * ```kotlin
 * val resolver = DefaultDependencyResolver(
 *     listOf(
 *         PathDependencyFetcher(),
 *         GitDependencyFetcher(cacheDir),
 *         RegistryDependencyFetcher(cacheDir)
 *     )
 * )
 *
 * val dependencies = mapOf(
 *     "local-lib" to DependencyConfig(path = "../local-lib"),
 *     "std-http" to DependencyConfig(version = "1.2.0")
 * )
 *
 * val resolved = resolver.resolve(dependencies, projectRoot, registryConfig)
 * ```
 *
 * @property fetchers 依赖拉取器列表，按优先级排序
 */
class DefaultDependencyResolver(
    private val fetchers: List<DependencyFetcher>
) : DependencyResolver {
    
    /**
     * 批量解析依赖配置。
     *
     * 将每个依赖配置映射为 [Dependency] 对象。如果任何依赖解析失败，
     * 整个操作失败并返回第一个遇到的错误。
     *
     * @param dependencies 依赖配置映射表
     * @param projectRoot 项目根目录
     * @param registry 远程仓库配置
     * @return 包含所有已解析依赖的 Result
     */
    override fun resolve(
        dependencies: Map<String, DependencyConfig>,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<List<Dependency>> = runCatching {
        dependencies.map { (name, config) ->
            resolveSingle(name, config, projectRoot, registry).getOrThrow()
        }
    }
    
    /**
     * 解析单个依赖配置。
     *
     * 执行流程：
     * 1. 检测依赖类型（路径、Git、远程仓库）
     * 2. 查找支持该类型的拉取器
     * 3. 调用拉取器获取依赖
     *
     * @param name 依赖名称
     * @param config 依赖配置
     * @param projectRoot 项目根目录
     * @param registry 远程仓库配置
     * @return 包含已解析依赖对象的 Result
     * @throws IllegalArgumentException 如果依赖类型无效或未找到对应拉取器
     */
    override fun resolveSingle(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency> = runCatching {
        val type = detectDependencyType(config)
        val fetcher = fetchers.find { it.canHandle(type) }
            ?: throw IllegalArgumentException("No fetcher found for dependency type: $type")
        
        fetcher.fetch(name, config, projectRoot, registry).getOrThrow()
    }
    
    /**
     * 根据依赖配置检测依赖类型。
     *
     * 检测规则（按优先级）：
     * 1. 如果配置中有 `path` 字段，则为路径依赖
     * 2. 如果配置中有 `git` 字段，则为 Git 依赖
     * 3. 如果配置中有 `version` 字段，则为远程仓库依赖
     * 4. 其他情况视为无效配置
     *
     * @param config 依赖配置
     * @return 检测到的依赖类型
     * @throws IllegalArgumentException 如果依赖配置无效（三个关键字段都为空）
     */
    private fun detectDependencyType(config: DependencyConfig): DependencyType {
        return when {
            config.path != null -> DependencyType.PATH
            config.git != null -> DependencyType.GIT
            config.version != null -> DependencyType.REGISTRY
            else -> throw IllegalArgumentException("Invalid dependency configuration")
        }
    }
}

/**
 * 依赖类型枚举。
 *
 * 定义了系统支持的三种依赖获取方式。
 */
enum class DependencyType {
    /** 从远程注册表（如 Maven 仓库）获取依赖 */
    REGISTRY,
    
    /** 从本地文件系统路径获取依赖 */
    PATH,
    
    /** 从 Git 仓库获取依赖 */
    GIT
}
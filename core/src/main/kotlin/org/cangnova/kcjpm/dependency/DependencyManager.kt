package org.cangnova.kcjpm.dependency

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.lock.*
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * 依赖管理器接口，提供依赖解析、安装和缓存管理的高级功能。
 *
 * 依赖管理器是整个依赖系统的门面，封装了：
 * - 直接依赖解析
 * - 传递依赖解析
 * - 版本冲突检测
 * - 缓存管理
 */
interface DependencyManager {
    /**
     * 解析项目的直接依赖。
     *
     * 只解析配置文件中明确声明的依赖，不包括传递依赖。
     *
     * @param config 项目配置
     * @param projectRoot 项目根目录
     * @return 包含已解析直接依赖列表的 Result
     */
    fun resolveDependencies(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>>
    
    /**
     * 安装项目的所有依赖（包括传递依赖）。
     *
     * 执行完整的依赖解析流程：
     * 1. 解析直接依赖
     * 2. 递归解析传递依赖
     * 3. 去重（按依赖名称）
     * 4. 验证版本冲突
     * 5. 返回完整的依赖列表
     *
     * @param config 项目配置
     * @param projectRoot 项目根目录
     * @return 包含所有依赖的 Result
     * @throws IllegalStateException 如果发现版本冲突
     */
    fun installDependencies(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>>
    
    /**
     * 获取依赖缓存目录。
     *
     * @return 缓存目录路径
     */
    fun getCacheDir(): Path
    
    /**
     * 清除所有依赖缓存。
     *
     * 删除缓存目录及其所有内容。下次安装依赖时将重新下载。
     *
     * @return 包含操作结果的 Result
     */
    fun clearCache(): Result<Unit>
}

/**
 * [DependencyManager] 的默认实现。
 *
 * 提供完整的依赖管理功能，包括传递依赖解析、版本冲突检测等。
 * 所有依赖（Git 和远程仓库）缓存在统一的缓存目录中。
 *
 * 示例用法：
 * ```kotlin
 * val manager = DefaultDependencyManager(
 *     cacheDir = Path.of(System.getProperty("user.home"), ".kcjpm", "cache")
 * )
 *
 * val config = ConfigLoader.loadFromProjectRoot(projectRoot).getOrThrow()
 * val dependencies = manager.installDependencies(config, projectRoot).getOrThrow()
 * ```
 *
 * @property cacheDir 依赖缓存根目录
 * @property resolver 依赖解析器
 */
class DefaultDependencyManager(
    private val cacheDir: Path,
    private val resolver: DependencyResolver = createDefaultResolver(cacheDir)
) : DependencyManager {
    
    /**
     * 解析项目的直接依赖。
     *
     * @param config 项目配置
     * @param projectRoot 项目根目录
     * @return 包含直接依赖列表的 Result
     */
    override fun resolveDependencies(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>> {
        return resolver.resolve(
            config.dependencies,
            projectRoot,
            config.registry
        )
    }
    
    /**
     * 安装所有依赖（包括传递依赖）。
     *
     * 执行流程：
     * 1. 解析直接依赖
     * 2. 递归解析传递依赖
     * 3. 合并并去重（同名依赖只保留一个）
     * 4. 验证版本冲突
     * 5. 返回完整依赖列表
     *
     * @param config 项目配置
     * @param projectRoot 项目根目录
     * @return 包含所有依赖的 Result
     * @throws IllegalStateException 如果同一依赖存在多个不兼容版本
     */
    override fun installDependencies(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>> = runCatching {
        val directDeps = resolveDependencies(config, projectRoot).getOrThrow()
        
        val transitiveDeps = resolveTransitiveDependencies(directDeps).getOrThrow()
        
        val allDeps = (directDeps + transitiveDeps).distinctBy { it.name }
        
        validateDependencies(allDeps).getOrThrow()
        
        allDeps
    }
    
    override fun getCacheDir(): Path = cacheDir
    
    /**
     * 清除依赖缓存。
     *
     * 递归删除整个缓存目录及其内容。
     *
     * @return 包含操作结果的 Result
     */
    override fun clearCache(): Result<Unit> = runCatching {
        if (cacheDir.exists()) {
            cacheDir.toFile().deleteRecursively()
        }
    }
    
    /**
     * 递归解析传递依赖。
     *
     * 对每个直接依赖，读取其配置文件并解析其依赖。
     * 使用深度优先遍历和访问标记避免重复处理和循环依赖。
     *
     * @param dependencies 直接依赖列表
     * @return 包含所有传递依赖的 Result
     */
    private fun resolveTransitiveDependencies(
        dependencies: List<Dependency>
    ): Result<List<Dependency>> = runCatching {
        val transitive = mutableListOf<Dependency>()
        val visited = mutableSetOf<String>()
        
        for (dep in dependencies) {
            resolveTransitiveRecursive(dep, transitive, visited).getOrThrow()
        }
        
        transitive
    }
    
    /**
     * 递归解析单个依赖的传递依赖。
     *
     * 执行流程：
     * 1. 检查是否已访问（避免重复和循环）
     * 2. 标记为已访问
     * 3. 获取依赖的本地路径
     * 4. 查找 cjpm.toml 配置文件
     * 5. 解析该依赖的依赖
     * 6. 递归处理每个子依赖
     *
     * @param dependency 当前依赖
     * @param accumulator 累积的传递依赖列表
     * @param visited 已访问的依赖名称集合
     * @return 包含操作结果的 Result
     */
    private fun resolveTransitiveRecursive(
        dependency: Dependency,
        accumulator: MutableList<Dependency>,
        visited: MutableSet<String>
    ): Result<Unit> = runCatching {
        if (dependency.name in visited) {
            return@runCatching
        }
        visited.add(dependency.name)
        
        val depPath = when (dependency) {
            is Dependency.PathDependency -> dependency.path
            is Dependency.GitDependency -> dependency.localPath ?: return@runCatching
            is Dependency.RegistryDependency -> dependency.localPath ?: return@runCatching
        }
        
        val configFile = depPath.resolve("cjpm.toml")
        if (!configFile.exists()) {
            return@runCatching
        }
        
        val depConfig = ConfigLoader.loadConfig(configFile).getOrNull() ?: return@runCatching
        
        val subDeps = resolver.resolve(
            depConfig.dependencies,
            depPath,
            depConfig.registry
        ).getOrThrow()
        
        for (subDep in subDeps) {
            accumulator.add(subDep)
            resolveTransitiveRecursive(subDep, accumulator, visited).getOrThrow()
        }
    }
    
    /**
     * 验证依赖列表，检测版本冲突。
     *
     * 规则：同一依赖名称只能有一个版本。
     * 如果检测到多个版本，抛出异常并列出所有冲突的依赖及其版本。
     *
     * @param dependencies 待验证的依赖列表
     * @return 包含操作结果的 Result
     * @throws IllegalStateException 如果存在版本冲突
     */
    private fun validateDependencies(dependencies: List<Dependency>): Result<Unit> = runCatching {
        val nameVersionMap = mutableMapOf<String, MutableSet<String>>()
        
        for (dep in dependencies) {
            val version = dep.version ?: continue
            nameVersionMap.getOrPut(dep.name) { mutableSetOf() }.add(version)
        }
        
        val conflicts = nameVersionMap.filter { it.value.size > 1 }
        if (conflicts.isNotEmpty()) {
            val message = conflicts.entries.joinToString("\n") { (name, versions) ->
                "  $name: ${versions.joinToString(", ")}"
            }
            throw IllegalStateException("Dependency version conflicts detected:\n$message")
        }
    }
    
    companion object {
        /**
         * 创建默认的依赖解析器。
         *
         * 配置三种拉取器：路径、Git、远程仓库。
         *
         * @param cacheDir 缓存目录
         * @return 配置好的依赖解析器
         */
        private fun createDefaultResolver(cacheDir: Path): DependencyResolver {
            val fetchers = listOf(
                PathDependencyFetcher(),
                GitDependencyFetcher(cacheDir),
                RegistryDependencyFetcher(cacheDir)
            )
            return DefaultDependencyResolver(fetchers)
        }
    }
}

/**
 * 依赖图数据结构，用于依赖关系分析。
 *
 * 提供拓扑排序功能，用于确定依赖的正确构建顺序。
 * 可检测循环依赖。
 *
 * @property dependencies 依赖节点映射表，键为依赖名称，值为依赖节点
 */
data class DependencyGraph(
    val dependencies: Map<String, DependencyNode>
) {
    /**
     * 对依赖图执行拓扑排序。
     *
     * 使用深度优先搜索算法，生成依赖的有效构建顺序。
     * 构建顺序确保依赖总是在其使用者之前构建。
     *
     * @return 包含排序后依赖名称列表的 Result
     * @throws IllegalStateException 如果检测到循环依赖
     */
    fun topologicalSort(): Result<List<String>> = runCatching {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        
        for (name in dependencies.keys) {
            if (name !in visited) {
                topologicalSortUtil(name, visited, recursionStack, result).getOrThrow()
            }
        }
        
        result.reversed()
    }
    
    /**
     * 拓扑排序的递归辅助函数。
     *
     * 使用递归栈检测循环依赖：
     * - 如果访问到递归栈中的节点，说明存在循环
     * - 访问完成后从递归栈移除
     *
     * @param name 当前节点名称
     * @param visited 已访问节点集合
     * @param recursionStack 递归调用栈
     * @param result 排序结果列表
     * @return 包含操作结果的 Result
     * @throws IllegalStateException 如果检测到循环依赖
     */
    private fun topologicalSortUtil(
        name: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        result: MutableList<String>
    ): Result<Unit> = runCatching {
        visited.add(name)
        recursionStack.add(name)
        
        val node = dependencies[name]
        if (node != null) {
            for (depName in node.dependencies) {
                if (depName !in visited) {
                    topologicalSortUtil(depName, visited, recursionStack, result).getOrThrow()
                } else if (depName in recursionStack) {
                    throw IllegalStateException("Circular dependency detected: $name -> $depName")
                }
            }
        }
        
        recursionStack.remove(name)
        result.add(name)
    }
}

/**
 * 依赖图中的节点，表示一个依赖及其直接依赖。
 *
 * @property name 依赖名称
 * @property version 依赖版本，可选
 * @property dependencies 直接依赖的名称列表
 */
data class DependencyNode(
    val name: String,
    val version: String?,
    val dependencies: List<String>
)
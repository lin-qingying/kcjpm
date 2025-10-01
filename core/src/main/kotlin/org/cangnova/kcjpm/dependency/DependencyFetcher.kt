package org.cangnova.kcjpm.dependency

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import java.nio.file.Path

/**
 * 依赖拉取器接口，负责从特定来源获取依赖。
 *
 * 每个拉取器实现处理一种依赖类型（路径、Git、远程仓库）。
 * 使用策略模式，允许灵活扩展新的依赖来源。
 */
interface DependencyFetcher {
    /**
     * 检查此拉取器是否能处理指定类型的依赖。
     *
     * @param type 依赖类型
     * @return 如果能处理返回 true，否则返回 false
     */
    fun canHandle(type: DependencyType): Boolean
    
    /**
     * 从相应来源拉取依赖。
     *
     * 根据依赖类型的不同，可能执行以下操作：
     * - 路径依赖：验证本地路径存在性
     * - Git 依赖：克隆或更新 Git 仓库
     * - 远程仓库依赖：下载并缓存依赖包
     *
     * @param name 依赖名称
     * @param config 依赖配置
     * @param projectRoot 项目根目录
     * @param registry 远程仓库配置
     * @return 包含拉取结果的 Result，成功时包含 [Dependency] 对象
     */
    fun fetch(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency>
}

/**
 * 本地路径依赖拉取器。
 *
 * 处理从本地文件系统路径获取的依赖，通常用于工作空间开发场景。
 * 路径可以是相对路径（相对于项目根目录）或绝对路径。
 *
 * 示例配置：
 * ```toml
 * [dependencies]
 * local-lib = { path = "../local-lib" }
 * ```
 */
class PathDependencyFetcher : DependencyFetcher {
    override fun canHandle(type: DependencyType): Boolean = type == DependencyType.PATH
    
    /**
     * 获取本地路径依赖。
     *
     * 执行步骤：
     * 1. 从配置中读取路径
     * 2. 解析为绝对路径（相对于项目根目录）
     * 3. 验证路径存在性
     * 4. 创建 PathDependency 对象
     *
     * @param name 依赖名称
     * @param config 依赖配置，必须包含 path 字段
     * @param projectRoot 项目根目录，用于解析相对路径
     * @param registry 此拉取器不使用此参数
     * @return 包含 PathDependency 的 Result
     * @throws IllegalArgumentException 如果 path 字段缺失或路径不存在
     */
    override fun fetch(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency> = runCatching {
        val path = config.path ?: throw IllegalArgumentException("Path is required for path dependency")
        val resolvedPath = projectRoot.resolve(path).normalize()
        
        if (!resolvedPath.toFile().exists()) {
            throw IllegalArgumentException("Dependency path does not exist: $resolvedPath")
        }
        
        Dependency.PathDependency(
            name = name,
            version = config.version,
            path = resolvedPath
        )
    }
}

/**
 * Git 仓库依赖拉取器。
 *
 * 从 Git 仓库克隆或更新依赖。支持通过标签、分支或提交哈希指定版本。
 * 所有 Git 依赖缓存在统一的缓存目录中，避免重复克隆。
 *
 * 示例配置：
 * ```toml
 * [dependencies]
 * http-client = { git = "https://github.com/user/repo", tag = "v1.0.0" }
 * dev-lib = { git = "https://github.com/user/dev", branch = "develop" }
 * ```
 *
 * @property cacheDir Git 依赖的缓存根目录
 */
class GitDependencyFetcher(
    private val cacheDir: Path
) : DependencyFetcher {
    override fun canHandle(type: DependencyType): Boolean = type == DependencyType.GIT
    
    /**
     * 从 Git 仓库获取依赖。
     *
     * 执行流程：
     * 1. 读取 Git 仓库 URL 和引用（标签/分支/提交）
     * 2. 检查本地缓存是否存在
     * 3. 如果不存在，执行浅克隆
     * 4. 如果存在，执行增量更新
     * 5. 切换到指定的引用
     * 6. 创建 GitDependency 对象
     *
     * @param name 依赖名称
     * @param config 依赖配置，必须包含 git 字段
     * @param projectRoot 项目根目录（此拉取器不使用）
     * @param registry 远程仓库配置（此拉取器不使用）
     * @return 包含 GitDependency 的 Result
     * @throws IllegalArgumentException 如果 git 字段缺失
     * @throws RuntimeException 如果 Git 操作失败
     */
    override fun fetch(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency> = runCatching {
        val gitUrl = config.git ?: throw IllegalArgumentException("Git URL is required")
        
        val reference = when {
            config.tag != null -> Dependency.GitReference.Tag(config.tag)
            config.branch != null -> Dependency.GitReference.Branch(config.branch)
            config.commit != null -> Dependency.GitReference.Commit(config.commit)
            else -> Dependency.GitReference.Branch("main")
        }
        
        val localPath = cloneOrUpdate(name, gitUrl, reference)
        
        Dependency.GitDependency(
            name = name,
            version = config.version,
            url = gitUrl,
            reference = reference,
            localPath = localPath
        )
    }
    
    /**
     * 克隆或更新 Git 仓库。
     *
     * 如果本地缓存不存在，执行新克隆；如果存在，执行更新操作。
     *
     * @param name 依赖名称，用于生成缓存目录名
     * @param url Git 仓库 URL
     * @param reference Git 引用
     * @return 本地缓存路径
     */
    private fun cloneOrUpdate(name: String, url: String, reference: Dependency.GitReference): Path {
        val targetDir = cacheDir.resolve("git").resolve(sanitizeName(name))
        
        if (!targetDir.toFile().exists()) {
            cloneRepository(url, targetDir, reference)
        } else {
            updateRepository(targetDir, reference)
        }
        
        return targetDir
    }
    
    /**
     * 克隆 Git 仓库。
     *
     * 使用浅克隆（--depth 1）优化下载速度和磁盘占用。
     * 对于标签和分支，直接克隆指定引用；对于提交哈希，先克隆后切换。
     *
     * @param url Git 仓库 URL
     * @param targetDir 目标目录
     * @param reference Git 引用
     * @throws RuntimeException 如果 Git 命令执行失败
     */
    private fun cloneRepository(url: String, targetDir: Path, reference: Dependency.GitReference) {
        val command = buildList {
            add("git")
            add("clone")
            when (reference) {
                is Dependency.GitReference.Branch -> {
                    add("--branch")
                    add(reference.name)
                }
                is Dependency.GitReference.Tag -> {
                    add("--branch")
                    add(reference.name)
                }
                else -> {}
            }
            add("--depth")
            add("1")
            add(url)
            add(targetDir.toString())
        }
        
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to clone repository: $output")
        }
        
        if (reference is Dependency.GitReference.Commit) {
            checkoutCommit(targetDir, reference.hash)
        }
    }
    
    /**
     * 更新已存在的 Git 仓库。
     *
     * 执行 fetch 和 checkout 操作以更新到最新的引用状态。
     *
     * @param targetDir 仓库目录
     * @param reference Git 引用
     * @throws RuntimeException 如果 Git 命令执行失败
     */
    private fun updateRepository(targetDir: Path, reference: Dependency.GitReference) {
        val fetchProcess = ProcessBuilder("git", "fetch", "--depth", "1")
            .directory(targetDir.toFile())
            .redirectErrorStream(true)
            .start()
        
        fetchProcess.waitFor()
        
        val checkoutRef = when (reference) {
            is Dependency.GitReference.Branch -> "origin/${reference.name}"
            is Dependency.GitReference.Tag -> reference.name
            is Dependency.GitReference.Commit -> reference.hash
        }
        
        val checkoutProcess = ProcessBuilder("git", "checkout", checkoutRef)
            .directory(targetDir.toFile())
            .redirectErrorStream(true)
            .start()
        
        val exitCode = checkoutProcess.waitFor()
        if (exitCode != 0) {
            val output = checkoutProcess.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to checkout $checkoutRef: $output")
        }
    }
    
    /**
     * 切换到指定的提交哈希。
     *
     * @param targetDir 仓库目录
     * @param commit 提交哈希
     * @throws RuntimeException 如果 Git 命令执行失败
     */
    private fun checkoutCommit(targetDir: Path, commit: String) {
        val process = ProcessBuilder("git", "checkout", commit)
            .directory(targetDir.toFile())
            .redirectErrorStream(true)
            .start()
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to checkout commit $commit: $output")
        }
    }
    
    /**
     * 清理依赖名称，使其适合作为目录名。
     *
     * 将所有非字母数字字符（除了 `-` 和 `_`）替换为下划线。
     *
     * @param name 原始依赖名称
     * @return 清理后的名称
     */
    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}

/**
 * 远程注册表依赖拉取器。
 *
 * 从远程包仓库（如 Maven、npm 风格的仓库）下载依赖。
 * 支持多种仓库配置：默认仓库、私有仓库、自定义仓库。
 * 下载的依赖缓存在本地，避免重复下载。
 *
 * 示例配置：
 * ```toml
 * [dependencies]
 * std-http = "1.2.0"  # 使用默认仓库
 * private-lib = { version = "2.0.0", registry = "private" }
 * ```
 *
 * @property cacheDir 依赖缓存根目录
 * @property httpClient HTTP 客户端，用于下载依赖包
 */
class RegistryDependencyFetcher(
    private val cacheDir: Path,
    private val httpClient: DependencyHttpClient = DefaultDependencyHttpClient()
) : DependencyFetcher {
    override fun canHandle(type: DependencyType): Boolean = type == DependencyType.REGISTRY
    
    /**
     * 从远程注册表获取依赖。
     *
     * 执行流程：
     * 1. 读取版本号和注册表名称
     * 2. 解析注册表 URL
     * 3. 检查本地缓存
     * 4. 如果不存在，从注册表下载并解压
     * 5. 创建 RegistryDependency 对象
     *
     * @param name 依赖名称
     * @param config 依赖配置，必须包含 version 字段
     * @param projectRoot 项目根目录（此拉取器不使用）
     * @param registry 远程仓库配置
     * @return 包含 RegistryDependency 的 Result
     * @throws IllegalArgumentException 如果 version 字段缺失或注册表配置无效
     */
    override fun fetch(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Result<Dependency> = runCatching {
        val version = config.version ?: throw IllegalArgumentException("Version is required for registry dependency")
        val registryUrl = resolveRegistryUrl(config.registry, registry)
        
        val localPath = downloadFromRegistry(name, version, registryUrl)
        
        Dependency.RegistryDependency(
            name = name,
            version = version,
            registryUrl = registryUrl,
            localPath = localPath
        )
    }
    
    /**
     * 解析注册表 URL。
     *
     * 解析规则：
     * - 如果 registryName 为 null，使用默认注册表
     * - 如果 registryName 为 "default"，使用配置中的默认注册表
     * - 如果 registryName 为 "private"，使用配置中的私有注册表
     * - 否则，registryName 本身就是注册表 URL
     *
     * @param registryName 注册表名称或 URL
     * @param registry 注册表配置
     * @return 解析后的注册表 URL
     * @throws IllegalArgumentException 如果私有注册表未配置
     */
    private fun resolveRegistryUrl(registryName: String?, registry: RegistryConfig?): String {
        if (registryName == null) {
            return registry?.default ?: "https://repo.cangjie-lang.cn"
        }
        
        return when (registryName) {
            "default" -> registry?.default ?: "https://repo.cangjie-lang.cn"
            "private" -> registry?.privateUrl 
                ?: throw IllegalArgumentException("Private registry URL not configured")
            else -> registryName
        }
    }
    
    /**
     * 从注册表下载依赖。
     *
     * 下载的依赖保存在 `cache/registry/<name>/<version>` 目录下。
     * 如果缓存已存在，直接返回缓存路径。
     *
     * @param name 依赖名称
     * @param version 依赖版本
     * @param registryUrl 注册表 URL
     * @return 本地缓存路径
     * @throws RuntimeException 如果下载失败
     */
    private fun downloadFromRegistry(name: String, version: String, registryUrl: String): Path {
        val targetDir = cacheDir.resolve("registry").resolve(name).resolve(version)
        
        if (targetDir.toFile().exists()) {
            return targetDir
        }
        
        targetDir.toFile().mkdirs()
        
        val packageUrl = "$registryUrl/packages/$name/$version/download"
        httpClient.download(packageUrl, targetDir).getOrThrow()
        
        return targetDir
    }
}
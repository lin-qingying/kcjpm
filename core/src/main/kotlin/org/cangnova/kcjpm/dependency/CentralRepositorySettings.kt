package org.cangnova.kcjpm.dependency

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.sdk.SdkManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * 中心仓客户端运行配置。
 *
 * 官方 `cjpm` 通过 `cangjie-repo.toml` 管理中心仓地址、认证 token 和本地缓存路径。
 * KCJPM 在 CLI 和编译流水线入口统一读取该配置，避免各命令各自硬编码仓库地址或缓存目录。
 */
data class CentralRepositorySettings(
    val registryUrl: String,
    val token: String?,
    val cacheRoot: Path,
    val configPath: Path?
) {
    val repositoryCacheDir: Path = cacheRoot.resolve("repository").normalize()

    fun toRegistryConfig(): RegistryConfig = RegistryConfig(default = registryUrl)
}

/**
 * 读取官方中心仓配置文件 `cangjie-repo.toml`。
 *
 * 搜索顺序与官方文档一致：
 * 1. 当前模块目录；
 * 2. 用户目录 `.cjpm`；
 * 3. 仓颉 SDK 的 `tools/config` 目录。
 */
object CentralRepositorySettingsLoader {
    const val CONFIG_FILE_NAME: String = "cangjie-repo.toml"

    private val toml = Toml { ignoreUnknownKeys = true }

    fun load(projectRoot: Path): Result<CentralRepositorySettings> = runCatching {
        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val configPath = findConfigPath(normalizedProjectRoot)
        val repoConfig = configPath?.let { path ->
            toml.decodeFromString(CangjieRepoToml.serializer(), path.readText())
        } ?: CangjieRepoToml()

        val cacheRoot = resolveCacheRoot(configPath, repoConfig.repository.cache.path)
        val registryUrl = repoConfig.repository.home.registry
            .takeIf { it.isNotBlank() }
            ?: CentralRepositoryDefaults.DEFAULT_REGISTRY_URL

        CentralRepositorySettings(
            registryUrl = registryUrl,
            token = repoConfig.repository.home.token?.takeIf { it.isNotBlank() },
            cacheRoot = cacheRoot,
            configPath = configPath
        )
    }

    private fun findConfigPath(projectRoot: Path): Path? {
        return sequenceOf(
            projectRoot.resolve(CONFIG_FILE_NAME),
            userConfigPath(),
            sdkConfigPath()
        )
            .filterNotNull()
            .firstOrNull { it.exists() && Files.isRegularFile(it) }
    }

    private fun userConfigPath(): Path? {
        val userHome = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return null
        return Path.of(userHome).resolve(".cjpm").resolve(CONFIG_FILE_NAME)
    }

    private fun sdkConfigPath(): Path? {
        val sdkHome = System.getenv("CANGJIE_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: SdkManager.fromSystemPath().getOrNull()?.sdkHome

        return sdkHome?.resolve("tools")?.resolve("config")?.resolve(CONFIG_FILE_NAME)
    }

    private fun resolveCacheRoot(configPath: Path?, configuredPath: String?): Path {
        val cachePath = configuredPath?.takeIf { it.isNotBlank() }
        if (cachePath == null) {
            return defaultCacheRoot()
        }

        val rawPath = Path.of(cachePath)
        return if (rawPath.isAbsolute) {
            rawPath.normalize()
        } else {
            val baseDir = configPath?.parent ?: defaultCacheRoot()
            baseDir.resolve(rawPath).normalize()
        }
    }

    private fun defaultCacheRoot(): Path {
        val userHome = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Cannot resolve user home for central repository cache")
        return Path.of(userHome).resolve(".cjpm").normalize()
    }
}

/**
 * 依赖管理器工厂。
 *
 * 工厂负责把项目配置和官方中心仓客户端配置合并为一个完整的依赖管理器配置。
 * 项目 `cjpm.toml`/`kcjpm.toml` 中的 `[registry]` 显式配置优先；未配置时使用 `cangjie-repo.toml`。
 */
object DependencyManagerFactory {
    fun create(projectRoot: Path, config: CjpmConfig): Result<DefaultDependencyManager> = runCatching {
        val settings = CentralRepositorySettingsLoader.load(projectRoot).getOrThrow()
        val registryConfig = config.registry ?: settings.toRegistryConfig()

        DefaultDependencyManager(
            cacheDir = settings.repositoryCacheDir,
            defaultRegistryConfig = registryConfig
        )
    }
}

@Serializable
private data class CangjieRepoToml(
    val repository: CangjieRepositoryToml = CangjieRepositoryToml()
)

@Serializable
private data class CangjieRepositoryToml(
    val cache: CangjieRepositoryCacheToml = CangjieRepositoryCacheToml(),
    val home: CangjieRepositoryHomeToml = CangjieRepositoryHomeToml()
)

@Serializable
private data class CangjieRepositoryCacheToml(
    val path: String? = null
)

@Serializable
private data class CangjieRepositoryHomeToml(
    val registry: String = CentralRepositoryDefaults.DEFAULT_REGISTRY_URL,
    val token: String? = null
)

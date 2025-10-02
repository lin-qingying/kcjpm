package org.cangnova.kcjpm.sdk

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

/**
 * SDK 管理器：管理仓颉编译器的路径和验证
 * 
 * 负责定位和验证仓颉编译器（cjc）的安装位置。
 * 优先级顺序：
 * 1. 手动指定的 SDK 路径
 * 2. CANGJIE_HOME 环境变量
 * 3. 系统 PATH 中的 cjc 命令
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * // 使用默认配置（从 CANGJIE_HOME 读取）
 * val sdk = SdkManager.default().getOrThrow()
 * println("编译器路径: ${sdk.compilerPath}")
 * 
 * // 手动指定 SDK 路径
 * val customSdk = SdkManager.fromPath(Path.of("/opt/cangjie"))
 *     .getOrThrow()
 * 
 * // 获取编译器命令
 * val compilerCommand = sdk.getCompilerCommand()
 * ```
 * 
 * @property sdkHome SDK 根目录（CANGJIE_HOME）
 * @property compilerPath 编译器可执行文件路径（cjc）
 * @property version SDK 版本信息（可选）
 */
data class CangjieSDK(
    val sdkHome: Path,
    val compilerPath: Path,
    val version: String? = null
) {
    /**
     * 验证 SDK 是否有效
     * 
     * 检查：
     * - SDK 根目录存在且为目录
     * - 编译器可执行文件存在
     * - 编译器文件具有可执行权限（Unix/Linux）
     * 
     * @return 验证结果，失败时包含详细错误信息
     */
    fun validate(): Result<Unit> = runCatching {
        require(sdkHome.exists() && sdkHome.isDirectory()) {
            "SDK 根目录不存在或不是目录: $sdkHome"
        }
        
        require(compilerPath.exists()) {
            "编译器可执行文件不存在: $compilerPath"
        }
        
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        if (!isWindows) {
            require(compilerPath.isExecutable()) {
                "编译器文件不可执行: $compilerPath"
            }
        }
    }
    
    /**
     * 获取编译器命令字符串
     * 
     * @return 编译器的完整路径字符串
     */
    fun getCompilerCommand(): String = compilerPath.toString()
    
    /**
     * 检测 SDK 版本
     * 
     * 通过执行 `cjc --version` 命令获取版本信息
     * 
     * @return 版本字符串，失败时返回 null
     */
    fun detectVersion(): String? = runCatching {
        val process = ProcessBuilder(compilerPath.toString(), "--version")
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        
        if (process.exitValue() == 0) output else null
    }.getOrNull()
}

/**
 * SDK 管理器：提供 SDK 查找和初始化功能
 */
object SdkManager {
    
    /**
     * 使用默认策略创建 SDK
     * 
     * 查找顺序：
     * 1. CANGJIE_HOME 环境变量
     * 2. 系统 PATH 中的 cjc 命令
     * 
     * @return SDK 实例，失败时返回错误
     */
    fun default(): Result<CangjieSDK> {
        val cangjieHome = System.getenv("CANGJIE_HOME")
        
        return if (cangjieHome != null) {
            fromPath(Path.of(cangjieHome))
        } else {
            fromSystemPath()
        }
    }
    
    /**
     * 从指定路径创建 SDK
     * 
     * @param sdkHome SDK 根目录路径
     * @return SDK 实例，失败时返回错误
     */
    fun fromPath(sdkHome: Path): Result<CangjieSDK> = runCatching {
        val compilerPath = findCompilerInSdk(sdkHome)
        val sdk = CangjieSDK(sdkHome, compilerPath)
        
        sdk.validate().getOrThrow()
        
        val version = sdk.detectVersion()
        sdk.copy(version = version)
    }
    
    /**
     * 从系统 PATH 中查找 SDK
     * 
     * 通过 `which cjc` (Unix) 或 `where cjc` (Windows) 定位编译器
     * 
     * @return SDK 实例，失败时返回错误
     */
    fun fromSystemPath(): Result<CangjieSDK> = runCatching {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val command = if (isWindows) "where" else "which"
        
        val process = ProcessBuilder(command, "cjc")
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        
        if (process.exitValue() != 0 || output.isEmpty()) {
            throw IllegalStateException(
                "未找到 cjc 编译器。请设置 CANGJIE_HOME 环境变量或将 cjc 添加到 PATH"
            )
        }
        
        val compilerPath = Path.of(output.lines().first())
        val sdkHome = compilerPath.parent?.parent 
            ?: throw IllegalStateException("无法确定 SDK 根目录")
        
        val sdk = CangjieSDK(sdkHome, compilerPath)
        val version = sdk.detectVersion()
        sdk.copy(version = version)
    }
    
    /**
     * 在 SDK 根目录中查找编译器可执行文件
     * 
     * 查找位置：
     * - Windows: `{sdkHome}/bin/cjc.exe` 或 `{sdkHome}/cjc.exe`
     * - Unix/Linux: `{sdkHome}/bin/cjc` 或 `{sdkHome}/cjc`
     * 
     * @param sdkHome SDK 根目录
     * @return 编译器可执行文件路径
     * @throws IllegalArgumentException 如果未找到编译器
     */
    private fun findCompilerInSdk(sdkHome: Path): Path {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val compilerName = if (isWindows) "cjc.exe" else "cjc"
        
        val candidatePaths = listOf(
            sdkHome.resolve("bin").resolve(compilerName),
            sdkHome.resolve(compilerName)
        )
        
        return candidatePaths.firstOrNull { it.exists() }
            ?: throw IllegalArgumentException(
                "在 SDK 目录中未找到编译器 ($compilerName): $sdkHome"
            )
    }
}

/**
 * SDK 配置类：用于存储和加载 SDK 配置
 * 
 * 可以从配置文件或代码中设置 SDK 路径，覆盖默认行为
 */
data class SdkConfig(
    val sdkHomePath: String? = null,
    val useSystemPath: Boolean = false
) {
    /**
     * 根据配置创建 SDK
     * 
     * @return SDK 实例
     */
    fun createSdk(): Result<CangjieSDK> {
        return when {
            sdkHomePath != null -> SdkManager.fromPath(Path.of(sdkHomePath))
            useSystemPath -> SdkManager.fromSystemPath()
            else -> SdkManager.default()
        }
    }
}
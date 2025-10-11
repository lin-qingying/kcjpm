package org.cangnova.kcjpm.dependency

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * 依赖 HTTP 客户端接口，负责从远程服务器下载依赖包。
 *
 * 提供统一的下载接口，支持不同的 HTTP 客户端实现。
 */
interface DependencyHttpClient {
    /**
     * 从指定 URL 下载依赖包并解压到目标目录。
     *
     * @param url 下载 URL
     * @param targetDir 目标目录
     * @return 包含操作结果的 Result，失败时包含错误信息
     */
    fun download(url: String, targetDir: Path): Result<Unit>
}

/**
 * 基于 Java 标准库的 HTTP 客户端实现。
 *
 * 使用 [HttpURLConnection] 下载文件，支持自动解压 ZIP 格式的依赖包。
 * 下载过程包括：
 * 1. 建立 HTTP 连接
 * 2. 下载到临时文件
 * 3. 解压到目标目录
 * 4. 清理临时文件
 *
 * 配置：
 * - 连接超时：30 秒
 * - 读取超时：60 秒
 * - 仅支持 ZIP 格式的依赖包
 */
class DefaultDependencyHttpClient : DependencyHttpClient {
    /**
     * 从远程服务器下载并解压依赖包。
     *
     * 执行流程：
     * 1. 建立 HTTP GET 连接
     * 2. 检查响应状态码（期望 200 OK）
     * 3. 将响应流写入临时文件
     * 4. 解压 ZIP 文件到目标目录
     * 5. 删除临时文件
     *
     * @param url 下载 URL，应指向 ZIP 格式的依赖包
     * @param targetDir 解压目标目录
     * @return 包含操作结果的 Result
     * @throws RuntimeException 如果 HTTP 请求失败或解压失败
     */
    override fun download(url: String, targetDir: Path): Result<Unit> = runCatching {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Failed to download from $url: HTTP $responseCode")
            }
            
            val tempFile = Files.createTempFile("kcjpm-download", ".zip")
            try {
                connection.inputStream.use { input ->
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
                }
                
                extractZip(tempFile, targetDir)
            } finally {
                Files.deleteIfExists(tempFile)
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 解压 ZIP 文件到目标目录。
     *
     * 遍历 ZIP 文件中的所有条目，根据条目类型：
     * - 目录：创建目录结构
     * - 文件：提取文件内容
     *
     * 自动创建必要的父目录，覆盖已存在的文件。
     *
     * @param zipFile ZIP 文件路径
     * @param targetDir 解压目标目录
     * @throws Exception 如果读取或写入失败
     */
    private fun extractZip(zipFile: Path, targetDir: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val targetFile = targetDir.resolve(entry.name)
                
                if (entry.isDirectory) {
                    Files.createDirectories(targetFile)
                } else {
                    Files.createDirectories(targetFile.parent)
                    Files.copy(zis, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
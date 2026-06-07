package org.cangnova.kcjpm.dependency

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * 依赖 HTTP 客户端接口，负责从远程服务器下载依赖包。
 *
 * 提供统一的下载和解包接口，支持不同的 HTTP 客户端实现。
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

    /**
     * 读取远程文本内容。
     *
     * 中心仓索引接口返回按行分隔的 JSON 文本，因此依赖解析需要在下载制品前读取索引。
     */
    fun getText(url: String, headers: Map<String, String> = emptyMap()): Result<String> =
        Result.failure(UnsupportedOperationException("getText is not supported by this HTTP client"))

    /**
     * 将远程文件下载到指定路径，不进行解包。
     *
     * 中心仓制品需要先校验 sha256sum，再解压到缓存目录。
     */
    fun downloadToFile(
        url: String,
        targetFile: Path,
        headers: Map<String, String> = emptyMap()
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("downloadToFile is not supported by this HTTP client"))

    /**
     * 校验下载文件的 SHA-256 摘要。
     */
    fun verifySha256(file: Path, expectedSha256: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("verifySha256 is not supported by this HTTP client"))

    /**
     * 解压归档文件到目标目录。
     *
     * 官方中心仓返回 tar.gz 源码包；旧实现仍兼容 ZIP 包。
     */
    fun extractArchive(archiveFile: Path, targetDir: Path): Result<Unit> =
        Result.failure(UnsupportedOperationException("extractArchive is not supported by this HTTP client"))
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
        val tempFile = Files.createTempFile("kcjpm-download", ".archive")
        try {
            downloadToFile(url, tempFile).getOrThrow()
            extractArchive(tempFile, targetDir).getOrThrow()
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    override fun getText(url: String, headers: Map<String, String>): Result<String> = runCatching {
        openGetConnection(url, headers).use { connection ->
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Failed to read from $url: HTTP $responseCode")
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    override fun downloadToFile(
        url: String,
        targetFile: Path,
        headers: Map<String, String>
    ): Result<Unit> = runCatching {
        openGetConnection(url, headers).use { connection ->
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Failed to download from $url: HTTP $responseCode")
            }

            Files.createDirectories(targetFile.parent)
            connection.inputStream.use { input ->
                Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    override fun verifySha256(file: Path, expectedSha256: String): Result<Unit> = runCatching {
        val normalizedExpected = expectedSha256.removePrefix("sha256:").lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != normalizedExpected) {
            throw RuntimeException("Dependency checksum mismatch: expected $normalizedExpected, actual $actual")
        }
    }

    override fun extractArchive(archiveFile: Path, targetDir: Path): Result<Unit> = runCatching {
        when {
            archiveFile.hasGzipHeader() -> extractTarGz(archiveFile, targetDir)
            archiveFile.hasZipHeader() -> extractZip(archiveFile, targetDir)
            else -> throw RuntimeException("Unsupported dependency archive format: $archiveFile")
        }
    }

    private fun openGetConnection(
        url: String,
        headers: Map<String, String>
    ): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
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
        val normalizedTarget = targetDir.toAbsolutePath().normalize()

        ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val targetFile = normalizedTarget.resolve(entry.name).normalize()
                require(targetFile.startsWith(normalizedTarget)) {
                    "依赖压缩包包含非法路径: ${entry.name}"
                }
                
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

    /**
     * 解压 tar.gz 源码包到目标目录。
     *
     * 只接受普通文件和目录；PAX/GNU 扩展头会被读取或跳过，符号链接等可能越权的条目会被拒绝。
     */
    private fun extractTarGz(tarGzFile: Path, targetDir: Path) {
        val normalizedTarget = targetDir.toAbsolutePath().normalize()
        Files.createDirectories(normalizedTarget)

        GZIPInputStream(Files.newInputStream(tarGzFile)).use { gzip ->
            var pendingLongName: String? = null
            var pendingPaxPath: String? = null

            while (true) {
                val header = gzip.readExactTarBlock() ?: break
                if (header.all { it.toInt() == 0 }) {
                    break
                }

                val type = header[156].toInt().toChar()
                val size = header.parseTarSize()
                val name = pendingPaxPath ?: pendingLongName ?: header.parseTarName()
                pendingLongName = null

                when (type) {
                    '0', '\u0000' -> {
                        pendingPaxPath = null
                        val targetFile = normalizedTarget.resolve(name).normalize()
                        require(targetFile.startsWith(normalizedTarget)) {
                            "依赖压缩包包含非法路径: $name"
                        }

                        Files.createDirectories(targetFile.parent)
                        gzip.copyTarEntryTo(targetFile, size)
                    }
                    '5' -> {
                        pendingPaxPath = null
                        val targetDirectory = normalizedTarget.resolve(name).normalize()
                        require(targetDirectory.startsWith(normalizedTarget)) {
                            "依赖压缩包包含非法路径: $name"
                        }
                        Files.createDirectories(targetDirectory)
                        gzip.skipTarPadding(size)
                    }
                    'L' -> {
                        pendingLongName = gzip.readTarEntryText(size).trimEnd('\u0000', '\n')
                    }
                    'x', 'g' -> {
                        val paxAttributes = gzip.readTarEntryText(size).parsePaxAttributes()
                        if (type == 'x') {
                            pendingPaxPath = paxAttributes["path"]
                        }
                    }
                    else -> {
                        throw RuntimeException("Unsupported tar entry type '$type': $name")
                    }
                }
            }
        }
    }

    private fun Path.hasZipHeader(): Boolean {
        return Files.newInputStream(this).use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4b.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }
    }

    private fun Path.hasGzipHeader(): Boolean {
        return Files.newInputStream(this).use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 &&
                header[0] == 0x1f.toByte() &&
                header[1] == 0x8b.toByte()
        }
    }

    private fun ByteArray.parseTarName(): String {
        val name = parseTarString(0, 100)
        val prefix = parseTarString(345, 155)
        return if (prefix.isBlank()) name else "$prefix/$name"
    }

    private fun ByteArray.parseTarSize(): Long {
        val sizeText = parseTarString(124, 12).trim()
        return if (sizeText.isBlank()) 0 else sizeText.toLong(8)
    }

    private fun ByteArray.parseTarString(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it].toInt() == 0 }
            ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun InputStream.readExactTarBlock(): ByteArray? {
        val block = ByteArray(TAR_BLOCK_SIZE)
        var offset = 0
        while (offset < TAR_BLOCK_SIZE) {
            val read = read(block, offset, TAR_BLOCK_SIZE - offset)
            if (read < 0) {
                return if (offset == 0) null else throw RuntimeException("Truncated tar archive")
            }
            offset += read
        }
        return block
    }

    private fun InputStream.copyTarEntryTo(targetFile: Path, size: Long) {
        var remaining = size
        Files.newOutputStream(targetFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (remaining > 0) {
                val readSize = minOf(buffer.size.toLong(), remaining).toInt()
                val read = read(buffer, 0, readSize)
                if (read < 0) {
                    throw RuntimeException("Truncated tar entry: $targetFile")
                }
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        skipTarPadding(size)
    }

    private fun InputStream.readTarEntryText(size: Long): String {
        val bytes = ByteArray(size.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val read = read(bytes, offset, bytes.size - offset)
            if (read < 0) {
                throw RuntimeException("Truncated tar long-name entry")
            }
            offset += read
        }
        skipTarPadding(size)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun String.parsePaxAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        var offset = 0
        while (offset < length) {
            val spaceIndex = indexOf(' ', startIndex = offset)
            if (spaceIndex < 0) break

            val recordLength = substring(offset, spaceIndex).toIntOrNull() ?: break
            val recordEnd = offset + recordLength
            if (recordEnd > length || recordEnd <= spaceIndex) break

            val record = substring(spaceIndex + 1, recordEnd).trimEnd('\n')
            val equalsIndex = record.indexOf('=')
            if (equalsIndex > 0) {
                attributes[record.substring(0, equalsIndex)] = record.substring(equalsIndex + 1)
            }
            offset = recordEnd
        }
        return attributes
    }

    private fun InputStream.skipTarEntry(size: Long) {
        skipFully(size)
        skipTarPadding(size)
    }

    private fun InputStream.skipTarPadding(size: Long) {
        val padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
        skipFully(padding)
    }

    private fun InputStream.skipFully(size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) {
                    throw RuntimeException("Truncated tar archive")
                }
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val TAR_BLOCK_SIZE = 512
    }
}

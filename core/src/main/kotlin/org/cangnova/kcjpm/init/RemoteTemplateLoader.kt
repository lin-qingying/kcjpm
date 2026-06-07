package org.cangnova.kcjpm.init

import org.cangnova.kcjpm.process.ProcessRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.io.path.*

class RemoteTemplateLoader {
    
    private val customLoader = CustomTemplateLoader()
    
    suspend fun loadFromGit(
        gitUrl: String,
        branch: String = "main",
        cacheDir: Path? = null
    ): Result<ProjectTemplate.Custom> = runCatching {
        val targetDir = cacheDir ?: createTempCacheDir()
        
        val cloneCommand = buildList {
            add("git")
            add("clone")
            add("--depth")
            add("1")
            add("--branch")
            add(branch)
            add(gitUrl)
            add(targetDir.toString())
        }
        
        val result = ProcessRunner.run(cloneCommand, timeout = Duration.ofMinutes(2)).getOrThrow()
        if (result.exitCode != 0) {
            throw RuntimeException("Git clone failed: ${result.output}")
        }
        
        customLoader.loadFromPath(targetDir).getOrThrow()
    }
    
    suspend fun loadFromRegistry(
        registryUrl: String,
        name: String,
        version: String,
        cacheDir: Path? = null
    ): Result<ProjectTemplate.Custom> = runCatching {
        val targetDir = cacheDir ?: createTempCacheDir()
        Files.createDirectories(targetDir)
        
        val downloadUrl = "${registryUrl.trimEnd('/')}/templates/$name/$version/download"
        val archiveFile = Files.createTempFile("kcjpm-template-", ".zip")

        try {
            downloadFile(downloadUrl, archiveFile)
            extractZip(archiveFile, targetDir)

            val templateDir = resolveTemplateRoot(targetDir)
            val template = customLoader.loadFromPath(templateDir).getOrThrow()
            template.copy(
                info = template.info.copy(
                    source = TemplateSource.Registry(registryUrl, name, version)
                )
            )
        } finally {
            Files.deleteIfExists(archiveFile)
        }
    }

    private fun downloadFile(url: String, targetFile: Path) {
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        try {
            val responseCode = connection.responseCode
            if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                throw RuntimeException("模板下载失败: HTTP $responseCode ($url)")
            }

            connection.inputStream.use { input ->
                Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractZip(zipFile: Path, targetDir: Path) {
        val normalizedTarget = targetDir.toAbsolutePath().normalize()

        ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outputPath = normalizedTarget.resolve(entry.name).normalize()
                require(outputPath.startsWith(normalizedTarget)) {
                    "模板压缩包包含非法路径: ${entry.name}"
                }

                if (entry.isDirectory) {
                    Files.createDirectories(outputPath)
                } else {
                    outputPath.parent?.let(Files::createDirectories)
                    Files.copy(zip, outputPath, StandardCopyOption.REPLACE_EXISTING)
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun resolveTemplateRoot(targetDir: Path): Path {
        if (targetDir.resolve("template.toml").exists()) {
            return targetDir
        }

        val childTemplateDirs = targetDir.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("template.toml").exists() }

        return when (childTemplateDirs.size) {
            1 -> childTemplateDirs.single()
            else -> targetDir
        }
    }
    
    private fun createTempCacheDir(): Path {
        val userHome = System.getProperty("user.home")
        val cacheBase = Path(userHome).resolve(".kcjpm/template-cache")
        Files.createDirectories(cacheBase)
        
        return Files.createTempDirectory(cacheBase, "template-")
    }
}

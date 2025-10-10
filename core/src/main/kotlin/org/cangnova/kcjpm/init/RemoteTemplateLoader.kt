package org.cangnova.kcjpm.init

import java.nio.file.Files
import java.nio.file.Path
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
        
        val process = ProcessBuilder(cloneCommand)
            .redirectErrorStream(true)
            .start()
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Git clone 失败: $output")
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
        
        val downloadUrl = "$registryUrl/templates/$name/$version/download"
        
        TODO("实现从仓库下载模板")
    }
    
    private fun createTempCacheDir(): Path {
        val userHome = System.getProperty("user.home")
        val cacheBase = Path(userHome).resolve(".kcjpm/template-cache")
        Files.createDirectories(cacheBase)
        
        return Files.createTempDirectory(cacheBase, "template-")
    }
}
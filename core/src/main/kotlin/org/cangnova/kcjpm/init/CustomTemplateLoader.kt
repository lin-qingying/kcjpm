package org.cangnova.kcjpm.init

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import net.peanuuutz.tomlkt.Toml
import java.nio.file.Path
import kotlin.io.path.*

class CustomTemplateLoader {
    
    private val toml = Toml { ignoreUnknownKeys = true }
    
    fun loadFromPath(templatePath: Path): Result<ProjectTemplate.Custom> = runCatching {
        require(templatePath.exists() && templatePath.isDirectory()) {
            "模板路径不存在或不是目录: $templatePath"
        }
        
        val metadataFile = templatePath.resolve("template.toml")
        require(metadataFile.exists()) {
            "模板元数据文件不存在: template.toml"
        }
        
        val metadata = toml.decodeFromString(
            TemplateMetadata.serializer(),
            metadataFile.readText()
        )
        
        val files = loadTemplateFiles(templatePath, metadata)
        
        val info = TemplateInfo(
            name = metadata.name,
            displayName = metadata.displayName ?: metadata.name,
            description = metadata.description ?: "",
            author = metadata.author,
            version = metadata.version,
            type = TemplateType.CUSTOM,
            source = TemplateSource.Local(templatePath)
        )
        
        ProjectTemplate.Custom(
            info = info,
            files = files,
            basePath = templatePath
        )
    }
    
    private fun loadTemplateFiles(
        templatePath: Path,
        metadata: TemplateMetadata
    ): List<TemplateFile> {
        val files = mutableListOf<TemplateFile>()
        val filesDir = templatePath.resolve("files")
        
        if (!filesDir.exists()) {
            return emptyList()
        }
        
        filesDir.walk().filter { it.isRegularFile() }.forEach { file ->
            val relativePath = filesDir.relativize(file).toString()
            val content = file.readText()
            
            val isTemplate = metadata.templates.isEmpty() || 
                metadata.templates.any { pattern ->
                    matchesPattern(relativePath, pattern)
                }
            
            files.add(
                TemplateFile(
                    path = relativePath,
                    content = content,
                    isTemplate = isTemplate
                )
            )
        }
        
        return files
    }
    
    private fun matchesPattern(path: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex()
        return regex.matches(path)
    }
}

@Serializable
data class TemplateMetadata(
    val name: String,
    @SerialName("display-name")
    val displayName: String? = null,
    val description: String? = null,
    val author: String? = null,
    val version: String = "1.0.0",
    val templates: List<String> = listOf("*"),
    val variables: Map<String, VariableConfig> = emptyMap()
)

@Serializable
data class VariableConfig(
    val description: String? = null,
    val default: String? = null,
    val required: Boolean = false
)
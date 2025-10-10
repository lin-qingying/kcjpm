package org.cangnova.kcjpm.init

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class DefaultProjectInitializer : ProjectInitializer {
    
    override suspend fun initProject(
        targetPath: Path,
        template: ProjectTemplate,
        options: InitOptions
    ): Result<InitResult> = runCatching {
        require(!targetPath.exists() || targetPath.listDirectoryEntries().isEmpty()) {
            "目标路径已存在且不为空: $targetPath"
        }
        
        Files.createDirectories(targetPath)
        
        val variables = buildVariables(options)
        val createdFiles = mutableListOf<Path>()
        
        template.files.forEach { templateFile ->
            val filePath = targetPath.resolve(templateFile.path)
            Files.createDirectories(filePath.parent)
            
            val content = if (templateFile.isTemplate) {
                TemplateRenderer.render(templateFile.content, variables)
            } else {
                templateFile.content
            }
            
            filePath.writeText(content)
            createdFiles.add(filePath)
        }
        
        InitResult(
            projectPath = targetPath,
            createdFiles = createdFiles,
            template = template.info
        )
    }
    
    override suspend fun listTemplates(): Result<List<TemplateInfo>> = runCatching {
        BuiltinTemplates.getAllTemplates().map { it.info }
    }
    
    override suspend fun getTemplate(name: String): Result<ProjectTemplate> = runCatching {
        BuiltinTemplates.getTemplate(name)
            ?: throw IllegalArgumentException("模板不存在: $name")
    }
    
    private fun buildVariables(options: InitOptions): Map<String, Any> {
        val baseVariables = mutableMapOf<String, Any>(
            "project_name" to options.projectName,
            "version" to options.version,
            "output_type" to options.outputType.name.lowercase().replace('_', '-')
        )
        
        if (options.authors.isNotEmpty()) {
            baseVariables["authors"] = options.authors
        }
        
        if (options.description != null) {
            baseVariables["description"] = options.description
        }
        
        if (options.license != null) {
            baseVariables["license"] = options.license
        }
        
        baseVariables.putAll(options.variables)
        
        return baseVariables
    }
}
package org.cangnova.kcjpm.init

import java.nio.file.Path

interface ProjectInitializer {
    suspend fun initProject(
        targetPath: Path,
        template: ProjectTemplate,
        options: InitOptions
    ): Result<InitResult>
    
    suspend fun listTemplates(): Result<List<TemplateInfo>>
    
    suspend fun getTemplate(name: String): Result<ProjectTemplate>
}

data class InitOptions(
    val projectName: String,
    val version: String = "0.1.0",
    val authors: List<String> = emptyList(),
    val license: String? = null,
    val description: String? = null,
    val outputType: OutputType = OutputType.EXECUTABLE,
    val variables: Map<String, String> = emptyMap()
)

enum class OutputType {
    EXECUTABLE,
    LIBRARY,
    STATIC_LIBRARY,
    DYNAMIC_LIBRARY
}

data class InitResult(
    val projectPath: Path,
    val createdFiles: List<Path>,
    val template: TemplateInfo
)

data class TemplateInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val author: String? = null,
    val version: String = "1.0.0",
    val type: TemplateType,
    val source: TemplateSource
)

enum class TemplateType {
    EXECUTABLE,
    LIBRARY,
    WORKSPACE,
    CUSTOM
}

sealed class TemplateSource {
    data class Builtin(val id: String) : TemplateSource()
    data class Local(val path: Path) : TemplateSource()
    data class Git(val url: String, val branch: String = "main") : TemplateSource()
    data class Registry(val registryUrl: String, val name: String, val version: String) : TemplateSource()
}

sealed class ProjectTemplate {
    abstract val info: TemplateInfo
    abstract val files: List<TemplateFile>
    
    data class Builtin(
        override val info: TemplateInfo,
        override val files: List<TemplateFile>
    ) : ProjectTemplate()
    
    data class Custom(
        override val info: TemplateInfo,
        override val files: List<TemplateFile>,
        val basePath: Path
    ) : ProjectTemplate()
}

data class TemplateFile(
    val path: String,
    val content: String,
    val isTemplate: Boolean = true
)
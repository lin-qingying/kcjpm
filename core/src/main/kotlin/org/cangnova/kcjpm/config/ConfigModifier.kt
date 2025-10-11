package org.cangnova.kcjpm.config

import java.nio.file.Path
import kotlin.io.path.writeText

object ConfigModifier {
    
    fun addDependency(
        config: CjpmConfig,
        name: String,
        dependencyConfig: DependencyConfig
    ): CjpmConfig {
        return config.copy(
            dependencies = config.dependencies + (name to dependencyConfig)
        )
    }
    
    fun saveConfig(config: CjpmConfig, configFilePath: Path) {
        val content = serializeToToml(config)
        configFilePath.writeText(content)
    }
    
    private fun serializeToToml(config: CjpmConfig): String {
        val content = buildString {
            config.`package`?.let { packageInfo ->
                appendLine("[package]")
                appendLine("name = \"${packageInfo.name}\"")
                appendLine("version = \"${packageInfo.version}\"")
                appendLine("cjc-version = \"${packageInfo.cjcVersion}\"")
                appendLine("output-type = \"${packageInfo.outputType.name.lowercase().replace('_', '-')}\"")
                
                if (packageInfo.authors.isNotEmpty()) {
                    append("authors = [")
                    append(packageInfo.authors.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                
                packageInfo.description?.let { appendLine("description = \"$it\"") }
                packageInfo.license?.let { appendLine("license = \"$it\"") }
                packageInfo.repository?.let { appendLine("repository = \"$it\"") }
                appendLine()
            }
            
            config.registry?.let { registry ->
                appendLine("[registry]")
                appendLine("default = \"${registry.default}\"")
                if (registry.mirrors.isNotEmpty()) {
                    append("mirrors = [")
                    append(registry.mirrors.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                registry.privateUrl?.let { appendLine("private-url = \"$it\"") }
                registry.privateUsername?.let { appendLine("private-username = \"$it\"") }
                registry.privateToken?.let { appendLine("private-token = \"$it\"") }
                appendLine()
            }
            
            if (config.dependencies.isNotEmpty()) {
                appendLine("[dependencies]")
                config.dependencies.forEach { (name, dep) ->
                    when {
                        dep.path != null -> {
                            appendLine("$name = { path = \"${dep.path}\" }")
                        }
                        dep.git != null -> {
                            append("$name = { git = \"${dep.git}\"")
                            dep.tag?.let { append(", tag = \"$it\"") }
                            dep.branch?.let { append(", branch = \"$it\"") }
                            dep.commit?.let { append(", commit = \"$it\"") }
                            appendLine(" }")
                        }
                        dep.version != null -> {
                            if (dep.registry != null) {
                                appendLine("$name = { version = \"${dep.version}\", registry = \"${dep.registry}\" }")
                            } else {
                                appendLine("$name = \"${dep.version}\"")
                            }
                        }
                    }
                }
                appendLine()
            }
            
            config.build?.let { build ->
                appendLine("[build]")
                if (build.sourceDir != "src") appendLine("source-dir = \"${build.sourceDir}\"")
                if (build.outputDir != "target") appendLine("output-dir = \"${build.outputDir}\"")
                if (build.testDir != "tests") appendLine("test-dir = \"${build.testDir}\"")
                if (!build.parallel) appendLine("parallel = false")
                if (!build.incremental) appendLine("incremental = false")
                build.jobs?.let { appendLine("jobs = $it") }
                if (build.verbose) appendLine("verbose = true")
                
                if (build.preBuild.isNotEmpty()) {
                    append("pre-build = [")
                    append(build.preBuild.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                
                if (build.postBuild.isNotEmpty()) {
                    append("post-build = [")
                    append(build.postBuild.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                appendLine()
            }
            
            config.workspace?.let { workspace ->
                appendLine("[workspace]")
                if (workspace.members.isNotEmpty()) {
                    append("members = [")
                    append(workspace.members.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                if (workspace.defaultMembers.isNotEmpty()) {
                    append("default-members = [")
                    append(workspace.defaultMembers.joinToString(", ") { "\"$it\"" })
                    appendLine("]")
                }
                appendLine()
            }
            
            config.profile.forEach { (name, profile) ->
                appendLine("[profile.$name]")
                appendLine("optimization-level = ${profile.optimizationLevel}")
                if (profile.debugInfo) appendLine("debug-info = true")
                if (profile.lto) appendLine("lto = true")
                appendLine()
            }
        }
        
        return content.trimEnd()
    }
}
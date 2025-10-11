package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.ConfigFormatDetector
import org.cangnova.kcjpm.config.ConfigModifier
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.dependency.DefaultDependencyManager
import kotlin.io.path.Path
import kotlin.io.path.exists
import java.nio.file.Paths

class AddCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Add) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        output.info(Messages.get("add.adding", command.dependency))
        
        return try {
            output.startProgress(Messages.get("add.loadingConfig"))
            val config = ConfigLoader.loadFromProjectRoot(projectPath).getOrThrow()
            val (format, configFilePath) = ConfigFormatDetector.detectFromProjectRoot(projectPath).getOrThrow()
            output.completeProgress(Messages.get("add.configLoaded"))
            
            val (dependencyName, dependencyConfig) = parseDependencyInput(command)
            
            output.startProgress(Messages.get("add.validatingDependency"))
            val dependencyManager = createDependencyManager()
            dependencyManager.validateDependency(
                dependencyName,
                dependencyConfig,
                projectPath,
                config.registry
            ).getOrThrow()
            output.completeProgress(Messages.get("add.dependencyValidated"))
            
            output.startProgress(Messages.get("add.updatingConfig"))
            val updatedConfig = ConfigModifier.addDependency(config, dependencyName, dependencyConfig)
            ConfigModifier.saveConfig(updatedConfig, configFilePath)
            output.completeProgress(Messages.get("add.configUpdated"))
            
            output.newline()
            output.success(Messages.get("add.success", dependencyName))
            output.info(Messages.get("add.buildHint"))
            
            0
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("Dependency not found in registry") == true -> {
                    val depName = extractDependencyName(e.message!!)
                    Messages.get("add.dependencyNotFound", depName)
                }
                e.message?.contains("Registry returned HTTP") == true -> {
                    Messages.get("add.registryNotAccessible")
                }
                e.message?.contains("Git repository not accessible") == true -> {
                    val gitUrl = extractGitUrl(e.message!!)
                    Messages.get("add.gitNotAccessible", gitUrl)
                }
                e.message?.contains("Dependency path does not exist") == true -> {
                    val path = extractPath(e.message!!)
                    Messages.get("add.pathNotExist", path)
                }
                e.message?.contains("Failed to download from") == true -> {
                    Messages.get("add.registryNotAccessible")
                }
                e.message?.contains("Connection") == true || e.message?.contains("timeout") == true -> {
                    Messages.get("add.registryNotAccessible")
                }
                else -> Messages.get("add.failed", e.message ?: "Unknown error")
            }
            handleError(errorMessage, e)
        }
    }
    
    private fun extractDependencyName(message: String): String {
        val regex = "Dependency not found in registry: (.+)".toRegex()
        return regex.find(message)?.groupValues?.get(1) ?: "unknown"
    }
    
    private fun extractGitUrl(message: String): String {
        val regex = "Git repository not accessible: (.+)".toRegex()
        return regex.find(message)?.groupValues?.get(1) ?: "unknown"
    }
    
    private fun extractPath(message: String): String {
        val regex = "Dependency path does not exist: (.+)".toRegex()
        return regex.find(message)?.groupValues?.get(1) ?: "unknown"
    }
    
    private fun parseDependencyInput(command: Command.Add): Pair<String, DependencyConfig> {
        val dependencyConfig = when {
            command.git != null -> {
                DependencyConfig(
                    git = command.git,
                    tag = command.tag,
                    branch = command.branch
                )
            }
            command.localPath != null -> {
                DependencyConfig(path = command.localPath)
            }
            else -> {
                val (name, version) = parseNameAndVersion(command.dependency)
                DependencyConfig(version = version)
            }
        }
        
        val dependencyName = when {
            command.git != null || command.localPath != null -> command.dependency
            else -> parseNameAndVersion(command.dependency).first
        }
        
        return dependencyName to dependencyConfig
    }
    
    private fun parseNameAndVersion(input: String): Pair<String, String> {
        val atIndex = input.lastIndexOf('@')
        return if (atIndex > 0) {
            val name = input.substring(0, atIndex)
            val version = input.substring(atIndex + 1)
            name to version
        } else {
            input to "latest"
        }
    }
    
    private fun createDependencyManager(): DefaultDependencyManager {
        val userHome = System.getProperty("user.home")
        val cacheDir = Paths.get(userHome, ".kcjpm", "cache")
        return DefaultDependencyManager(cacheDir)
    }
}
package org.cangnova.kcjpm.config.toml

import net.peanuuutz.tomlkt.Toml
import org.cangnova.kcjpm.build.*
import org.cangnova.kcjpm.config.*
import org.cangnova.kcjpm.platform.KPath
import org.cangnova.kcjpm.platform.getFileSystem

class TomlConfigParser : ConfigParser {
    private val toml = Toml { 
        ignoreUnknownKeys = true
    }
    
    override fun getSupportedFormat(): ConfigFormat = ConfigFormat.CUSTOM
    
    override fun loadConfig(configPath: KPath): Result<CjpmConfig> = runCatching {
        val content = getFileSystem().readText(configPath).getOrThrow()
        toml.decodeFromString(CjpmConfig.serializer(), content)
    }
    
    override fun loadFromProjectRoot(projectRoot: KPath): Result<CjpmConfig> {
        val configPath = projectRoot.resolve(getConfigFileName())
        return loadConfig(configPath)
    }
    
    override fun loadAndConvert(
        projectRoot: KPath,
        targetPlatform: CompilationTarget?,
        profileName: String
    ): Result<CompilationContext> {
        return loadFromProjectRoot(projectRoot).mapCatching { config ->
            convertToContext(config, projectRoot, targetPlatform, profileName)
        }
    }
    
    private fun convertToContext(
        config: CjpmConfig,
        projectRoot: KPath,
        targetPlatform: CompilationTarget?,
        profileName: String
    ): CompilationContext {
        TODO("Implement context conversion")
    }
    
    override fun getConfigFileName(): String = "kcjpm.toml"
}
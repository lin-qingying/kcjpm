package org.cangnova.kcjpm.config.toml

import net.peanuuutz.tomlkt.Toml
import org.cangnova.kcjpm.build.*
import org.cangnova.kcjpm.config.*
import java.nio.file.Path
import kotlin.io.path.readText

class TomlConfigParser : ConfigParser {
    private val toml = Toml { 
        ignoreUnknownKeys = true
    }
    
    override fun getSupportedFormat(): ConfigFormat = ConfigFormat.CUSTOM
    
    override fun loadConfig(configPath: Path): Result<CjpmConfig> = runCatching {
        val content = configPath.readText()
        toml.decodeFromString(CjpmConfig.serializer(), content)
    }
    
    override fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig> {
        val configPath = projectRoot.resolve(getConfigFileName())
        return loadConfig(configPath)
    }
    
    override fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget,
        profileName: String
    ): Result<CompilationContext> {
        return loadFromProjectRoot(projectRoot).mapCatching { config ->
            ConfigToContextConverter.convert(config, projectRoot, targetPlatform, profileName)
                .getOrThrow()
        }
    }
    
    override fun getConfigFileName(): String = "kcjpm.toml"
}
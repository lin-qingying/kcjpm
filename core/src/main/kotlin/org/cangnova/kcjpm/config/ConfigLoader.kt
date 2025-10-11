package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import java.nio.file.Path
import java.util.ServiceLoader

object ConfigLoader {
    private val parsers = mutableMapOf<ConfigFormat, ConfigParser>()
    
    init {
        autoDiscoverParsers()
    }
    
    private fun autoDiscoverParsers() {
        val loader = ServiceLoader.load(ConfigParser::class.java)
        for (parser in loader) {
            val format = parser.getSupportedFormat()
            registerParser(format, parser)
        }
    }
    
    fun registerParser(format: ConfigFormat, parser: ConfigParser) {
        parsers[format] = parser
    }
    
    fun getParser(format: ConfigFormat): ConfigParser? {
        return parsers[format]
    }
    
    fun loadConfig(configPath: Path, format: ConfigFormat? = null): Result<CjpmConfig> {
        val detectedFormat = format ?: ConfigFormatDetector.detectFormat(configPath).getOrElse { 
            return Result.failure(it)
        }
        
        val parser = getParser(detectedFormat)
            ?: return Result.failure(
                IllegalStateException("No parser registered for format: ${detectedFormat.name}")
            )
        
        return parser.loadConfig(configPath)
    }
    
    fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig> {
        return ConfigFormatDetector.detectFromProjectRoot(projectRoot).mapCatching { (format, configPath) ->
            val parser = getParser(format)
                ?: throw IllegalStateException("No parser registered for format: ${format.name}")
            
            parser.loadConfig(configPath).getOrThrow()
        }
    }
    
    fun <T> loadAndConvert(
        configPath: Path,
        converter: (CjpmConfig) -> T,
        format: ConfigFormat? = null
    ): Result<T> {
        return loadConfig(configPath, format).map(converter)
    }
    
    fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget ? = null,
        profileName: String = "release"
    ): Result<CompilationContext> {
        return ConfigFormatDetector.detectFromProjectRoot(projectRoot).mapCatching { (format, configPath) ->
            val parser = getParser(format)
                ?: throw IllegalStateException("No parser registered for format: ${format.name}")
            
            parser.loadAndConvert(projectRoot, targetPlatform, profileName).getOrThrow()
        }
    }
}
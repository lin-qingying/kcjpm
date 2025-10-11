package org.cangnova.kcjpm.config

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

enum class ConfigFormat {
    OFFICIAL,
    CUSTOM
}

object ConfigFormatDetector {
    
    fun detectFormat(configPath: Path): Result<ConfigFormat> = runCatching {
        if (!configPath.exists()) {
            throw IllegalArgumentException("Config file not found: $configPath")
        }
        
        when (configPath.name) {
            "cjpm.toml" -> ConfigFormat.OFFICIAL
            "kcjpm.toml" -> ConfigFormat.CUSTOM
            else -> {
                val content = configPath.readText()
                detectFromContent(content)
            }
        }
    }
    
    private fun detectFromContent(content: String): ConfigFormat {
        val officialMarkers = listOf(
            "compile-option",
            "link-option",
            "package-configuration"
        )
        
        val customMarkers = listOf(
            "authors",
            "license",
            "repository",
            "[registry]",
            "[workspace]",
            "[profile"
        )
        
        val hasOfficialMarkers = officialMarkers.any { marker ->
            content.contains(Regex("""$marker\s*="""))
        }
        
        val hasCustomMarkers = customMarkers.any { marker ->
            content.contains(marker)
        }
        
        return when {
            hasOfficialMarkers -> ConfigFormat.OFFICIAL
            hasCustomMarkers -> ConfigFormat.CUSTOM
            else -> throw IllegalArgumentException("Unable to recognize config file format")
        }
    }
    
    fun detectFromProjectRoot(projectRoot: Path): Result<Pair<ConfigFormat, Path>> = runCatching {
        val customPath = projectRoot.resolve("kcjpm.toml")
        val officialPath = projectRoot.resolve("cjpm.toml")
        
        when {
            customPath.exists() -> ConfigFormat.CUSTOM to customPath
            officialPath.exists() -> ConfigFormat.OFFICIAL to officialPath
            else -> throw IllegalArgumentException("Config file not found in project root: cjpm.toml or kcjpm.toml")
        }
    }
}

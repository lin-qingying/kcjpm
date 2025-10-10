package org.cangnova.kcjpm.build

import java.nio.file.Path

data class BuildScriptResult(
    val linkLibraries: List<String> = emptyList(),
    val includeDirs: List<Path> = emptyList(),
    val rerunIfChanged: List<Path> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val customFlags: Map<String, String> = emptyMap()
) {
    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}

data class BuildScriptContext(
    val projectRoot: Path,
    val outDir: Path,
    val target: CompilationTarget,
    val profile: String,
    val packageName: String,
    val packageVersion: String
) {
    fun toEnvironmentVariables(): Map<String, String> {
        return mapOf(
            "KCJPM_OUT_DIR" to outDir.toString(),
            "KCJPM_TARGET" to target.triple,
            "KCJPM_PROFILE" to profile,
            "KCJPM_MANIFEST_DIR" to projectRoot.toString(),
            "KCJPM_PKG_NAME" to packageName,
            "KCJPM_PKG_VERSION" to packageVersion
        )
    }
}

sealed class BuildScriptInstruction {
    data class LinkLibrary(val library: String) : BuildScriptInstruction()
    data class IncludeDir(val path: Path) : BuildScriptInstruction()
    data class RerunIfChanged(val path: Path) : BuildScriptInstruction()
    data class Warning(val message: String) : BuildScriptInstruction()
    data class Error(val message: String) : BuildScriptInstruction()
    data class CustomFlag(val key: String, val value: String) : BuildScriptInstruction()
    
    companion object {
        private const val PREFIX = "kcjpm:"
        
        fun parse(line: String, projectRoot: Path): BuildScriptInstruction? {
            if (!line.startsWith(PREFIX)) return null
            
            val content = line.substring(PREFIX.length)
            val parts = content.split('=', limit = 2)
            if (parts.size != 2) return null
            
            val key = parts[0].trim()
            val value = parts[1].trim()
            
            return when (key) {
                "rustc-link-lib", "link-lib" -> LinkLibrary(value)
                "include-dir" -> IncludeDir(projectRoot.resolve(value).normalize())
                "rerun-if-changed" -> RerunIfChanged(projectRoot.resolve(value).normalize())
                "warning" -> Warning(value)
                "error" -> Error(value)
                else -> CustomFlag(key, value)
            }
        }
    }
}
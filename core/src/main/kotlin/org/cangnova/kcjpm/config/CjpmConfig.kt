package org.cangnova.kcjpm.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import net.peanuuutz.tomlkt.*

@Serializable
data class CjpmConfig(
    val `package`: PackageInfo? = null,
    val registry: RegistryConfig? = null,
    @Serializable(with = DependencyMapSerializer::class)
    val dependencies: Map<String, DependencyConfig> = emptyMap(),
    val build: BuildConfig? = null,
    val workspace: WorkspaceConfig? = null,
    val profile: Map<String, ProfileConfig> = emptyMap(),
    val publish: PublishConfig? = null,
    val test: TestConfig? = null
)

@Serializable
data class PackageInfo(
    val name: String,
    val version: String,
    @SerialName("cjc-version")
    val cjcVersion: String,
    @SerialName("output-type")
    val outputType: OutputType,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val license: String? = null,
    val repository: String? = null
)

@Serializable
enum class OutputType {
    @SerialName("executable")
    EXECUTABLE,
    
    @SerialName("library")
    LIBRARY,
    
    @SerialName("static-library")
    STATIC_LIBRARY,
    
    @SerialName("dynamic-library")
    DYNAMIC_LIBRARY
}

@Serializable
data class RegistryConfig(
    val default: String = "https://repo.cangjie-lang.cn",
    val mirrors: List<String> = emptyList(),
    @SerialName("private-url")
    val privateUrl: String? = null,
    @SerialName("private-username")
    val privateUsername: String? = null,
    @SerialName("private-token")
    val privateToken: String? = null
)


@Serializable
data class DependencyConfig(
    val version: String? = null,
    val registry: String? = null,
    val path: String? = null,
    val git: String? = null,
    val tag: String? = null,
    val branch: String? = null,
    val commit: String? = null,
    val optional: Boolean = false
)

@Serializable
data class BuildConfig(
    @SerialName("source-dir")
    val sourceDir: String = "src",
    @SerialName("output-dir")
    val outputDir: String = "target",
    @SerialName("test-dir")
    val testDir: String = "tests",
    val parallel: Boolean = true,
    val incremental: Boolean = true,
    val jobs: Int? = null,
    val verbose: Boolean = false,
    @SerialName("pre-build")
    val preBuild: List<String> = emptyList(),
    @SerialName("post-build")
    val postBuild: List<String> = emptyList(),
    val target: TargetConfig? = null,
    val ffi: FfiConfig? = null
)

@Serializable
data class TargetConfig(
    @SerialName("compiler-flags")
    val compilerFlags: List<String> = emptyList(),
    @SerialName("linker-flags")
    val linkerFlags: List<String> = emptyList(),
    @SerialName("bin-paths")
    val binPaths: List<String> = emptyList(),
    
    @SerialName("windows-compiler-flags")
    val windowsCompilerFlags: List<String>? = null,
    @SerialName("windows-linker-flags")
    val windowsLinkerFlags: List<String>? = null,
    @SerialName("windows-bin-paths")
    val windowsBinPaths: List<String>? = null,
    
    @SerialName("linux-compiler-flags")
    val linuxCompilerFlags: List<String>? = null,
    @SerialName("linux-linker-flags")
    val linuxLinkerFlags: List<String>? = null,
    @SerialName("linux-bin-paths")
    val linuxBinPaths: List<String>? = null,
    
    @SerialName("macos-compiler-flags")
    val macosCompilerFlags: List<String>? = null,
    @SerialName("macos-linker-flags")
    val macosLinkerFlags: List<String>? = null,
    @SerialName("macos-bin-paths")
    val macosBinPaths: List<String>? = null
)

@Serializable
data class FfiConfig(
    val includes: List<String> = emptyList(),
    @SerialName("lib-paths")
    val libPaths: List<String> = emptyList(),
    val libs: List<String> = emptyList(),
    @SerialName("c-flags")
    val cFlags: List<String> = emptyList(),
    @SerialName("cxx-flags")
    val cxxFlags: List<String> = emptyList()
)

@Serializable
data class WorkspaceConfig(
    val members: List<String> = emptyList(),
    @SerialName("default-members")
    val defaultMembers: List<String> = emptyList()
)

@Serializable
data class ProfileConfig(
    @SerialName("optimization-level")
    val optimizationLevel: Int,
    @SerialName("debug-info")
    val debugInfo: Boolean = false,
    val lto: Boolean = false
)

@Serializable
data class PublishConfig(
    val registry: String = "default",
    val exclude: List<String> = emptyList()
)

@Serializable
data class TestConfig(
    val timeout: Int = 300,
    val parallel: Boolean = true
)

object DependencyMapSerializer : KSerializer<Map<String, DependencyConfig>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DependencyMap")

    override fun deserialize(decoder: Decoder): Map<String, DependencyConfig> {
        require(decoder is TomlDecoder) { "DependencyMapSerializer only works with TOML" }
        
        val element = decoder.decodeTomlElement()
        require(element is TomlTable) { "Dependencies must be a TOML table" }
        
        return element.content.mapValues { (_, value) ->
            when (value) {
                is TomlLiteral -> {
                    DependencyConfig(version = value.toString())
                }
                is TomlTable -> {
                    val toml = Toml { ignoreUnknownKeys = true }
                    toml.decodeFromTomlElement(DependencyConfig.serializer(), value)
                }
                else -> throw IllegalArgumentException("Invalid dependency format")
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, DependencyConfig>) {
        throw NotImplementedError("Serialization not supported")
    }
}
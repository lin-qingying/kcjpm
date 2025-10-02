package org.cangnova.kcjpm.config.official

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OfficialCjpmConfig(
    val `package`: OfficialPackageInfo,
    val dependencies: Map<String, OfficialDependencyConfig> = emptyMap()
)

@Serializable
data class OfficialPackageInfo(
    val name: String,
    val version: String,
    @SerialName("cjc-version")
    val cjcVersion: String,
    @SerialName("output-type")
    val outputType: String,
    @SerialName("compile-option")
    val compileOption: String = "",
    @SerialName("link-option")
    val linkOption: String = "",
    @SerialName("src-dir")
    val srcDir: String = "",
    @SerialName("target-dir")
    val targetDir: String = "",
    val description: String = "",
    @SerialName("package-configuration")
    val packageConfiguration: Map<String, String> = emptyMap()
)

@Serializable
data class OfficialDependencyConfig(
    val version: String? = null,
    val path: String? = null,
    val git: String? = null,
    val tag: String? = null,
    val branch: String? = null,
    val commit: String? = null
)
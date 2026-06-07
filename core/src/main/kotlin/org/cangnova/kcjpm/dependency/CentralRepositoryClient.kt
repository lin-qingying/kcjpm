package org.cangnova.kcjpm.dependency

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.cangnova.kcjpm.config.RegistryConfig
import java.net.URLEncoder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * 仓颉中心仓官方协议客户端。
 *
 * 协议依据中心仓 1.0.0 文档：
 * - 索引下载：GET /index/{mo}/{du}/{module}[?organization=org]
 * - 制品下载：GET /pkg/{module}/{version}[?organization=org]
 * - 制品包格式：tar.gz 源码包，使用索引中的 sha256sum 校验。
 */
class CentralRepositoryClient(
    private val httpClient: DependencyHttpClient = DefaultDependencyHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun resolvePackage(
        registryUrl: String,
        dependencyName: String,
        versionRequirement: String
    ): Result<CentralRepositoryPackage> = runCatching {
        val coordinate = CentralRepositoryCoordinate.parse(dependencyName)
        val requirement = VersionRequirement.parse(versionRequirement)
        val entries = downloadIndex(registryUrl, coordinate).getOrThrow()
            .filter { it.matches(coordinate) }
            .filterNot { it.yanked }

        val selected = requirement.select(entries)
            ?: throw RuntimeException("Dependency not found in registry: $dependencyName@$versionRequirement")

        val checksum = selected.sha256sum
            ?: throw RuntimeException("Registry index missing sha256sum: ${coordinate.displayName}@${selected.version}")

        CentralRepositoryPackage(
            coordinate = coordinate,
            version = selected.version,
            registryUrl = registryUrl.normalizeRegistryUrl(),
            sha256sum = checksum
        )
    }

    fun downloadPackage(
        pkg: CentralRepositoryPackage,
        targetDir: Path
    ): Result<Unit> = runCatching {
        Files.createDirectories(targetDir)

        val packageUrl = packageUrl(pkg.registryUrl, pkg.coordinate, pkg.version)
        val tempFile = Files.createTempFile("kcjpm-central-repo", ".tar.gz")
        val tempDir = Files.createTempDirectory("kcjpm-central-repo-extract")

        try {
            httpClient.downloadToFile(packageUrl, tempFile).getOrThrow()
            httpClient.verifySha256(tempFile, pkg.sha256sum).getOrThrow()
            httpClient.extractArchive(tempFile, tempDir).getOrThrow()
            replaceDirectoryContent(tempDir.normalizePackageRoot(), targetDir)
        } finally {
            Files.deleteIfExists(tempFile)
            tempDir.toFile().deleteRecursively()
        }
    }

    fun indexUrl(registryUrl: String, coordinate: CentralRepositoryCoordinate): String {
        val module = coordinate.module
        val first = module.take(2)
        val second = module.drop(2).take(2)
        val path = "/index/${first.encodePathSegment()}/${second.encodePathSegment()}/${module.encodePathSegment()}"
        return registryUrl.normalizeRegistryUrl() + path + coordinate.organizationQuery()
    }

    fun packageUrl(registryUrl: String, coordinate: CentralRepositoryCoordinate, version: String): String {
        val path = "/pkg/${coordinate.module.encodePathSegment()}/${version.encodePathSegment()}"
        return registryUrl.normalizeRegistryUrl() + path + coordinate.organizationQuery()
    }

    private fun downloadIndex(
        registryUrl: String,
        coordinate: CentralRepositoryCoordinate
    ): Result<List<CentralRepositoryIndexEntry>> = runCatching {
        val indexText = httpClient.getText(indexUrl(registryUrl, coordinate)).getOrThrow()
        indexText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> json.decodeFromString(CentralRepositoryIndexEntry.serializer(), line) }
            .toList()
    }

    private fun CentralRepositoryIndexEntry.matches(coordinate: CentralRepositoryCoordinate): Boolean {
        val normalizedOrganization = organization?.takeIf { it.isNotBlank() }
        return name == coordinate.module && normalizedOrganization == coordinate.organization
    }

    private fun replaceDirectoryContent(sourceRoot: Path, targetDir: Path) {
        targetDir.toFile().deleteRecursively()
        Files.createDirectories(targetDir)

        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { it != sourceRoot }
                .forEach { source ->
                    val relativePath = sourceRoot.relativize(source)
                    val target = targetDir.resolve(relativePath).normalize()
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
        }
    }

    private fun Path.normalizePackageRoot(): Path {
        if (Files.exists(resolve("cjpm.toml"))) {
            return this
        }

        val children = Files.list(this).use { stream -> stream.toList() }
        val onlyDirectory = children.singleOrNull()?.takeIf { Files.isDirectory(it) }
        if (onlyDirectory != null && Files.exists(onlyDirectory.resolve("cjpm.toml"))) {
            return onlyDirectory
        }

        return this
    }
}

data class CentralRepositoryPackage(
    val coordinate: CentralRepositoryCoordinate,
    val version: String,
    val registryUrl: String,
    val sha256sum: String
)

data class CentralRepositoryCoordinate(
    val organization: String?,
    val module: String
) {
    val displayName: String = if (organization == null) module else "$organization::$module"
    val cacheName: String = if (organization == null) module else "${organization}__${module}"

    fun organizationQuery(): String {
        return organization?.let { "?organization=${it.encodeQueryParam()}" } ?: ""
    }

    companion object {
        fun parse(dependencyName: String): CentralRepositoryCoordinate {
            val parts = dependencyName.split("::")
            return when (parts.size) {
                1 -> CentralRepositoryCoordinate(null, parts[0].requireArtifactName("制品名"))
                2 -> CentralRepositoryCoordinate(
                    parts[0].requireArtifactName("组织名"),
                    parts[1].requireArtifactName("制品名")
                )
                else -> throw IllegalArgumentException("Invalid central repository dependency name: $dependencyName")
            }
        }

        private fun String.requireArtifactName(label: String): String {
            val value = trim()
            require(value.isNotEmpty()) { "$label 不能为空" }
            return value
        }
    }
}

@Serializable
data class CentralRepositoryIndexEntry(
    val organization: String? = null,
    val name: String,
    val version: String,
    @SerialName("cjc-version")
    val cjcVersion: String? = null,
    val dependencies: List<CentralRepositoryIndexDependency> = emptyList(),
    @SerialName("test-dependencies")
    val testDependencies: List<CentralRepositoryIndexDependency> = emptyList(),
    @SerialName("script-dependencies")
    val scriptDependencies: List<CentralRepositoryIndexDependency> = emptyList(),
    val sha256sum: String? = null,
    val yanked: Boolean = false,
    @SerialName("index-version")
    @Serializable(with = FlexibleIntSerializer::class)
    val indexVersion: Int = 1
)

@Serializable
data class CentralRepositoryIndexDependency(
    val name: String,
    val require: String,
    val target: String? = null,
    val type: String? = null,
    @SerialName("output-type")
    val outputType: String? = null
)

private sealed interface VersionRequirement {
    fun matches(version: SemanticVersion): Boolean

    fun select(entries: List<CentralRepositoryIndexEntry>): CentralRepositoryIndexEntry? {
        return entries
            .mapNotNull { entry -> SemanticVersion.parseOrNull(entry.version)?.let { it to entry } }
            .filter { (version, _) -> matches(version) }
            .maxByOrNull { (version, _) -> version }
            ?.second
    }

    private data class Exact(val version: SemanticVersion) : VersionRequirement {
        override fun matches(version: SemanticVersion): Boolean = version == this.version
    }

    private object Latest : VersionRequirement {
        override fun matches(version: SemanticVersion): Boolean = true
    }

    private data class RangeSet(val ranges: List<VersionRequirement>) : VersionRequirement {
        override fun matches(version: SemanticVersion): Boolean = ranges.any { it.matches(version) }
    }

    private data class Range(
        val lower: Bound?,
        val upper: Bound?
    ) : VersionRequirement {
        override fun matches(version: SemanticVersion): Boolean {
            val lowerMatches = lower?.containsLower(version) ?: true
            val upperMatches = upper?.containsUpper(version) ?: true
            return lowerMatches && upperMatches
        }
    }

    private data class Bound(
        val version: SemanticVersion,
        val inclusive: Boolean
    ) {
        fun containsLower(candidate: SemanticVersion): Boolean {
            val comparison = candidate.compareTo(version)
            return comparison > 0 || (inclusive && comparison == 0)
        }

        fun containsUpper(candidate: SemanticVersion): Boolean {
            val comparison = candidate.compareTo(version)
            return comparison < 0 || (inclusive && comparison == 0)
        }
    }

    companion object {
        fun parse(text: String): VersionRequirement {
            val requirement = text.trim()
            require(requirement.isNotEmpty()) { "Version requirement is empty" }

            if (requirement.equals("latest", ignoreCase = true)) {
                return Latest
            }

            val tokens = splitRequirement(requirement)
            val requirements = tokens.map { parseToken(it) }
            return if (requirements.size == 1) requirements.single() else RangeSet(requirements)
        }

        private fun parseToken(token: String): VersionRequirement {
            val trimmed = token.trim()
            if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
                return parseRange(trimmed)
            }
            return Exact(SemanticVersion.parse(trimmed))
        }

        private fun parseRange(token: String): VersionRequirement {
            require(token.length >= 3) { "Invalid version range: $token" }

            val lowerInclusive = when (token.first()) {
                '[' -> true
                '(' -> false
                else -> throw IllegalArgumentException("Invalid range lower bound: $token")
            }
            val upperInclusive = when (token.last()) {
                ']' -> true
                ')' -> false
                else -> throw IllegalArgumentException("Invalid range upper bound: $token")
            }

            val content = token.substring(1, token.lastIndex)
            val commaIndex = content.indexOf(',')
            require(commaIndex >= 0) { "Invalid version range: $token" }

            val lowerText = content.substring(0, commaIndex).trim()
            val upperText = content.substring(commaIndex + 1).trim()

            require(lowerText.isNotEmpty() || !lowerInclusive) { "Open lower bound must use '(' in $token" }
            require(upperText.isNotEmpty() || !upperInclusive) { "Open upper bound must use ')' in $token" }

            val lower = lowerText.takeIf { it.isNotEmpty() }
                ?.let { Bound(SemanticVersion.parse(it), lowerInclusive) }
            val upper = upperText.takeIf { it.isNotEmpty() }
                ?.let { Bound(SemanticVersion.parse(it), upperInclusive) }

            return Range(lower, upper)
        }

        private fun splitRequirement(requirement: String): List<String> {
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var rangeDepth = 0

            for (char in requirement) {
                when (char) {
                    '[', '(' -> {
                        rangeDepth++
                        current.append(char)
                    }
                    ']', ')' -> {
                        rangeDepth--
                        require(rangeDepth >= 0) { "Invalid version requirement: $requirement" }
                        current.append(char)
                    }
                    ',' -> {
                        if (rangeDepth == 0) {
                            tokens.add(current.toString().trim())
                            current.clear()
                        } else {
                            current.append(char)
                        }
                    }
                    else -> current.append(char)
                }
            }

            require(rangeDepth == 0) { "Invalid version requirement: $requirement" }
            tokens.add(current.toString().trim())
            return tokens.filter { it.isNotEmpty() }
        }
    }
}

private data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        return compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
    }

    companion object {
        private val pattern = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parse(text: String): SemanticVersion {
            return parseOrNull(text) ?: throw IllegalArgumentException("Invalid central repository version: $text")
        }

        fun parseOrNull(text: String): SemanticVersion? {
            val match = pattern.matchEntire(text.trim()) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt()
            )
        }
    }
}

/**
 * 中心仓线上索引历史上同时出现过数字和字符串形式的版本号。
 */
private object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder is JsonDecoder) {
            val primitive = decoder.decodeJsonElement().jsonPrimitive
            return primitive.intOrNull ?: primitive.content.toInt()
        }

        return decoder.decodeInt()
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

object CentralRepositoryDefaults {
    const val DEFAULT_REGISTRY_URL: String = "https://pkg.cangjie-lang.cn/registry"
}

/**
 * 中心仓缓存目录布局。
 *
 * 同名同版本制品可能来自不同中心仓或私有仓，因此缓存目录必须包含 registry 来源维度。
 */
object RegistryCacheLayout {
    fun registryScope(registryUrl: String): String {
        val normalized = registryUrl.normalizeRegistryUrl()
        val host = runCatching { URI(normalized).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "registry"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return "${host.sanitizePathSegment()}-$digest"
    }
}

object RegistryUrlResolver {
    fun resolve(registryName: String?, registry: RegistryConfig?): List<String> {
        return when (registryName) {
            null, "default" -> buildList {
                add(registry?.default ?: CentralRepositoryDefaults.DEFAULT_REGISTRY_URL)
                registry?.mirrors?.let { addAll(it) }
            }.distinct()
            "private" -> listOf(
                registry?.privateUrl ?: throw IllegalArgumentException("Private registry URL not configured")
            )
            else -> listOf(registryName)
        }
    }
}

internal fun Throwable.isRegistryNotFoundFailure(): Boolean {
    val message = message ?: return false
    return message.contains("HTTP 404") || message.contains("Dependency not found in registry")
}

private fun String.normalizeRegistryUrl(): String = trim().trimEnd('/')

private fun String.sanitizePathSegment(): String = replace(Regex("[^a-zA-Z0-9._-]"), "_")

private fun String.encodePathSegment(): String = encodeQueryParam()

private fun String.encodeQueryParam(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")
}

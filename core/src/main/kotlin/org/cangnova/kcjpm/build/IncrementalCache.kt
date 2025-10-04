package org.cangnova.kcjpm.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.*

@Serializable
data class CompilationCache(
    val packages: Map<String, PackageCacheEntry> = emptyMap(),
    val version: Int = CACHE_VERSION
) {
    companion object {
        const val CACHE_VERSION = 1
        const val CACHE_FILE_NAME = "kcjpm-cache.json"
    }
}

@Serializable
data class PackageCacheEntry(
    val packageName: String,
    val packageRoot: String,
    val outputPath: String,
    val sourceFiles: Map<String, FileMetadata>,
    val compilationTimestamp: Long,
    val buildConfigHash: String
)

@Serializable
data class FileMetadata(
    val path: String,
    val lastModified: Long,
    val contentHash: String,
    val size: Long
)

class IncrementalCacheManager(
    private val cacheDir: Path
) {
    private val cacheFile = cacheDir.resolve(CompilationCache.CACHE_FILE_NAME)
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.createDirectories()
        }
    }

    fun loadCache(): CompilationCache = runCatching {
        if (cacheFile.exists()) {
            json.decodeFromString<CompilationCache>(cacheFile.readText())
        } else {
            CompilationCache()
        }
    }.getOrElse { CompilationCache() }

    fun saveCache(cache: CompilationCache): Result<Unit> = runCatching {
        cacheFile.writeText(json.encodeToString(CompilationCache.serializer(), cache))
    }

    fun updatePackageCache(
        cache: CompilationCache,
        packageInfo: PackageInfo,
        outputPath: Path,
        buildConfigHash: String
    ): CompilationCache {
        val sourceFileMetadata = packageInfo.sourceFiles.associate { sourceFile ->
            sourceFile.toString() to FileMetadata(
                path = sourceFile.toString(),
                lastModified = sourceFile.getLastModifiedTime().toMillis(),
                contentHash = computeFileHash(sourceFile),
                size = sourceFile.fileSize()
            )
        }

        val packageEntry = PackageCacheEntry(
            packageName = packageInfo.name,
            packageRoot = packageInfo.packageRoot.toString(),
            outputPath = outputPath.toString(),
            sourceFiles = sourceFileMetadata,
            compilationTimestamp = System.currentTimeMillis(),
            buildConfigHash = buildConfigHash
        )

        val updatedPackages = cache.packages + (packageInfo.name to packageEntry)
        return cache.copy(packages = updatedPackages)
    }

    fun clearCache(): Result<Unit> = runCatching {
        if (cacheFile.exists()) {
            cacheFile.deleteExisting()
        }
    }
}

class FileChangeDetector {
    fun detectChanges(
        packageInfo: PackageInfo,
        cachedEntry: PackageCacheEntry?,
        currentBuildConfigHash: String
    ): ChangeDetectionResult {
        if (cachedEntry == null) {
            return ChangeDetectionResult.NoCacheFound
        }

        if (cachedEntry.buildConfigHash != currentBuildConfigHash) {
            return ChangeDetectionResult.BuildConfigChanged
        }

        val currentFiles = packageInfo.sourceFiles.toSet()
        val cachedFiles = cachedEntry.sourceFiles.keys.map { Paths.get(it) }.toSet()

        if (currentFiles != cachedFiles) {
            return ChangeDetectionResult.FilesChanged(
                added = currentFiles - cachedFiles,
                removed = cachedFiles - currentFiles
            )
        }

        val modifiedFiles = currentFiles.filter { sourceFile ->
            val cached = cachedEntry.sourceFiles[sourceFile.toString()] ?: return@filter true
            !isFileUnchanged(sourceFile, cached)
        }

        if (modifiedFiles.isNotEmpty()) {
            return ChangeDetectionResult.FilesChanged(
                modified = modifiedFiles.toSet()
            )
        }

        val outputPath = Paths.get(cachedEntry.outputPath)
        if (!outputPath.exists()) {
            return ChangeDetectionResult.OutputMissing
        }

        return ChangeDetectionResult.NoChanges
    }

    private fun isFileUnchanged(file: Path, metadata: FileMetadata): Boolean {
        if (!file.exists()) return false
        
        if (file.fileSize() != metadata.size) return false
        
        val currentModified = file.getLastModifiedTime().toMillis()
        if (currentModified != metadata.lastModified) {
            return computeFileHash(file) == metadata.contentHash
        }
        
        return true
    }
}

sealed interface ChangeDetectionResult {
    data object NoCacheFound : ChangeDetectionResult
    data object BuildConfigChanged : ChangeDetectionResult
    data object OutputMissing : ChangeDetectionResult
    data object NoChanges : ChangeDetectionResult
    data class FilesChanged(
        val added: Set<Path> = emptySet(),
        val removed: Set<Path> = emptySet(),
        val modified: Set<Path> = emptySet()
    ) : ChangeDetectionResult
}

fun computeFileHash(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun computeBuildConfigHash(buildConfig: BuildConfig): String {
    val configString = buildConfig.toString()
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(configString.toByteArray())
    return digest.digest().joinToString("") { "%02x".format(it) }
}
package org.cangnova.kcjpm.lock

import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.*
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

interface LockFileSerializer {
    fun serialize(lockFile: LockFile): String
    fun deserialize(content: String): Result<LockFile>
}

class TomlLockFileSerializer : LockFileSerializer {
    
    override fun serialize(lockFile: LockFile): String {
        val root = buildTomlTable {
            literal("version", lockFile.version.toLong())
            
            table("metadata") {
                literal("generated-at", lockFile.metadata.generatedAt.toString())
                literal("kcjpm-version", lockFile.metadata.kcjpmVersion)
            }
            
            array("package") {
                lockFile.packages.forEach { pkg ->
                    table {
                        literal("name", pkg.name)
                        literal("version", pkg.version)
                        literal("source", pkg.source.toSourceString())
                        pkg.checksum?.let { literal("checksum", it) }
                        if (pkg.dependencies.isNotEmpty()) {
                            array("dependencies") {
                                pkg.dependencies.forEach { literal(it) }
                            }
                        }
                    }
                }
            }
        }
        
        return Toml.encodeToString(TomlTable.serializer(), root)
    }
    
    override fun deserialize(content: String): Result<LockFile> = runCatching {
        val root = Toml.decodeFromString(TomlTable.serializer(), content)
        
        val version = (root["version"] as? TomlLiteral)?.toInt() ?: 1
        
        val metadataTable = root["metadata"] as? TomlTable
            ?: throw IllegalArgumentException("Missing metadata section")
        val generatedAtStr = (metadataTable["generated-at"] as? TomlLiteral)?.content as? String
            ?: throw IllegalArgumentException("Missing generated-at in metadata")
        val generatedAt = Instant.parse(generatedAtStr)
        val kcjpmVersion = (metadataTable["kcjpm-version"] as? TomlLiteral)?.content as? String
            ?: throw IllegalArgumentException("Missing kcjpm-version in metadata")
        
        val metadata = LockMetadata(generatedAt, kcjpmVersion)
        
        val packagesArray = root["package"] as? TomlArray
            ?: throw IllegalArgumentException("Missing package section")
        
        val packages = packagesArray.content.mapNotNull { element ->
            val pkgTable = element as? TomlTable ?: return@mapNotNull null
            
            val name = (pkgTable["name"] as? TomlLiteral)?.content as? String ?: return@mapNotNull null
            val version = (pkgTable["version"] as? TomlLiteral)?.content as? String ?: return@mapNotNull null
            val sourceString = (pkgTable["source"] as? TomlLiteral)?.content as? String ?: return@mapNotNull null
            val source = PackageSource.parse(sourceString) ?: return@mapNotNull null
            val checksum = (pkgTable["checksum"] as? TomlLiteral)?.content as? String
            val dependencies = (pkgTable["dependencies"] as? TomlArray)?.content?.mapNotNull { 
                (it as? TomlLiteral)?.content as? String
            } ?: emptyList()
            
            LockedPackage(name, version, source, checksum, dependencies)
        }
        
        LockFile(version, metadata, packages)
    }
}

object LockFileIO {
    private const val LOCK_FILE_NAME = "kcjpm.lock"
    
    fun read(projectRoot: Path, serializer: LockFileSerializer = TomlLockFileSerializer()): Result<LockFile> {
        return runCatching {
            val lockFilePath = projectRoot.resolve(LOCK_FILE_NAME)
            val content = lockFilePath.readText()
            serializer.deserialize(content).getOrThrow()
        }
    }
    
    fun write(
        projectRoot: Path, 
        lockFile: LockFile,
        serializer: LockFileSerializer = TomlLockFileSerializer()
    ): Result<Unit> {
        return runCatching {
            val lockFilePath = projectRoot.resolve(LOCK_FILE_NAME)
            val content = serializer.serialize(lockFile)
            lockFilePath.writeText(content)
        }
    }
    
    fun exists(projectRoot: Path): Boolean {
        return projectRoot.resolve(LOCK_FILE_NAME).toFile().exists()
    }
}
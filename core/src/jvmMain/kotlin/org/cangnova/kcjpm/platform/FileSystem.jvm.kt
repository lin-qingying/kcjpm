package org.cangnova.kcjpm.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

class JvmFileSystem : FileSystem {
    override fun exists(path: KPath): Boolean = Path.of(path.path).exists()
    
    override fun isDirectory(path: KPath): Boolean = Path.of(path.path).isDirectory()
    
    override fun isFile(path: KPath): Boolean = Path.of(path.path).isRegularFile()
    
    override fun createDirectory(path: KPath): Result<Unit> = runCatching {
        Files.createDirectory(Path.of(path.path))
    }
    
    override fun createDirectories(path: KPath): Result<Unit> = runCatching {
        Files.createDirectories(Path.of(path.path))
    }
    
    override fun delete(path: KPath): Result<Unit> = runCatching {
        Files.delete(Path.of(path.path))
    }
    
    override fun deleteRecursively(path: KPath): Result<Unit> = runCatching {
        Path.of(path.path).toFile().deleteRecursively()
    }
    
    override fun readText(path: KPath): Result<String> = runCatching {
        Path.of(path.path).readText()
    }
    
    override fun writeText(path: KPath, content: String): Result<Unit> = runCatching {
        Path.of(path.path).writeText(content)
    }
    
    override fun listFiles(path: KPath): Result<List<KPath>> = runCatching {
        Files.list(Path.of(path.path)).use { stream ->
            stream.map { KPath(it.toString()) }.toList()
        }
    }
    
    override fun copy(source: KPath, target: KPath): Result<Unit> = runCatching {
        Files.copy(Path.of(source.path), Path.of(target.path), StandardCopyOption.REPLACE_EXISTING)
    }
    
    override fun move(source: KPath, target: KPath): Result<Unit> = runCatching {
        Files.move(Path.of(source.path), Path.of(target.path), StandardCopyOption.REPLACE_EXISTING)
    }
    
    override fun absolutePath(path: KPath): KPath = 
        KPath(Path.of(path.path).toAbsolutePath().toString())
    
    override fun normalize(path: KPath): KPath = 
        KPath(Path.of(path.path).normalize().toString())
    
    override fun workingDirectory(): KPath = 
        KPath(System.getProperty("user.dir"))
    
    override fun tempDirectory(): KPath = 
        KPath(System.getProperty("java.io.tmpdir"))
}

actual fun getFileSystem(): FileSystem = JvmFileSystem()

fun KPath.toJavaPath(): Path = Path.of(this.path)

fun Path.toKPath(): KPath = KPath(this.toString().replace('\\', '/'))
package org.cangnova.kcjpm.platform

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
class NativeFileSystem : FileSystem {
    override fun exists(path: KPath): Boolean {
        return access(path.path, F_OK) == 0
    }
    
    override fun isDirectory(path: KPath): Boolean {
        memScoped {
            val statBuf = alloc<stat>()
            if (stat(path.path, statBuf.ptr) != 0) {
                return false
            }
            return (statBuf.st_mode.toInt() and S_IFDIR) != 0
        }
    }
    
    override fun isFile(path: KPath): Boolean {
        memScoped {
            val statBuf = alloc<stat>()
            if (stat(path.path, statBuf.ptr) != 0) {
                return false
            }
            return (statBuf.st_mode.toInt() and S_IFREG) != 0
        }
    }
    
    override fun createDirectory(path: KPath): Result<Unit> = runCatching {
        TODO("createDirectory not yet implemented for Native")
    }
    
    override fun createDirectories(path: KPath): Result<Unit> = runCatching {
        TODO("createDirectories not yet implemented for Native")
    }
    
    override fun delete(path: KPath): Result<Unit> = runCatching {
        if (remove(path.path) != 0) {
            throw RuntimeException("Failed to delete: $path")
        }
    }
    
    override fun deleteRecursively(path: KPath): Result<Unit> = runCatching {
        TODO("deleteRecursively not yet implemented for Native")
    }
    
    override fun readText(path: KPath): Result<String> = runCatching {
        val file = fopen(path.path, "r") ?: throw RuntimeException("Failed to open file: $path")
        try {
            val buffer = StringBuilder()
            memScoped {
                val chunk = allocArray<ByteVar>(4096)
                while (true) {
                    val read = fgets(chunk, 4096, file)?.toKString()
                    if (read == null || read.isEmpty()) break
                    buffer.append(read)
                }
            }
            buffer.toString()
        } finally {
            fclose(file)
        }
    }
    
    override fun writeText(path: KPath, content: String): Result<Unit> = runCatching {
        val file = fopen(path.path, "w") ?: throw RuntimeException("Failed to open file: $path")
        try {
            fputs(content, file)
        } finally {
            fclose(file)
        }
    }
    
    override fun listFiles(path: KPath): Result<List<KPath>> = runCatching {
        TODO("listFiles not yet implemented for Native")
    }
    
    override fun copy(source: KPath, target: KPath): Result<Unit> = runCatching {
        val content = readText(source).getOrThrow()
        writeText(target, content).getOrThrow()
    }
    
    override fun move(source: KPath, target: KPath): Result<Unit> = runCatching {
        if (rename(source.path, target.path) != 0) {
            throw RuntimeException("Failed to move: $source to $target")
        }
    }
    
    override fun absolutePath(path: KPath): KPath {
        return if (path.path.startsWith("/")) {
            path
        } else {
            val cwd = workingDirectory()
            cwd.resolve(path.path)
        }
    }
    
    override fun normalize(path: KPath): KPath {
        val parts = path.path.split("/").toMutableList()
        val normalized = mutableListOf<String>()
        
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (normalized.isNotEmpty() && normalized.last() != "..") {
                    normalized.removeAt(normalized.lastIndex)
                } else {
                    normalized.add(part)
                }
                else -> normalized.add(part)
            }
        }
        
        val result = normalized.joinToString("/")
        return KPath(if (path.path.startsWith("/")) "/$result" else result)
    }
    
    override fun workingDirectory(): KPath {
        return KPath(".")
    }
    
    override fun tempDirectory(): KPath {
        return KPath("/tmp")
    }
}

actual fun getFileSystem(): FileSystem = NativeFileSystem()
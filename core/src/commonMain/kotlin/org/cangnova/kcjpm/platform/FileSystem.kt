package org.cangnova.kcjpm.platform

data class KPath(val path: String) {
    fun resolve(other: String): KPath = KPath("$path/$other")
    fun resolve(other: KPath): KPath = resolve(other.path)
    fun parent(): KPath? {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash > 0) KPath(path.take(lastSlash)) else null
    }
    
    override fun toString(): String = path
    
    companion object {
        fun of(vararg parts: String): KPath {
            return KPath(parts.joinToString("/"))
        }
    }
}

interface FileSystem {
    fun exists(path: KPath): Boolean
    fun isDirectory(path: KPath): Boolean
    fun isFile(path: KPath): Boolean
    fun createDirectory(path: KPath): Result<Unit>
    fun createDirectories(path: KPath): Result<Unit>
    fun delete(path: KPath): Result<Unit>
    fun deleteRecursively(path: KPath): Result<Unit>
    fun readText(path: KPath): Result<String>
    fun writeText(path: KPath, content: String): Result<Unit>
    fun listFiles(path: KPath): Result<List<KPath>>
    fun copy(source: KPath, target: KPath): Result<Unit>
    fun move(source: KPath, target: KPath): Result<Unit>
    fun absolutePath(path: KPath): KPath
    fun normalize(path: KPath): KPath
    fun workingDirectory(): KPath
    fun tempDirectory(): KPath
}

expect fun getFileSystem(): FileSystem
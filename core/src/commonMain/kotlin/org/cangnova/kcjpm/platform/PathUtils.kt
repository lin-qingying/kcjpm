package org.cangnova.kcjpm.platform

object PathUtils {
    fun String.toKPath(): KPath = KPath(this.replace('\\', '/'))
    
    fun joinPath(vararg parts: String): String {
        return parts.joinToString("/")
    }
    
    fun resolvePath(base: String, relative: String): String {
        val normalizedBase = base.replace('\\', '/')
        val normalizedRelative = relative.replace('\\', '/')
        return if (normalizedBase.endsWith('/')) {
            "$normalizedBase$normalizedRelative"
        } else {
            "$normalizedBase/$normalizedRelative"
        }
    }
    
    fun normalizePath(path: String): String {
        return path.replace('\\', '/')
    }
}
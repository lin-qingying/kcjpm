package org.cangnova.kcjpm.platform

import java.nio.file.Path as JavaPath

object PathCompat {
    fun resolve(base: JavaPath, other: String): JavaPath = base.resolve(other)
    
    fun resolve(base: KPath, other: String): KPath = base.resolve(other)
    
    fun toAbsolutePath(path: JavaPath): JavaPath = path.toAbsolutePath()
    
    fun toAbsolutePath(path: KPath): KPath = getFileSystem().absolutePath(path)
}
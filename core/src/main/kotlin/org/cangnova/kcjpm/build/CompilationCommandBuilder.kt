package org.cangnova.kcjpm.build

import org.cangnova.kcjpm.sdk.SdkManager
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class CompilationCommandBuilder {
    
    private fun getCompilerCommand(): String {
        val sdk = SdkManager.default().getOrNull()
        return sdk?.getCompilerCommand() ?: "cjc"
    }

    /**
     * 构建完整的编译命令（自动检测包模式或文件模式）
     */
    context(ctx: CompilationContext)
    fun buildCommand(): List<String> = buildList {
        add(getCompilerCommand())

        ctx.sourceFiles.takeIf { it.isNotEmpty() }
            ?.let { files -> findPackageDirectory(files, ctx.projectRoot) }
            ?.also { packageDir ->
                add("--package")
                add(packageDir.toString())
            } ?: run {
            addAll(ctx.sourceFiles.map(Path::toString))
        }

        ctx.buildConfig.run {
            add("--output-type=${getOutputType()}")
            add("--output")
            add(ctx.outputPath.toString())

            addAll(optimizationLevel.toArgs())

            if (debugInfo) add("-g")

            if (parallel) {
                add("--jobs")
                add(maxParallelSize.toString())
            }

            if (incremental) {
                addAll(listOf("--experimental", "--incremental-compile"))
            }

            target?.let { addAll(listOf("--target", it.triple)) }

            if (verbose) add("--verbose")
        }

        addAll(ctx.dependencies.flatMap { it.toCommandArgs() })
    }
    
    /**
     * 为项目中的每个包构建编译命令
     * 
     * 返回多个命令，每个命令对应一个包的编译
     */
    context(ctx: CompilationContext)
    fun buildPackageCommands(): List<List<String>> {
        val packages = PackageDiscovery.discoverPackages().toList()
        val outputDir = ctx.outputPath.resolve("libs")
        val importPaths = listOf(ctx.outputPath)
        
        return packages.map { packageInfo ->
            buildPackageCommand(
                packageDir = packageInfo.packageRoot,
                outputDir = outputDir,
                outputFileName = "lib${packageInfo.name}.a",
                importPaths = importPaths,
                hasSubPackages = packageInfo.hasSubPackages
            )
        }
    }

    /**
     * 构建包编译命令（生成 .a 静态库）
     */
    context(ctx: CompilationContext)
    fun buildPackageCommand(
        packageDir: Path,
        outputDir: Path,
        outputFileName: String,
        importPaths: List<Path> = emptyList(),
        hasSubPackages: Boolean = true
    ): List<String> = buildList {
        add(getCompilerCommand())
        
        importPaths.forEach { importPath ->
            add("--import-path=${importPath}")
        }
        
        if (ctx.buildConfig.parallel) {
            add("-j${ctx.buildConfig.maxParallelSize}")
        }

        if (!hasSubPackages) {
            add("--no-sub-pkg")
        }

        add("-p")
        add(packageDir.toString())


        add("--output-dir=${outputDir}")
        add("--output-type=staticlib")
        add("-o=${outputFileName}")
        
        addAll(ctx.buildConfig.optimizationLevel.toArgs())

        if (ctx.buildConfig.debugInfo) add("-g")

        ctx.buildConfig.target?.let { addAll(listOf("--target", it.triple)) }
    }

    context(ctx: CompilationContext)
    fun buildExecutableCommand(
        mainFile: Path,
        libraryFiles: List<Path>,
        outputPath: Path
    ): List<String> = buildList {
        add(getCompilerCommand())
        add(mainFile.toString())
        addAll(libraryFiles.map(Path::toString))
        addAll(listOf("--output-type=exe", "--output", outputPath.toString()))
        addAll(ctx.buildConfig.optimizationLevel.toArgs())

        if (ctx.buildConfig.debugInfo) add("-g")

        ctx.buildConfig.target?.let { addAll(listOf("--target", it.triple)) }
    }

    private fun findPackageDirectory(sourceFiles: List<Path>, projectRoot: Path): Path? =
        sourceFiles.asSequence()
            .map { it.parent ?: projectRoot }
            .reduce(::findCommonParent)
            .takeIf(::hasCangjieFiles)

    private tailrec fun findCommonParent(path1: Path, path2: Path): Path {
        val normalized1 = path1.normalize()
        val normalized2 = path2.normalize()

        return when {
            normalized2.startsWith(normalized1) -> normalized1
            else -> {
                val parent = normalized1.parent ?: return normalized1.root
                findCommonParent(parent, normalized2)
            }
        }
    }

    private fun hasCangjieFiles(directory: Path): Boolean = runCatching {
        directory.listDirectoryEntries()
            .any { it.extension == "cj" }
    }.getOrDefault(false)

    context(ctx: CompilationContext)
    private fun getOutputType(): String =
        when (ctx.outputType) {
            org.cangnova.kcjpm.config.OutputType.EXECUTABLE -> "exe"
            org.cangnova.kcjpm.config.OutputType.LIBRARY,
            org.cangnova.kcjpm.config.OutputType.STATIC_LIBRARY -> "staticlib"
            org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY -> "dylib"
        }
}

private fun OptimizationLevel.toArgs(): List<String> = when (this) {
    OptimizationLevel.DEBUG -> listOf("-O0")
    OptimizationLevel.RELEASE -> listOf("-O2")
    OptimizationLevel.SIZE -> listOf("-Os")
    OptimizationLevel.SPEED -> listOf("-O3")
}

private fun Dependency.toCommandArgs(): List<String> = when (this) {
    is Dependency.PathDependency -> {
        val pathStr = path.toString()
        if (pathStr.endsWith(".cjo") || pathStr.endsWith(".so") || pathStr.endsWith(".dylib") || pathStr.endsWith(".dll")) {
            listOf(pathStr)
        } else {
            listOf("--library-path", pathStr)
        }
    }
    is Dependency.GitDependency -> localPath?.let { path ->
        val pathStr = path.toString()
        if (pathStr.endsWith(".cjo")) {
            listOf(pathStr)
        } else {
            listOf("--library-path", pathStr)
        }
    } ?: emptyList()
    is Dependency.RegistryDependency -> localPath?.let { path ->
        listOf("--library-path", path.toString(), "--library", name)
    } ?: emptyList()
}


@CompilationDsl
class CompilationCommandDsl {
    private val args = mutableListOf<String>()

    fun command(name: String) = apply { args.add(name) }
    fun option(name: String, value: String? = null) = apply {
        args.add(name)
        value?.let { args.add(it) }
    }

    fun flag(name: String) = apply { args.add(name) }
    fun argument(value: String) = apply { args.add(value) }
    fun arguments(vararg values: String) = apply { args.addAll(values) }

    fun build(): List<String> = args.toList()
}

inline fun buildCommand(block: CompilationCommandDsl.() -> Unit): List<String> =
    CompilationCommandDsl().apply(block).build()

/**
 * 包发现器：从源文件中发现和组织包结构
 */
object PackageDiscovery {

    /**
     * 发现项目中的所有包
     * 
     * 通过分析源文件的 package 声明和目录结构，将文件分组为包
     */
    context(ctx: CompilationContext)
    fun discoverPackages(): Sequence<PackageInfo> =
        ctx.sourceFiles
            .groupBy { sourceFile -> findPackageRoot(sourceFile, ctx.projectRoot) }
            .asSequence()
            .mapNotNull { (packageRoot, files) ->
                packageRoot?.let { root ->
                    val packageName = extractPackageName(files.first())
                    PackageInfo(packageName, root, files)
                }
            }

    private tailrec fun findPackageRoot(
        sourceFile: Path,
        projectRoot: Path,
        current: Path? = sourceFile.parent
    ): Path? =
        when {
            current == null || current == projectRoot -> sourceFile.parent
            isPackageRoot(current) -> current
            else -> findPackageRoot(sourceFile, projectRoot, current.parent)
        }

    private fun isPackageRoot(directory: Path): Boolean = runCatching {
        val cjFiles = directory.listDirectoryEntries("*.cj")

        if (cjFiles.isEmpty()) return false

        val firstPackage = extractPackageName(cjFiles.first())
        cjFiles.all { extractPackageName(it) == firstPackage }
    }.getOrDefault(false)

    private fun extractPackageName(sourceFile: Path): String = runCatching {
        val packageRegex = Regex("""^package\s+(\w+(?:\.\w+)*)""")
        sourceFile.readText()
            .lineSequence()
            .firstNotNullOfOrNull { line ->
                packageRegex.find(line.trim())?.groupValues?.get(1)
            } ?: "main"
    }.getOrDefault("main")
}

/**
 * 包信息数据类
 * 
 * @property name 包名（从 package 声明提取）
 * @property packageRoot 包根目录
 * @property sourceFiles 包内所有源文件
 */
data class PackageInfo(
    val name: String,
    val packageRoot: Path,
    val sourceFiles: List<Path>
) {
    val isMainPackage: Boolean get() = name == "main"
    val hasMainFunction: Boolean get() = sourceFiles.any { it.containsMainFunction() }
    val hasSubPackages: Boolean get() = runCatching {
        packageRoot.listDirectoryEntries()
            .any { it.toFile().isDirectory && it.listDirectoryEntries("*.cj").isNotEmpty() }
    }.getOrDefault(false)
}

private fun Path.containsMainFunction(): Boolean = runCatching {
    readText().contains(Regex("""main\s*\(\s*\)\s*\{"""))
}.getOrDefault(false)

context(ctx: CompilationContext)
fun buildCompilationCommand(): List<String> = 
    CompilationCommandBuilder().buildCommand()

/**
 * 依赖收集器：收集项目和依赖的所有库文件
 */
object DependencyCollector {

    /**
     * 收集所有需要链接的库文件
     * 
     * 支持的格式：
     * - .cjo：仓颇静态库
     * - lib*.so：Linux 动态库
     * - lib*.dylib：macOS 动态库
     * - lib*.dll：Windows 动态库
     */
    context(ctx: CompilationContext)
    fun collectLibraryFiles(): List<Path> =
        ctx.run {
            val projectLibs = sourceFiles.asSequence()
                .mapNotNull { it.parent?.resolve("target") }
                .filter { it.exists() }
                .flatMap { targetDir ->
                    listOf(
                        targetDir.listDirectoryEntries("*.cjo"),
                        targetDir.listDirectoryEntries("lib*.so"),
                        targetDir.listDirectoryEntries("lib*.dylib"),
                        targetDir.listDirectoryEntries("lib*.dll")
                    ).flatten()
                }
                .toList()

            val dependencyLibs = dependencies.flatMap { dependency ->
                when (dependency) {
                    is Dependency.PathDependency -> {
                        val pathStr = dependency.path.toString()
                        if (pathStr.endsWith(".cjo") || pathStr.endsWith(".so") || 
                            pathStr.endsWith(".dylib") || pathStr.endsWith(".dll")) {
                            listOf(dependency.path)
                        } else {
                            emptyList()
                        }
                    }

                    is Dependency.GitDependency -> {
                        dependency.localPath?.let { localPath ->
                            buildList {
                                val cjoFile = localPath.resolve("${dependency.name}.cjo")
                                if (cjoFile.exists()) add(cjoFile)
                                
                                val soFile = localPath.resolve("lib${dependency.name}.so")
                                if (soFile.exists()) add(soFile)
                                
                                val dylibFile = localPath.resolve("lib${dependency.name}.dylib")
                                if (dylibFile.exists()) add(dylibFile)
                                
                                val dllFile = localPath.resolve("lib${dependency.name}.dll")
                                if (dllFile.exists()) add(dllFile)
                            }
                        } ?: emptyList()
                    }

                    is Dependency.RegistryDependency -> {
                        dependency.localPath?.let { localPath ->
                            buildList {
                                val cjoFile = localPath.resolve("${dependency.name}.cjo")
                                if (cjoFile.exists()) add(cjoFile)
                                
                                val soFile = localPath.resolve("lib${dependency.name}.so")
                                if (soFile.exists()) add(soFile)
                                
                                val dylibFile = localPath.resolve("lib${dependency.name}.dylib")
                                if (dylibFile.exists()) add(dylibFile)
                                
                                val dllFile = localPath.resolve("lib${dependency.name}.dll")
                                if (dllFile.exists()) add(dllFile)
                            }
                        } ?: emptyList()
                    }
                }
            }

            projectLibs + dependencyLibs
        }
}

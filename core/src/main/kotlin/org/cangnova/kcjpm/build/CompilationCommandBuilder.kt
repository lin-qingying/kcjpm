package org.cangnova.kcjpm.build

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class CompilationCommandBuilder {

    context(ctx: CompilationContext)
    fun buildCommand(): List<String> = buildList {
        add("cjc")

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

            addAll(listOf("--target", target.triple))

            if (verbose) add("--verbose")
        }

        addAll(ctx.dependencies.flatMap { it.toCommandArgs() })
    }

    fun buildPackageCommand(
        packageDir: Path,
        outputPath: Path,
        buildConfig: BuildConfig,
        moduleName: String? = null
    ): List<String> = buildList {
        add("cjc")
        add("--package")
        add(packageDir.toString())

        moduleName?.let {
            addAll(listOf("--module-name", it))
        }

        addAll(listOf("--output-type=staticlib", "--output", outputPath.toString()))
        addAll(buildConfig.optimizationLevel.toArgs())

        if (buildConfig.debugInfo) add("-g")

        addAll(listOf("--target", buildConfig.target.triple))
    }

    fun buildExecutableCommand(
        mainFile: Path,
        libraryFiles: List<Path>,
        outputPath: Path,
        buildConfig: BuildConfig
    ): List<String> = buildList {
        add("cjc")
        add(mainFile.toString())
        addAll(libraryFiles.map(Path::toString))
        addAll(listOf("--output-type=exe", "--output", outputPath.toString()))
        addAll(buildConfig.optimizationLevel.toArgs())

        if (buildConfig.debugInfo) add("-g")

        addAll(listOf("--target", buildConfig.target.triple))
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
        ctx.buildConfig.optimizationLevel.let { level ->
            when (level) {
                OptimizationLevel.DEBUG -> "exe"
                else -> "exe"
            }
        }
}

private fun OptimizationLevel.toArgs(): List<String> = when (this) {
    OptimizationLevel.DEBUG -> listOf("-O0")
    OptimizationLevel.RELEASE -> listOf("-O2")
    OptimizationLevel.SIZE -> listOf("-Os")
    OptimizationLevel.SPEED -> listOf("-O3")
}

private fun Dependency.toCommandArgs(): List<String> = when (this) {
    is Dependency.PathDependency -> path.toCommandArgs()
    is Dependency.GitDependency -> localPath?.toCommandArgs() ?: emptyList()
    is Dependency.RegistryDependency -> localPath?.let { path ->
        listOf("--library-path", path.toString(), "--library", name)
    } ?: emptyList()
}

private fun Path.toCommandArgs(): List<String> = when {
    toString().endsWith(".a") || toString().endsWith(".so") -> listOf(toString())
    else -> listOf("--library-path", toString())
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

object PackageDiscovery {

    fun discoverPackages(sourceFiles: List<Path>, projectRoot: Path): Sequence<PackageInfo> =
        sourceFiles
            .groupBy { sourceFile -> findPackageRoot(sourceFile, projectRoot) }
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

data class PackageInfo(
    val name: String,
    val packageRoot: Path,
    val sourceFiles: List<Path>
) {
    val isMainPackage: Boolean get() = name == "main"
    val hasMainFunction: Boolean get() = sourceFiles.any { it.containsMainFunction() }
}

private fun Path.containsMainFunction(): Boolean = runCatching {
    readText().contains(Regex("""fun\s+main\s*\("""))
}.getOrDefault(false)

context(ctx: CompilationContext)
fun buildCompilationCommand(): List<String> = 
    CompilationCommandBuilder().buildCommand()

object DependencyCollector {

    fun collectLibraryFiles(context: CompilationContext): List<Path> =
        context.run {
            val projectLibs = sourceFiles.asSequence()
                .mapNotNull { it.parent?.resolve("target") }
                .filter { it.exists() }
                .flatMap { it.listDirectoryEntries("lib*.a") }
                .toList()

            val dependencyLibs = dependencies.mapNotNull { dependency ->
                when (dependency) {
                    is Dependency.PathDependency ->
                        dependency.path.takeIf { it.toString().endsWith(".a") }

                    is Dependency.GitDependency ->
                        dependency.localPath?.resolve("lib${dependency.name}.a")?.takeIf { it.exists() }

                    is Dependency.RegistryDependency ->
                        dependency.localPath?.resolve("lib${dependency.name}.a")?.takeIf { it.exists() }
                }
            }

            projectLibs + dependencyLibs
        }
}

private fun findPackageDirectory(sourceFiles: List<Path>, projectRoot: Path): Path? {
    if (sourceFiles.isEmpty()) return null

    val commonParent = sourceFiles
        .map { it.parent ?: projectRoot }
        .reduce { acc, path ->
            findCommonParent(acc, path)
        }

    return if (hasCangjieFiles(commonParent)) commonParent else null
}

private fun findCommonParent(path1: Path, path2: Path): Path {
    val normalized1 = path1.normalize()
    val normalized2 = path2.normalize()

    var common = normalized1
    while (!normalized2.startsWith(common)) {
        common = common.parent ?: return normalized1.root
    }
    return common
}

private fun hasCangjieFiles(directory: Path): Boolean {
    return try {
        directory.toFile().listFiles()
            ?.any { it.extension == "cj" } ?: false
    } catch (e: Exception) {
        false
    }
}

private fun getOutputType(buildConfig: BuildConfig): String {
    return when (buildConfig.optimizationLevel) {
        OptimizationLevel.DEBUG -> "exe"
        else -> "exe"
    }
}

private fun getOptimizationArgs(level: OptimizationLevel): List<String> {
    return when (level) {
        OptimizationLevel.DEBUG -> listOf("-O0")
        OptimizationLevel.RELEASE -> listOf("-O2")
        OptimizationLevel.SIZE -> listOf("-Os")
        OptimizationLevel.SPEED -> listOf("-O3")
    }
}

private fun getDependencyArgs(dependencies: List<Dependency>): List<String> {
    val args = mutableListOf<String>()

    dependencies.forEach { dependency ->
        when (dependency) {
                val libPath = dependency.path
                if (libPath.toString().endsWith(".a") || libPath.toString().endsWith(".so")) {
                    args.add(libPath.toString())
                    args.add("--library-path")
                    args.add(libPath.toString())
                }
            }

                dependency.localPath?.let { localPath ->
                    args.add("--library-path")
                    args.add(localPath.toString())

                    val libFile = localPath.resolve("lib${dependency.name}.a")
                    if (libFile.toFile().exists()) {
                        args.add(libFile.toString())
                    }
                }
            }

                dependency.localPath?.let { localPath ->
                    args.add("--library-path")
                    args.add(localPath.toString())

                    args.add("--library")
                    args.add(dependency.name)
                }
            }
        }
    }

    return args
}

fun buildPackageCommand(
    packageDir: Path,
    outputPath: Path,
    buildConfig: BuildConfig,
    moduleName: String? = null
): List<String> {
    val args = mutableListOf<String>()

    args.add("cjc")
    args.add("--package")
    args.add(packageDir.toString())

    moduleName?.let {
        args.add("--module-name")
        args.add(it)
    }

    args.add("--output-type=staticlib")
    args.add("--output")
    args.add(outputPath.toString())

    args.addAll(getOptimizationArgs(buildConfig.optimizationLevel))

    if (buildConfig.debugInfo) {
        args.add("-g")
    }

    args.add("--target")
    args.add(buildConfig.target.triple)

    return args
}

fun buildExecutableCommand(
    mainFile: Path,
    libraryFiles: List<Path>,
    outputPath: Path,
    buildConfig: BuildConfig
): List<String> {
    val args = mutableListOf<String>()

    args.add("cjc")
    args.add(mainFile.toString())

    libraryFiles.forEach { libFile ->
        args.add(libFile.toString())
    }

    args.add("--output-type=exe")
    args.add("--output")
    args.add(outputPath.toString())

    args.addAll(getOptimizationArgs(buildConfig.optimizationLevel))

    if (buildConfig.debugInfo) {
        args.add("-g")
    }

    args.add("--target")
    args.add(buildConfig.target.triple)

    return args
}
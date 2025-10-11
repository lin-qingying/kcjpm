package org.cangnova.kcjpm.cli.parser

sealed class Command {
    data class Build(
        val path: String = ".",
        val release: Boolean = false,
        val profile: String? = null,
        val target: String? = null,
        val noIncremental: Boolean = false
    ) : Command()
    
    data class Init(
        val path: String = ".",
        val name: String,
        val template: String = "executable",
        val lib: Boolean = false
    ) : Command()
    
    data class Clean(
        val path: String = "."
    ) : Command()
    
    data class Add(
        val path: String = ".",
        val dependency: String,
        val git: String? = null,
        val branch: String? = null,
        val tag: String? = null,
        val localPath: String? = null
    ) : Command()
    
    data class Update(
        val path: String = ".",
        val dependency: String? = null
    ) : Command()
    
    data class Run(
        val path: String = ".",
        val args: List<String> = emptyList()
    ) : Command()
    
    data object Help : Command()
    data object Version : Command()
}

data class GlobalOptions(
    val verbose: Boolean = false,
    val quiet: Boolean = false,
    val color: Boolean = true
)

sealed class ParseResult {
    data class Success(
        val command: Command,
        val options: GlobalOptions
    ) : ParseResult()
    
    data class Error(
        val message: String
    ) : ParseResult()
}

class CommandParser {
    fun parse(args: Array<String>): ParseResult {
        if (args.isEmpty()) {
            return ParseResult.Error("未指定命令。使用 --help 查看帮助")
        }
        
        val globalOptions = parseGlobalOptions(args)
        val commandArgs = args.filterNot { it.startsWith("--verbose") || it.startsWith("--quiet") || it.startsWith("--no-color") }
        
        val command = when (commandArgs.firstOrNull()) {
            "build", "b" -> parseBuildCommand(commandArgs)
            "init" -> parseInitCommand(commandArgs)
            "clean" -> parseCleanCommand(commandArgs)
            "add" -> parseAddCommand(commandArgs)
            "update" -> parseUpdateCommand(commandArgs)
            "run", "r" -> parseRunCommand(commandArgs)
            "help", "--help", "-h" -> Command.Help
            "version", "--version", "-v" -> Command.Version
            else -> return ParseResult.Error("未知命令: ${commandArgs.first()}。使用 --help 查看帮助")
        }
        
        return ParseResult.Success(command, globalOptions)
    }
    
    private fun parseGlobalOptions(args: Array<String>): GlobalOptions {
        return GlobalOptions(
            verbose = args.contains("--verbose"),
            quiet = args.contains("--quiet"),
            color = !args.contains("--no-color")
        )
    }
    
    private fun parseBuildCommand(args: List<String>): Command {
        var path = "."
        var release = false
        var profile: String? = null
        var target: String? = null
        var noIncremental = false
        
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--release", "-r" -> release = true
                "--profile" -> {
                    profile = args.getOrNull(++i)
                }
                "--target" -> {
                    target = args.getOrNull(++i)
                }
                "--no-incremental" -> noIncremental = true
                else -> {
                    if (!args[i].startsWith("--")) {
                        path = args[i]
                    }
                }
            }
            i++
        }
        
        return Command.Build(
            path = path,
            release = release,
            profile = profile,
            target = target,
            noIncremental = noIncremental
        )
    }
    
    private fun parseInitCommand(args: List<String>): Command {
        var path: String? = null
        var name: String? = null
        var template = "executable"
        var lib = false
        
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--name" -> {
                    name = args.getOrNull(++i)
                }
                "--template" -> {
                    template = args.getOrNull(++i) ?: template
                }
                "--lib" -> lib = true
                else -> {
                    if (!args[i].startsWith("--") && path == null) {
                        path = args[i]
                    }
                }
            }
            i++
        }
        
        val targetPath = path ?: "."
        val projectName = name ?: java.nio.file.Paths.get(targetPath).toAbsolutePath().fileName.toString()
        
        return Command.Init(
            path = targetPath,
            name = projectName,
            template = if (lib) "library" else template,
            lib = lib
        )
    }
    
    private fun parseCleanCommand(args: List<String>): Command {
        val path = args.getOrNull(1)?.takeIf { !it.startsWith("--") } ?: "."
        return Command.Clean(path = path)
    }
    
    private fun parseAddCommand(args: List<String>): Command {
        var path = "."
        var dependency: String? = null
        var git: String? = null
        var branch: String? = null
        var tag: String? = null
        var localPath: String? = null
        
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--git" -> {
                    git = args.getOrNull(++i)
                }
                "--branch" -> {
                    branch = args.getOrNull(++i)
                }
                "--tag" -> {
                    tag = args.getOrNull(++i)
                }
                "--path" -> {
                    localPath = args.getOrNull(++i)
                }
                else -> {
                    if (!args[i].startsWith("--") && dependency == null) {
                        dependency = args[i]
                    }
                }
            }
            i++
        }
        
        return Command.Add(
            path = path,
            dependency = dependency ?: throw IllegalArgumentException("必须指定依赖名称"),
            git = git,
            branch = branch,
            tag = tag,
            localPath = localPath
        )
    }
    
    private fun parseUpdateCommand(args: List<String>): Command {
        val path = "."
        val dependency = args.getOrNull(1)?.takeIf { !it.startsWith("--") }
        
        return Command.Update(
            path = path,
            dependency = dependency
        )
    }
    
    private fun parseRunCommand(args: List<String>): Command {
        val path = "."
        val runArgs = args.drop(1).dropWhile { it.startsWith("--") }
        
        return Command.Run(
            path = path,
            args = runArgs
        )
    }
}
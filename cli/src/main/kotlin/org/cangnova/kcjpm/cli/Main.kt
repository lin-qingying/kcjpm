package org.cangnova.kcjpm.cli

import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.cli.handler.*
import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.ConsoleOutputAdapter
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = runBlocking {
        try {
            KcjpmCli().execute(args)
        } catch (e: Exception) {
            System.err.println("Error: ${e.message ?: "Unknown error"}")
            e.printStackTrace()
            1
        }
    }
    exitProcess(exitCode)
}

class KcjpmCli {
    private var exitCode: Int = 0
    private lateinit var output: OutputAdapter
    private lateinit var globalOptions: GlobalOptions
    
    @OptIn(ExperimentalCli::class)
    fun execute(args: Array<String>): Int {
        // 处理全局版本显示（只有当-v或--version不跟子命令时）
        val subcommands = listOf("build", "init", "clean", "add", "update", "run")
        val hasSubcommand = args.any { it in subcommands }
        
        if ((args.contains("-v") || args.contains("--version")) && !hasSubcommand) {
            println("kcjpm 1.0.0")
            return 0
        }
        
        if (args.isEmpty()) {
            println(Messages.get("cli.usage"))
            println()
            println(Messages.get("cli.commands"))
            println("  build    ${Messages.get("cmd.build")}")
            println("  init     ${Messages.get("cmd.init")}")
            println("  clean    ${Messages.get("cmd.clean")}")
            println("  add      ${Messages.get("cmd.add")}")
            println("  update   ${Messages.get("cmd.update")}")
            println("  run      ${Messages.get("cmd.run")}")
            println()
            println(Messages.get("cli.options"))
            println("  -q, --quiet      ${Messages.get("opt.quiet")}")
            println("  --no-color       ${Messages.get("opt.noColor")}")
            println("  -v, --version    ${Messages.get("opt.version")}")
            println("  -h, --help       ${Messages.get("opt.help")}")
            println()
            println(Messages.get("cli.help.footer"))
            return 0
        }
        
        val parser = ArgParser("kcjpm", strictSubcommandOptionsOrder = true)
        
        val quiet by parser.option(ArgType.Boolean, shortName = "q", description = Messages.get("opt.quiet")).default(false)
        val noColor by parser.option(ArgType.Boolean, description = Messages.get("opt.noColor")).default(false)
        
        
        class BuildCommand : Subcommand("build", Messages.get("cmd.build")) {
            val path by argument(ArgType.String, description = Messages.get("build.arg.path")).optional().default(".")
            val release by option(ArgType.Boolean, shortName = "r", description = Messages.get("build.opt.release")).default(false)
            val profile by option(ArgType.String, description = Messages.get("build.opt.profile"))
            val target by option(ArgType.String, description = Messages.get("build.opt.target"))
            val noIncremental by option(ArgType.Boolean, description = Messages.get("build.opt.noIncremental")).default(false)
            val verboseMode by option(ArgType.Boolean, shortName = "v", fullName = "verbose", description = Messages.get("build.opt.verbose")).default(false)
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = verboseMode,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = verboseMode
                )
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Build(
                    path = path,
                    release = release,
                    profile = profile,
                    target = target,
                    noIncremental = noIncremental
                )
                exitCode = runBlocking {
                    BuildCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        class InitCommand : Subcommand("init", Messages.get("cmd.init")) {
            val path by argument(ArgType.String, description = Messages.get("init.arg.path")).optional().default(".")
            val projectName by option(ArgType.String, description = Messages.get("init.opt.name"))
            val template by option(ArgType.String, description = Messages.get("init.opt.template")).default("executable")
            val lib by option(ArgType.Boolean, description = Messages.get("init.opt.lib")).default(false)
            val verboseMode by option(ArgType.Boolean, shortName = "v", fullName = "verbose", description = Messages.get("init.opt.verbose")).default(false)
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = verboseMode,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = verboseMode
                )
                
                val name = projectName 
                    ?: java.nio.file.Paths.get(path).toAbsolutePath().fileName.toString()
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Init(
                    path = path,
                    name = name,
                    template = if (lib) "library" else template,
                    lib = lib
                )
                exitCode = runBlocking {
                    InitCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        class CleanCommand : Subcommand("clean", Messages.get("cmd.clean")) {
            val path by argument(ArgType.String, description = Messages.get("clean.arg.path")).optional().default(".")
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = false,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = false
                )
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Clean(path = path)
                exitCode = runBlocking {
                    CleanCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        class AddCommand : Subcommand("add", Messages.get("cmd.add")) {
            val dependency by argument(ArgType.String, description = Messages.get("add.arg.dependency"))
            val path by option(ArgType.String, description = Messages.get("add.arg.path")).default(".")
            val git by option(ArgType.String, description = Messages.get("add.opt.git"))
            val branch by option(ArgType.String, description = Messages.get("add.opt.branch"))
            val tag by option(ArgType.String, description = Messages.get("add.opt.tag"))
            val localPath by option(ArgType.String, description = Messages.get("add.opt.path"))
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = false,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = false
                )
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Add(
                    path = path,
                    dependency = dependency,
                    git = git,
                    branch = branch,
                    tag = tag,
                    localPath = localPath
                )
                exitCode = runBlocking {
                    AddCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        class UpdateCommand : Subcommand("update", Messages.get("cmd.update")) {
            val path by argument(ArgType.String, description = Messages.get("update.arg.path")).optional().default(".")
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = false,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = false
                )
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Update(
                    path = path,
                    dependency = null
                )
                exitCode = runBlocking {
                    UpdateCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        class RunCommand : Subcommand("run", Messages.get("cmd.run")) {
            val path by argument(ArgType.String, description = Messages.get("run.arg.path")).optional().default(".")
            val verboseMode by option(ArgType.Boolean, shortName = "v", fullName = "verbose", description = Messages.get("run.opt.verbose")).default(false)
            
            override fun execute() {
                globalOptions = GlobalOptions(
                    verbose = verboseMode,
                    quiet = quiet,
                    color = !noColor
                )
                output = ConsoleOutputAdapter(
                    useColors = !noColor,
                    showDebug = verboseMode
                )
                
                val command = org.cangnova.kcjpm.cli.parser.Command.Run(
                    path = path,
                    args = emptyList()
                )
                exitCode = runBlocking {
                    RunCommandHandler(output).handle(command, globalOptions)
                }
            }
        }
        
        parser.subcommands(
            BuildCommand(),
            InitCommand(),
            CleanCommand(),
            AddCommand(),
            UpdateCommand(),
            RunCommand()
        )
        
        parser.parse(args)
        
        return exitCode
    }
}
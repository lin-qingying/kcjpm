package org.cangnova.kcjpm.cli.i18n

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "中文")
}

object
Messages {
    private val messages = mutableMapOf<String, Map<Language, String>>()
    
    var currentLanguage: Language = Language.ENGLISH
        private set
    
    init {
        loadMessages()
        detectLanguage()
    }
    
    fun setLanguage(language: Language) {
        currentLanguage = language
    }
    
    fun get(key: String, vararg args: Any): String {
        val template = messages[key]?.get(currentLanguage) 
            ?: messages[key]?.get(Language.ENGLISH)
            ?: key
        
        return if (args.isEmpty()) {
            template
        } else {
            String.format(template, *args)
        }
    }
    
    private fun detectLanguage() {
        val osName = System.getProperty("os.name", "").lowercase()
        
        currentLanguage = if (osName.contains("win")) {
            Language.ENGLISH
        } else {
            val systemLang = System.getProperty("user.language", "en")
            when {
                systemLang.startsWith("zh") -> Language.CHINESE
                else -> Language.ENGLISH
            }
        }
    }
    
    private fun loadMessages() {
        messages["cli.usage"] = mapOf(
            Language.ENGLISH to "Usage: kcjpm <command> [options]",
            Language.CHINESE to "用法: kcjpm <命令> [选项]"
        )
        
        messages["cli.commands"] = mapOf(
            Language.ENGLISH to "Commands:",
            Language.CHINESE to "命令:"
        )
        
        messages["cli.options"] = mapOf(
            Language.ENGLISH to "Options:",
            Language.CHINESE to "选项:"
        )
        
        messages["cli.help.footer"] = mapOf(
            Language.ENGLISH to "Use 'kcjpm <command> --help' for more information on a command",
            Language.CHINESE to "使用 'kcjpm <命令> --help' 查看具体命令的帮助"
        )
        
        messages["cmd.build"] = mapOf(
            Language.ENGLISH to "Build the project",
            Language.CHINESE to "构建项目"
        )
        
        messages["cmd.init"] = mapOf(
            Language.ENGLISH to "Initialize a new project",
            Language.CHINESE to "初始化新项目"
        )
        
        messages["cmd.clean"] = mapOf(
            Language.ENGLISH to "Clean build outputs",
            Language.CHINESE to "清理构建输出"
        )
        
        messages["cmd.add"] = mapOf(
            Language.ENGLISH to "Add a dependency",
            Language.CHINESE to "添加依赖"
        )
        
        messages["cmd.update"] = mapOf(
            Language.ENGLISH to "Update dependencies",
            Language.CHINESE to "更新依赖"
        )
        
        messages["cmd.run"] = mapOf(
            Language.ENGLISH to "Run the project",
            Language.CHINESE to "运行项目"
        )
        
        messages["opt.verbose"] = mapOf(
            Language.ENGLISH to "Verbose output",
            Language.CHINESE to "详细输出"
        )
        
        messages["opt.quiet"] = mapOf(
            Language.ENGLISH to "Quiet mode",
            Language.CHINESE to "静默模式"
        )
        
        messages["opt.noColor"] = mapOf(
            Language.ENGLISH to "Disable colored output",
            Language.CHINESE to "禁用彩色输出"
        )
        
        messages["opt.help"] = mapOf(
            Language.ENGLISH to "Show help information",
            Language.CHINESE to "显示帮助信息"
        )
        
        messages["opt.version"] = mapOf(
            Language.ENGLISH to "Show version information",
            Language.CHINESE to "显示版本信息"
        )
        
        messages["build.opt.verbose"] = mapOf(
            Language.ENGLISH to "Show detailed compilation information",
            Language.CHINESE to "显示详细编译信息"
        )
        
        messages["init.opt.verbose"] = mapOf(
            Language.ENGLISH to "Show detailed initialization information",
            Language.CHINESE to "显示详细初始化信息"
        )
        
        messages["run.opt.verbose"] = mapOf(
            Language.ENGLISH to "Show detailed runtime information",
            Language.CHINESE to "显示详细运行信息"
        )
        
        messages["build.arg.path"] = mapOf(
            Language.ENGLISH to "Project path",
            Language.CHINESE to "项目路径"
        )
        
        messages["build.opt.release"] = mapOf(
            Language.ENGLISH to "Release build",
            Language.CHINESE to "发布构建"
        )
        
        messages["build.opt.profile"] = mapOf(
            Language.ENGLISH to "Build profile",
            Language.CHINESE to "构建配置文件"
        )
        
        messages["build.opt.target"] = mapOf(
            Language.ENGLISH to "Target platform",
            Language.CHINESE to "目标平台"
        )
        
        messages["build.opt.noIncremental"] = mapOf(
            Language.ENGLISH to "Disable incremental compilation",
            Language.CHINESE to "禁用增量编译"
        )
        
        messages["init.arg.path"] = mapOf(
            Language.ENGLISH to "Project path",
            Language.CHINESE to "项目路径"
        )
        
        messages["init.opt.name"] = mapOf(
            Language.ENGLISH to "Project name",
            Language.CHINESE to "项目名称"
        )
        
        messages["init.opt.template"] = mapOf(
            Language.ENGLISH to "Template name",
            Language.CHINESE to "模板名称"
        )
        
        messages["init.opt.lib"] = mapOf(
            Language.ENGLISH to "Create library project",
            Language.CHINESE to "创建库项目"
        )
        
        messages["clean.arg.path"] = mapOf(
            Language.ENGLISH to "Project path",
            Language.CHINESE to "项目路径"
        )
        
        messages["add.arg.dependency"] = mapOf(
            Language.ENGLISH to "Dependency name",
            Language.CHINESE to "依赖名称"
        )
        
        messages["add.opt.git"] = mapOf(
            Language.ENGLISH to "Git repository URL",
            Language.CHINESE to "Git 仓库 URL"
        )
        
        messages["add.opt.branch"] = mapOf(
            Language.ENGLISH to "Git branch",
            Language.CHINESE to "Git 分支"
        )
        
        messages["add.opt.tag"] = mapOf(
            Language.ENGLISH to "Git tag",
            Language.CHINESE to "Git 标签"
        )
        
        messages["add.opt.path"] = mapOf(
            Language.ENGLISH to "Local path",
            Language.CHINESE to "本地路径"
        )
        
        messages["update.arg.dependency"] = mapOf(
            Language.ENGLISH to "Dependency name",
            Language.CHINESE to "依赖名称"
        )
        
        messages["run.arg.path"] = mapOf(
            Language.ENGLISH to "Project path",
            Language.CHINESE to "项目路径"
        )
        
        messages["error.invalidCommand"] = mapOf(
            Language.ENGLISH to "Invalid command type",
            Language.CHINESE to "无效的命令类型"
        )
        
        messages["error.pathNotExist"] = mapOf(
            Language.ENGLISH to "Project path does not exist: %s",
            Language.CHINESE to "项目路径不存在: %s"
        )
        
        messages["build.starting"] = mapOf(
            Language.ENGLISH to "Starting build: %s",
            Language.CHINESE to "开始构建项目: %s"
        )
        
        messages["build.loadingConfig"] = mapOf(
            Language.ENGLISH to "Loading project configuration",
            Language.CHINESE to "加载项目配置"
        )
        
        messages["build.configLoaded"] = mapOf(
            Language.ENGLISH to "Configuration loaded",
            Language.CHINESE to "配置加载完成"
        )
        
        messages["build.project"] = mapOf(
            Language.ENGLISH to "Project: %s",
            Language.CHINESE to "项目: %s"
        )
        
        messages["build.version"] = mapOf(
            Language.ENGLISH to "Version: %s",
            Language.CHINESE to "版本: %s"
        )
        
        messages["build.resolvingDeps"] = mapOf(
            Language.ENGLISH to "Resolving dependencies",
            Language.CHINESE to "解析依赖"
        )
        
        messages["build.depsResolved"] = mapOf(
            Language.ENGLISH to "Dependencies resolved (%d dependencies)",
            Language.CHINESE to "依赖解析完成 (%d 个依赖)"
        )
        
        messages["build.building"] = mapOf(
            Language.ENGLISH to "Building project",
            Language.CHINESE to "构建项目"
        )
        
        messages["build.compiling"] = mapOf(
            Language.ENGLISH to "Compiling",
            Language.CHINESE to "编译完成"
        )
        
        messages["build.success"] = mapOf(
            Language.ENGLISH to "Build successful",
            Language.CHINESE to "构建成功"
        )
        
        messages["build.output"] = mapOf(
            Language.ENGLISH to "Output: %s",
            Language.CHINESE to "输出: %s"
        )
        
        messages["build.failed"] = mapOf(
            Language.ENGLISH to "Build failed: %s",
            Language.CHINESE to "构建失败: %s"
        )
        
        messages["build.compileFailed"] = mapOf(
            Language.ENGLISH to "Compilation failed: %s",
            Language.CHINESE to "编译失败: %s"
        )
        
        messages["build.report"] = mapOf(
            Language.ENGLISH to "Compilation report:",
            Language.CHINESE to "编译报告:"
        )
        
        messages["workspace.loading"] = mapOf(
            Language.ENGLISH to "Loading workspace",
            Language.CHINESE to "加载工作空间"
        )
        
        messages["workspace.loaded"] = mapOf(
            Language.ENGLISH to "Workspace loaded",
            Language.CHINESE to "工作空间加载完成"
        )
        
        messages["workspace.members"] = mapOf(
            Language.ENGLISH to "Workspace members: %s",
            Language.CHINESE to "工作空间成员: %s"
        )
        
        messages["workspace.building"] = mapOf(
            Language.ENGLISH to "Building workspace members",
            Language.CHINESE to "构建工作空间成员"
        )
        
        messages["workspace.buildComplete"] = mapOf(
            Language.ENGLISH to "Workspace build complete",
            Language.CHINESE to "工作空间构建完成"
        )
        
        messages["workspace.memberSuccess"] = mapOf(
            Language.ENGLISH to "Member %s built successfully",
            Language.CHINESE to "成员 %s 构建成功"
        )
        
        messages["workspace.memberFailed"] = mapOf(
            Language.ENGLISH to "Member %s build failed: %s",
            Language.CHINESE to "成员 %s 构建失败: %s"
        )
        
        messages["workspace.memberSkipped"] = mapOf(
            Language.ENGLISH to "Member %s skipped: %s",
            Language.CHINESE to "成员 %s 已跳过: %s"
        )
        
        messages["workspace.buildFailed"] = mapOf(
            Language.ENGLISH to "Workspace build failed: %s",
            Language.CHINESE to "工作空间构建失败: %s"
        )
        
        messages["clean.cleaning"] = mapOf(
            Language.ENGLISH to "Cleaning project: %s",
            Language.CHINESE to "清理项目: %s"
        )
        
        messages["clean.progress"] = mapOf(
            Language.ENGLISH to "Cleaning build outputs",
            Language.CHINESE to "清理构建输出"
        )
        
        messages["clean.complete"] = mapOf(
            Language.ENGLISH to "Clean complete",
            Language.CHINESE to "清理完成"
        )
        
        messages["clean.deleted"] = mapOf(
            Language.ENGLISH to "Deleted %d files",
            Language.CHINESE to "删除了 %d 个文件"
        )
        
        messages["clean.freedSpace"] = mapOf(
            Language.ENGLISH to "Freed space: %s",
            Language.CHINESE to "释放空间: %s"
        )
        
        messages["clean.deletedFiles"] = mapOf(
            Language.ENGLISH to "Deleted files:",
            Language.CHINESE to "删除的文件:"
        )
        
        messages["clean.moreFiles"] = mapOf(
            Language.ENGLISH to "... and %d more files",
            Language.CHINESE to "... 还有 %d 个文件"
        )
        
        messages["clean.failed"] = mapOf(
            Language.ENGLISH to "Clean failed: %s",
            Language.CHINESE to "清理失败: %s"
        )
        
        messages["init.initializing"] = mapOf(
            Language.ENGLISH to "Initializing project: %s",
            Language.CHINESE to "初始化项目: %s"
        )
        
        messages["init.path"] = mapOf(
            Language.ENGLISH to "Path: %s",
            Language.CHINESE to "路径: %s"
        )
        
        messages["init.template"] = mapOf(
            Language.ENGLISH to "Template: %s",
            Language.CHINESE to "模板: %s"
        )
        
        messages["init.loadingTemplate"] = mapOf(
            Language.ENGLISH to "Loading template",
            Language.CHINESE to "加载模板"
        )
        
        messages["init.templateLoaded"] = mapOf(
            Language.ENGLISH to "Template loaded",
            Language.CHINESE to "模板加载完成"
        )
        
        messages["init.creating"] = mapOf(
            Language.ENGLISH to "Creating project files",
            Language.CHINESE to "创建项目文件"
        )
        
        messages["init.created"] = mapOf(
            Language.ENGLISH to "Project created",
            Language.CHINESE to "项目创建完成"
        )
        
        messages["init.success"] = mapOf(
            Language.ENGLISH to "Project %s initialized successfully",
            Language.CHINESE to "项目 %s 初始化成功"
        )
        
        messages["init.filesCreated"] = mapOf(
            Language.ENGLISH to "Created %d files:",
            Language.CHINESE to "创建了 %d 个文件:"
        )
        
        messages["init.nextSteps"] = mapOf(
            Language.ENGLISH to "Next steps:",
            Language.CHINESE to "下一步:"
        )
        
        messages["init.failed"] = mapOf(
            Language.ENGLISH to "Project initialization failed: %s",
            Language.CHINESE to "项目初始化失败: %s"
        )
        
        messages["run.loadingConfig"] = mapOf(
            Language.ENGLISH to "Loading project configuration",
            Language.CHINESE to "加载项目配置"
        )
        
        messages["run.configLoaded"] = mapOf(
            Language.ENGLISH to "Configuration loaded",
            Language.CHINESE to "配置加载完成"
        )
        
        messages["run.notFound"] = mapOf(
            Language.ENGLISH to "Executable not found",
            Language.CHINESE to "未找到项目名称"
        )
        
        messages["run.executableNotFound"] = mapOf(
            Language.ENGLISH to "Executable not found, attempting to build first...",
            Language.CHINESE to "可执行文件不存在，尝试先构建项目..."
        )
        
        messages["run.buildFailed"] = mapOf(
            Language.ENGLISH to "Build failed, cannot run",
            Language.CHINESE to "构建失败，无法运行"
        )
        
        messages["run.running"] = mapOf(
            Language.ENGLISH to "Running: %s",
            Language.CHINESE to "运行: %s"
        )
        
        messages["run.complete"] = mapOf(
            Language.ENGLISH to "Program finished",
            Language.CHINESE to "程序运行完成"
        )
        
        messages["run.exitCode"] = mapOf(
            Language.ENGLISH to "Exit code: %d",
            Language.CHINESE to "程序退出码: %d"
        )
        
        messages["run.failed"] = mapOf(
            Language.ENGLISH to "Run failed: %s",
            Language.CHINESE to "运行失败: %s"
        )
        
        messages["add.adding"] = mapOf(
            Language.ENGLISH to "Adding dependency: %s",
            Language.CHINESE to "添加依赖: %s"
        )
        
        messages["add.loadingConfig"] = mapOf(
            Language.ENGLISH to "Loading project configuration",
            Language.CHINESE to "加载项目配置"
        )
        
        messages["add.configLoaded"] = mapOf(
            Language.ENGLISH to "Configuration loaded",
            Language.CHINESE to "配置加载完成"
        )
        
        messages["add.configNotFound"] = mapOf(
            Language.ENGLISH to "cjpm.toml not found",
            Language.CHINESE to "找不到 cjpm.toml 配置文件"
        )
        
        messages["add.sourceRequired"] = mapOf(
            Language.ENGLISH to "Must specify dependency source (--git, --path or version)",
            Language.CHINESE to "必须指定依赖来源 (--git, --path 或版本号)"
        )
        
        messages["add.updatingConfig"] = mapOf(
            Language.ENGLISH to "Updating configuration file",
            Language.CHINESE to "更新配置文件"
        )
        
        messages["add.configUpdated"] = mapOf(
            Language.ENGLISH to "Configuration file updated",
            Language.CHINESE to "配置文件更新完成"
        )
        
        messages["add.success"] = mapOf(
            Language.ENGLISH to "Dependency %s added successfully",
            Language.CHINESE to "依赖 %s 添加成功"
        )
        
        messages["add.buildHint"] = mapOf(
            Language.ENGLISH to "Run 'kcjpm build' to install dependencies",
            Language.CHINESE to "运行 'kcjpm build' 来安装依赖"
        )
        
        messages["add.failed"] = mapOf(
            Language.ENGLISH to "Failed to add dependency: %s",
            Language.CHINESE to "添加依赖失败: %s"
        )
        
        messages["update.loadingConfig"] = mapOf(
            Language.ENGLISH to "Loading project configuration",
            Language.CHINESE to "加载项目配置"
        )
        
        messages["update.configLoaded"] = mapOf(
            Language.ENGLISH to "Configuration loaded",
            Language.CHINESE to "配置加载完成"
        )
        
        messages["update.updating"] = mapOf(
            Language.ENGLISH to "Updating dependencies...",
            Language.CHINESE to "更新依赖..."
        )
        
        messages["update.updatingOne"] = mapOf(
            Language.ENGLISH to "Updating dependency: %s",
            Language.CHINESE to "更新依赖: %s"
        )
        
        messages["update.updatingAll"] = mapOf(
            Language.ENGLISH to "Updating all dependencies",
            Language.CHINESE to "更新所有依赖"
        )
        
        messages["update.complete"] = mapOf(
            Language.ENGLISH to "Dependencies updated",
            Language.CHINESE to "依赖更新完成"
        )
        
        messages["update.success"] = mapOf(
            Language.ENGLISH to "Updated %d dependencies",
            Language.CHINESE to "更新了 %d 个依赖"
        )
        
        messages["update.failed"] = mapOf(
            Language.ENGLISH to "Failed to update dependencies: %s",
            Language.CHINESE to "更新依赖失败: %s"
        )
    }
}
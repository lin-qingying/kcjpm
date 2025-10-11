package org.cangnova.kcjpm.init

object BuiltinTemplates {
    
    fun getExecutableTemplate(): ProjectTemplate.Builtin {
        val info = TemplateInfo(
            name = "executable",
            displayName = "可执行程序",
            description = "创建一个基础的可执行程序项目",
            author = "KCJPM",
            type = TemplateType.EXECUTABLE,
            source = TemplateSource.Builtin("executable")
        )
        
        val files = listOf(
            TemplateFile(
                path = "kcjpm.toml",
                content = """
[package]
name = "{{project_name}}"
version = "{{version}}"
cjc-version = "1.0.0"
output-type = "executable"{{#if authors}}
authors = [{{#each authors}}"{{this}}"{{#unless @last}}, {{/unless}}{{/each}}]{{/if}}{{#if description}}
description = "{{description}}"{{/if}}{{#if license}}
license = "{{license}}"{{/if}}

[dependencies]

[build]
source-dir = "src"
output-dir = "target"
parallel = true
incremental = true

[profile.debug]
optimization-level = 0
debug-info = true
lto = false

[profile.release]
optimization-level = 2
debug-info = false
lto = false
                """.trimIndent()
            ),
            TemplateFile(
                path = "src/main.cj",
                content = """
package {{project_name}}
main(): Int64 {
    println("Hello, {{project_name}}!")
    return 0
}
                """.trimIndent()
            ),
            TemplateFile(
                path = ".gitignore",
                content = """
/target/
/.kcjpm/
*.swp
*.swo
*~
.DS_Store
                """.trimIndent(),
                isTemplate = false
            ),
            TemplateFile(
                path = "README.md",
                content = """
# {{project_name}}
{{#if description}}
{{description}}
{{else}}
A Cangjie project created with KCJPM.
{{/if}}

## Build

\`\`\`bash
kcjpm build
\`\`\`

## Run

\`\`\`bash
kcjpm run
\`\`\`
                """.trimIndent()
            )
        )
        
        return ProjectTemplate.Builtin(info, files)
    }
    
    fun getLibraryTemplate(): ProjectTemplate.Builtin {
        val info = TemplateInfo(
            name = "library",
            displayName = "库项目",
            description = "创建一个可复用的库项目",
            author = "KCJPM",
            type = TemplateType.LIBRARY,
            source = TemplateSource.Builtin("library")
        )
        
        val files = listOf(
            TemplateFile(
                path = "kcjpm.toml",
                content = """
[package]
name = "{{project_name}}"
version = "{{version}}"
cjc-version = "1.0.0"
output-type = "library"{{#if authors}}
authors = [{{#each authors}}"{{this}}"{{#unless @last}}, {{/unless}}{{/each}}]{{/if}}{{#if description}}
description = "{{description}}"{{/if}}{{#if license}}
license = "{{license}}"{{/if}}

[dependencies]

[build]
source-dir = "src"
output-dir = "target"
parallel = true
incremental = true
                """.trimIndent()
            ),
            TemplateFile(
                path = "src/lib.cj",
                content = """
public func greet(name: String): String {
    return "Hello, ${'$'}{name}!"
}

public class {{pascal_case project_name}} {
    public init() {}
    
    public func sayHello(): Unit {
        println("Hello from {{project_name}} library!")
    }
}
                """.trimIndent()
            ),
            TemplateFile(
                path = ".gitignore",
                content = """
/target/
/.kcjpm/
*.swp
*.swo
*~
.DS_Store
                """.trimIndent(),
                isTemplate = false
            ),
            TemplateFile(
                path = "README.md",
                content = """
# {{project_name}}
{{#if description}}
{{description}}
{{else}}
A Cangjie library created with KCJPM.
{{/if}}

## Usage

Add this library to your `kcjpm.toml`:

\`\`\`toml
[dependencies]
{{project_name}} = "{{version}}"
\`\`\`

## Build

\`\`\`bash
kcjpm build
\`\`\`
                """.trimIndent()
            )
        )
        
        return ProjectTemplate.Builtin(info, files)
    }
    
    fun getWorkspaceTemplate(): ProjectTemplate.Builtin {
        val info = TemplateInfo(
            name = "workspace",
            displayName = "工作空间",
            description = "创建一个包含多个项目的工作空间",
            author = "KCJPM",
            type = TemplateType.WORKSPACE,
            source = TemplateSource.Builtin("workspace")
        )
        
        val files = listOf(
            TemplateFile(
                path = "kcjpm.toml",
                content = """
[workspace]
members = ["packages/*"]
default-members = []

[registry]
default = "https://repo.cangjie-lang.cn"
                """.trimIndent()
            ),
            TemplateFile(
                path = "packages/.gitkeep",
                content = "",
                isTemplate = false
            ),
            TemplateFile(
                path = ".gitignore",
                content = """
/target/
/.kcjpm/
*.swp
*.swo
*~
.DS_Store
                """.trimIndent(),
                isTemplate = false
            ),
            TemplateFile(
                path = "README.md",
                content = """
# {{project_name}}
{{#if description}}
{{description}}
{{else}}
A Cangjie workspace created with KCJPM.
{{/if}}

## Structure

Place your packages in the `packages/` directory.

## Build All

\`\`\`bash
kcjpm build
\`\`\`
                """.trimIndent()
            )
        )
        
        return ProjectTemplate.Builtin(info, files)
    }
    
    fun getAllTemplates(): List<ProjectTemplate.Builtin> {
        return listOf(
            getExecutableTemplate(),
            getLibraryTemplate(),
            getWorkspaceTemplate()
        )
    }
    
    fun getTemplate(name: String): ProjectTemplate.Builtin? {
        return when (name) {
            "executable", "exe", "bin" -> getExecutableTemplate()
            "library", "lib" -> getLibraryTemplate()
            "workspace", "ws" -> getWorkspaceTemplate()
            else -> null
        }
    }
}
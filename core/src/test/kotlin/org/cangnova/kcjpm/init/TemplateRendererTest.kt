package org.cangnova.kcjpm.init

import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.test.BaseTest

class TemplateRendererTest : BaseTest() {
    
    init {
        test("应该替换简单变量") {
            val template = "Hello, {{name}}!"
            val variables = mapOf("name" to "World")
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe "Hello, World!"
        }
        
        test("应该处理条件语句if") {
            val template = """
                Project: {{name}}{{#if description}}
                Description: {{description}}{{/if}}
            """.trimIndent()
            
            val withDescription = mapOf(
                "name" to "MyProject",
                "description" to "A cool project"
            )
            val withoutDescription = mapOf(
                "name" to "MyProject"
            )
            
            val result1 = TemplateRenderer.render(template, withDescription)
            result1 shouldBe """
                Project: MyProject
                Description: A cool project
            """.trimIndent()
            
            val result2 = TemplateRenderer.render(template, withoutDescription)
            result2 shouldBe "Project: MyProject"
        }
        
        test("应该处理if-else语句") {
            val template = """
                {{#if description}}
                {{description}}
                {{else}}
                No description provided.
                {{/if}}
            """.trimIndent()
            
            val result1 = TemplateRenderer.render(template, mapOf("description" to "Test"))
            result1.trim() shouldBe "Test"
            
            val result2 = TemplateRenderer.render(template, emptyMap())
            result2.trim() shouldBe "No description provided."
        }
        
        test("应该处理列表迭代") {
            val template = """
                Authors: [{{#each authors}}"{{this}}"{{#unless @last}}, {{/unless}}{{/each}}]
            """.trimIndent()
            
            val variables = mapOf(
                "authors" to listOf("Alice", "Bob", "Charlie")
            )
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe """Authors: ["Alice", "Bob", "Charlie"]"""
        }
        
        test("应该将字符串转换为PascalCase") {
            val template = "class {{pascal_case project_name}} {}"
            val variables = mapOf("project_name" to "my-cool-project")
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe "class MyCoolProject {}"
        }
        
        test("应该将字符串转换为snake_case") {
            val template = "const {{snake_case project_name}}"
            val variables = mapOf("project_name" to "MyCoolProject")
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe "const my_cool_project"
        }
        
        test("应该将字符串转换为camelCase") {
            val template = "function {{camel_case function_name}}() {}"
            val variables = mapOf("function_name" to "my-cool-function")
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe "function myCoolFunction() {}"
        }
        
        test("应该处理复杂模板") {
            val template = """
                [package]
                name = "{{project_name}}"
                version = "{{version}}"{{#if authors}}
                authors = [{{#each authors}}"{{this}}"{{#unless @last}}, {{/unless}}{{/each}}]{{/if}}{{#if description}}
                description = "{{description}}"{{/if}}
            """.trimIndent()
            
            val variables = mapOf(
                "project_name" to "my-app",
                "version" to "1.0.0",
                "authors" to listOf("Alice", "Bob"),
                "description" to "My application"
            )
            
            val result = TemplateRenderer.render(template, variables)
            
            result shouldBe """
                [package]
                name = "my-app"
                version = "1.0.0"
                authors = ["Alice", "Bob"]
                description = "My application"
            """.trimIndent()
        }
    }
}
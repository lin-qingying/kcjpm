package org.cangnova.kcjpm.init

object TemplateRenderer {
    
    fun render(template: String, variables: Map<String, Any>): String {
        var result = template
        
        variables.forEach { (key, value) ->
            result = result.replace("{{${key}}}", value.toString())
        }
        
        result = processConditionals(result, variables)
        result = processIterations(result, variables)
        result = processHelpers(result, variables)
        
        return result
    }
    
    private fun processConditionals(template: String, variables: Map<String, Any>): String {
        var result = template
        
        val ifPattern = Regex("""\{\{#if\s+(\w+)\}\}(.*?)\{\{/if\}\}""", RegexOption.DOT_MATCHES_ALL)
        result = ifPattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val content = matchResult.groupValues[2]
            
            val varValue = variables[varName]
            if (isTruthy(varValue)) content else ""
        }
        
        val ifElsePattern = Regex(
            """\{\{#if\s+(\w+)\}\}(.*?)\{\{else\}\}(.*?)\{\{/if\}\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        result = ifElsePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val trueContent = matchResult.groupValues[2]
            val falseContent = matchResult.groupValues[3]
            
            val varValue = variables[varName]
            if (isTruthy(varValue)) trueContent else falseContent
        }
        
        return result
    }
    
    private fun processIterations(template: String, variables: Map<String, Any>): String {
        var result = template
        
        val eachPattern = Regex("""\{\{#each\s+(\w+)\}\}(.*?)\{\{/each\}\}""", RegexOption.DOT_MATCHES_ALL)
        result = eachPattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val content = matchResult.groupValues[2]
            
            val list = variables[varName]
            if (list is List<*>) {
                list.mapIndexed { index, item ->
                    val itemVars = mapOf(
                        "this" to (item ?: ""),
                        "@index" to index,
                        "@first" to (index == 0),
                        "@last" to (index == list.size - 1)
                    )
                    
                    var itemContent = content
                    itemVars.forEach { (key, value) ->
                        itemContent = itemContent.replace("{{${key}}}", value.toString())
                    }
                    
                    val unlessLastPattern = Regex("""\{\{#unless\s+@last\}\}(.*?)\{\{/unless\}\}""")
                    itemContent = unlessLastPattern.replace(itemContent) { match ->
                        if (index == list.size - 1) "" else match.groupValues[1]
                    }
                    
                    itemContent
                }.joinToString("")
            } else {
                ""
            }
        }
        
        return result
    }
    
    private fun processHelpers(template: String, variables: Map<String, Any>): String {
        var result = template
        
        val pascalCasePattern = Regex("""\{\{pascal_case\s+(\w+)\}\}""")
        result = pascalCasePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val value = variables[varName]?.toString() ?: ""
            toPascalCase(value)
        }
        
        val snakeCasePattern = Regex("""\{\{snake_case\s+(\w+)\}\}""")
        result = snakeCasePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val value = variables[varName]?.toString() ?: ""
            toSnakeCase(value)
        }
        
        val camelCasePattern = Regex("""\{\{camel_case\s+(\w+)\}\}""")
        result = camelCasePattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            val value = variables[varName]?.toString() ?: ""
            toCamelCase(value)
        }
        
        return result
    }
    
    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            null -> false
            is Boolean -> value
            is String -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            is Number -> value.toDouble() != 0.0
            else -> true
        }
    }
    
    private fun toPascalCase(input: String): String {
        return input.split(Regex("[_\\-\\s]+"))
            .joinToString("") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
    
    private fun toSnakeCase(input: String): String {
        return input.replace(Regex("([A-Z])"), "_$1")
            .lowercase()
            .replace(Regex("^_"), "")
            .replace(Regex("[\\-\\s]+"), "_")
    }
    
    private fun toCamelCase(input: String): String {
        val words = input.split(Regex("[_\\-\\s]+"))
        return words.mapIndexed { index, word ->
            if (index == 0) {
                word.lowercase()
            } else {
                word.replaceFirstChar { it.uppercase() }
            }
        }.joinToString("")
    }
}
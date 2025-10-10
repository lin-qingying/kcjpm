package org.cangnova.kcjpm.workspace

import org.cangnova.kcjpm.config.DependencyConfig
import java.nio.file.Path


class WorkspaceDependencyGraph(
    private val workspace: Workspace
) {
    private val adjacencyList = mutableMapOf<String, MutableSet<String>>()
    private val inDegree = mutableMapOf<String, Int>()
    
    init {
        buildGraph()
    }
    
    private fun buildGraph() {
        workspace.members.forEach { member ->
            adjacencyList[member.name] = mutableSetOf()
            inDegree[member.name] = 0
        }
        
        workspace.members.forEach { member ->
            val dependencies = getWorkspaceDependencies(member)
            dependencies.forEach { depName ->
                adjacencyList[depName]?.add(member.name)
                inDegree[member.name] = (inDegree[member.name] ?: 0) + 1
            }
        }
    }
    
    private fun getWorkspaceDependencies(member: WorkspaceMember): List<String> {
        return member.config.dependencies
            .filter { (_, config) -> isWorkspaceMemberDependency(member, config) }
            .mapNotNull { (_, config) -> 
                resolveWorkspaceMemberName(member, config)
            }
    }
    
    private fun isWorkspaceMemberDependency(member: WorkspaceMember, config: DependencyConfig): Boolean {
        val path = config.path ?: return false
        val absolutePath = member.path.resolve(path).normalize()
        return workspace.members.any { it.path.normalize() == absolutePath }
    }
    
    private fun resolveWorkspaceMemberName(member: WorkspaceMember, config: DependencyConfig): String? {
        val path = config.path ?: return null
        val absolutePath = member.path.resolve(path).normalize()
        return workspace.members.find { it.path.normalize() == absolutePath }?.name
    }
    
    fun topologicalSort(): Result<List<WorkspaceMember>> = runCatching {
        val result = mutableListOf<WorkspaceMember>()
        val currentInDegree = inDegree.toMutableMap()
        val queue = ArrayDeque<String>()
        
        currentInDegree.filter { it.value == 0 }.forEach { (name, _) ->
            queue.add(name)
        }
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(workspace.getMember(current))
            
            adjacencyList[current]?.forEach { neighbor ->
                currentInDegree[neighbor] = (currentInDegree[neighbor] ?: 0) - 1
                if (currentInDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        if (result.size != workspace.members.size) {
            val cycles = detectCycles()
            throw IllegalStateException("工作空间存在循环依赖: ${cycles.joinToString(" -> ")}")
        }
        
        result
    }
    
    fun detectCycles(): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val currentPath = mutableListOf<String>()
        
        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)
            currentPath.add(node)
            
            adjacencyList[node]?.forEach { neighbor ->
                if (!visited.contains(neighbor)) {
                    if (dfs(neighbor)) return true
                } else if (recursionStack.contains(neighbor)) {
                    val cycleStartIndex = currentPath.indexOf(neighbor)
                    val cycle = currentPath.subList(cycleStartIndex, currentPath.size)
                    cycles.add(cycle)
                    return true
                }
            }
            
            recursionStack.remove(node)
            currentPath.removeAt(currentPath.size - 1)
            return false
        }
        
        workspace.members.forEach { member ->
            if (!visited.contains(member.name)) {
                dfs(member.name)
            }
        }
        
        return cycles
    }
    
    fun getDependents(memberName: String): List<String> {
        return adjacencyList[memberName]?.toList() ?: emptyList()
    }
    
    fun getDependencies(memberName: String): List<String> {
        val member = workspace.getMember(memberName)
        return getWorkspaceDependencies(member)
    }
    
    fun getIndependentMembers(): List<WorkspaceMember> {
        return workspace.members.filter { (inDegree[it.name] ?: 0) == 0 }
    }
}
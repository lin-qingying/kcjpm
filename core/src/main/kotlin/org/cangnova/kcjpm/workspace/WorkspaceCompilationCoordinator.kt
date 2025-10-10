package org.cangnova.kcjpm.workspace

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.cangnova.kcjpm.build.*
import org.cangnova.kcjpm.config.ConfigLoader

class WorkspaceCompilationCoordinator(
    private val workspace: Workspace
) {
    private val dependencyGraph = WorkspaceDependencyGraph(workspace)
    
    suspend fun buildAll(
        parallel: Boolean = true,
        targetPlatform: CompilationTarget? = null
    ): Result<Map<String, MemberBuildResult>> = runCatching {
        val sortedMembers = dependencyGraph.topologicalSort().getOrThrow()
        
        if (parallel) {
            buildInParallel(sortedMembers, targetPlatform)
        } else {
            buildInSequence(sortedMembers, targetPlatform)
        }
    }
    
    suspend fun buildMember(
        memberName: String,
        targetPlatform: CompilationTarget? = null
    ): Result<MemberBuildResult> = runCatching {
        val member = workspace.getMember(memberName)
        compileMember(member, targetPlatform)
    }
    
    suspend fun buildDefaultMembers(
        parallel: Boolean = true,
        targetPlatform: CompilationTarget? = null
    ): Result<Map<String, MemberBuildResult>> = runCatching {
        val defaultMemberNames = workspace.rootConfig.workspace?.defaultMembers
            ?: workspace.members.map { it.name }
        
        val members = defaultMemberNames.map { workspace.getMember(it) }
        val sortedMembers = sortMembers(members)
        
        if (parallel) {
            buildInParallel(sortedMembers, targetPlatform)
        } else {
            buildInSequence(sortedMembers, targetPlatform)
        }
    }
    
    private suspend fun buildInSequence(
        members: List<WorkspaceMember>,
        targetPlatform: CompilationTarget?
    ): Map<String, MemberBuildResult> {
        val results = mutableMapOf<String, MemberBuildResult>()
        
        for (member in members) {
            val result = try {
                compileMember(member, targetPlatform)
            } catch (e: Exception) {
                MemberBuildResult.Failure(member.name, e)
            }
            
            results[member.name] = result
            
            if (result is MemberBuildResult.Failure) {
                break
            }
        }
        
        return results
    }
    
    private suspend fun buildInParallel(
        members: List<WorkspaceMember>,
        targetPlatform: CompilationTarget?
    ): Map<String, MemberBuildResult> = coroutineScope {
        val results = mutableMapOf<String, MemberBuildResult>()
        val inDegree = members.associateWith { member ->
            dependencyGraph.getDependencies(member.name).size
        }.toMutableMap()
        
        val queue = ArrayDeque<WorkspaceMember>()
        members.filter { inDegree[it] == 0 }.forEach { queue.add(it) }
        
        val activeJobs = mutableMapOf<String, Deferred<MemberBuildResult>>()
        
        while (queue.isNotEmpty() || activeJobs.isNotEmpty()) {
            while (queue.isNotEmpty()) {
                val member = queue.removeFirst()
                val job = async {
                    try {
                        compileMember(member, targetPlatform)
                    } catch (e: Exception) {
                        MemberBuildResult.Failure(member.name, e)
                    }
                }
                activeJobs[member.name] = job
            }
            
            if (activeJobs.isNotEmpty()) {
                val completedJob = select {
                    activeJobs.forEach { (name, job) ->
                        job.onAwait { result ->
                            name to result
                        }
                    }
                }
                
                val (completedName, result) = completedJob
                activeJobs.remove(completedName)
                results[completedName] = result
                
                if (result is MemberBuildResult.Failure) {
                    activeJobs.values.forEach { it.cancel() }
                    break
                }
                
                dependencyGraph.getDependents(completedName).forEach { dependentName ->
                    val currentDegree = inDegree[workspace.getMember(dependentName)]
                    if (currentDegree != null && currentDegree > 0) {
                        inDegree[workspace.getMember(dependentName)] = currentDegree - 1
                        if (currentDegree - 1 == 0) {
                            queue.add(workspace.getMember(dependentName))
                        }
                    }
                }
            }
        }
        
        results
    }
    
    private suspend fun compileMember(
        member: WorkspaceMember,
        targetPlatform: CompilationTarget?
    ): MemberBuildResult = withContext(Dispatchers.IO) {
        val context = ConfigLoader.loadAndConvert(
            projectRoot = member.path,
            targetPlatform = targetPlatform
        ).getOrThrow()
        
        val manager = CompilationManager()
        val result = with(context) { manager.compile() }
        
        if (result.isSuccess) {
            val compilationResult = result.getOrThrow()
            when (compilationResult) {
                is CompilationResult.Success -> MemberBuildResult.Success(
                    memberName = member.name,
                    outputPath = compilationResult.outputPath,
                    artifacts = compilationResult.artifacts
                )
                is CompilationResult.Failure -> MemberBuildResult.Failure(
                    memberName = member.name,
                    error = RuntimeException(
                        "编译失败: ${compilationResult.errors.joinToString(", ") { it.message }}"
                    )
                )
            }
        } else {
            MemberBuildResult.Failure(
                memberName = member.name,
                error = result.exceptionOrNull() ?: RuntimeException("未知编译错误")
            )
        }
    }
    
    private fun sortMembers(members: List<WorkspaceMember>): List<WorkspaceMember> {
        val allSorted = dependencyGraph.topologicalSort().getOrThrow()
        return allSorted.filter { sorted -> members.any { it.name == sorted.name } }
    }
}
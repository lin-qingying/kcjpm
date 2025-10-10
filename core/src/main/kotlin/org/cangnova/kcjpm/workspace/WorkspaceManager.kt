package org.cangnova.kcjpm.workspace

import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.CjpmConfig
import java.nio.file.Path
import kotlin.io.path.*

interface WorkspaceManager {
    suspend fun loadWorkspace(rootPath: Path): Result<Workspace>
    
    suspend fun buildWorkspace(
        workspace: Workspace,
        members: List<String>? = null
    ): Result<WorkspaceResult>
    
    suspend fun buildMember(
        workspace: Workspace,
        memberName: String
    ): Result<MemberBuildResult>
    
    fun isWorkspaceRoot(path: Path): Boolean
}

class DefaultWorkspaceManager : WorkspaceManager {
    
    override suspend fun loadWorkspace(rootPath: Path): Result<Workspace> = runCatching {
        require(rootPath.exists() && rootPath.isDirectory()) {
            "工作空间根目录不存在或不是目录: $rootPath"
        }
        
        val rootConfig = ConfigLoader.loadFromProjectRoot(rootPath).getOrThrow()
        
        val workspaceConfig = rootConfig.workspace
            ?: throw IllegalArgumentException("指定路径不是工作空间根目录: $rootPath")
        
        val members = loadMembers(rootPath, workspaceConfig, rootConfig)
        
        Workspace(
            rootPath = rootPath,
            rootConfig = rootConfig,
            members = members,
            sharedDependencies = emptyList()
        )
    }
    
    private fun loadMembers(
        rootPath: Path,
        workspaceConfig: org.cangnova.kcjpm.config.WorkspaceConfig,
        rootConfig: CjpmConfig
    ): List<WorkspaceMember> {
        val members = mutableListOf<WorkspaceMember>()
        
        for (memberPattern in workspaceConfig.members) {
            when {
                memberPattern == "." -> {
                    val packageName = rootConfig.`package`?.name
                    if (packageName?.isNotBlank() == true) {
                        members.add(
                            WorkspaceMember(
                                name = packageName,
                                path = rootPath,
                                config = rootConfig
                            )
                        )
                    }
                }
                memberPattern.contains('*') -> {
                    val expandedMembers = expandGlobPattern(rootPath, memberPattern)
                    members.addAll(expandedMembers)
                }
                else -> {
                    val memberPath = rootPath.resolve(memberPattern).normalize()
                    if (memberPath.exists() && memberPath.isDirectory()) {
                        val memberConfig = ConfigLoader.loadFromProjectRoot(memberPath).getOrThrow()
                        val packageName = memberConfig.`package`?.name
                        if (packageName?.isNotBlank() == true) {
                            members.add(
                                WorkspaceMember(
                                    name = packageName,
                                    path = memberPath,
                                    config = memberConfig
                                )
                            )
                        }
                    }
                }
            }
        }
        
        return members
    }
    
    private fun expandGlobPattern(rootPath: Path, pattern: String): List<WorkspaceMember> {
        val members = mutableListOf<WorkspaceMember>()
        val parts = pattern.split('/')
        
        if (parts.size == 2 && parts[1] == "*") {
            val baseDir = rootPath.resolve(parts[0])
            if (baseDir.exists() && baseDir.isDirectory()) {
                baseDir.listDirectoryEntries().forEach { candidatePath ->
                    if (candidatePath.isDirectory()) {
                        val configResult = ConfigLoader.loadFromProjectRoot(candidatePath)
                        if (configResult.isSuccess) {
                            val config = configResult.getOrThrow()
                            val packageName = config.`package`?.name
                            if (packageName?.isNotBlank() == true) {
                                members.add(
                                    WorkspaceMember(
                                        name = packageName,
                                        path = candidatePath,
                                        config = config
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        
        return members
    }
    
    override suspend fun buildWorkspace(
        workspace: Workspace,
        members: List<String>?
    ): Result<WorkspaceResult> = runCatching {
        val targetMembers = if (members != null) {
            members.map { workspace.getMember(it) }
        } else {
            val defaultMembers = workspace.rootConfig.workspace?.defaultMembers
            if (!defaultMembers.isNullOrEmpty()) {
                defaultMembers.map { workspace.getMember(it) }
            } else {
                workspace.members
            }
        }
        
        val results = mutableMapOf<String, MemberBuildResult>()
        
        for (member in targetMembers) {
            val result = buildMember(workspace, member.name).getOrThrow()
            results[member.name] = result
        }
        
        WorkspaceResult(workspace, results)
    }
    
    override suspend fun buildMember(
        workspace: Workspace,
        memberName: String
    ): Result<MemberBuildResult> = runCatching {
        val member = workspace.getMember(memberName)
        
        MemberBuildResult.Success(
            memberName = memberName,
            outputPath = member.path.resolve("target").toString(),
            artifacts = emptyList()
        )
    }
    
    override fun isWorkspaceRoot(path: Path): Boolean {
        if (!path.exists() || !path.isDirectory()) return false
        
        val configResult = ConfigLoader.loadFromProjectRoot(path)
        if (configResult.isFailure) return false
        
        val config = configResult.getOrThrow()
        return config.workspace != null
    }
}
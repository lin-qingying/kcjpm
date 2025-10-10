package org.cangnova.kcjpm.workspace

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import java.nio.file.Path

data class Workspace(
    val rootPath: Path,
    val rootConfig: CjpmConfig,
    val members: List<WorkspaceMember>,
    val sharedDependencies: List<Dependency> = emptyList()
) {
    val isVirtual: Boolean
        get() = rootConfig.`package` == null
    
    val isMixed: Boolean
        get() = rootConfig.`package` != null
    
    fun findMember(name: String): WorkspaceMember? =
        members.find { it.name == name }
    
    fun getMember(name: String): WorkspaceMember =
        findMember(name) ?: throw IllegalArgumentException("工作空间成员不存在: $name")
}

data class WorkspaceMember(
    val name: String,
    val path: Path,
    val config: CjpmConfig
) {
    val relativePath: Path
        get() = path.fileName ?: path
}

data class WorkspaceResult(
    val workspace: Workspace,
    val results: Map<String, MemberBuildResult>
) {
    val isSuccess: Boolean
        get() = results.values.all { it.isSuccess }
    
    val failures: Map<String, MemberBuildResult.Failure>
        get() = results.filterValues { it is MemberBuildResult.Failure }
            .mapValues { it.value as MemberBuildResult.Failure }
}

sealed interface MemberBuildResult {
    val memberName: String
    val isSuccess: Boolean
    
    data class Success(
        override val memberName: String,
        val outputPath: String,
        val artifacts: List<String>
    ) : MemberBuildResult {
        override val isSuccess = true
    }
    
    data class Failure(
        override val memberName: String,
        val error: Throwable
    ) : MemberBuildResult {
        override val isSuccess = false
    }
    
    data class Skipped(
        override val memberName: String,
        val reason: String
    ) : MemberBuildResult {
        override val isSuccess = true
    }
}
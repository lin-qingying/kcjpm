package org.cangnova.kcjpm.build

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed interface CompilationResult {
    data class Success(val outputPath: String, val artifacts: List<String>) : CompilationResult
    data class Failure(val errors: List<CompilationError>) : CompilationResult
}

data class CompilationError(
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val severity: Severity = Severity.ERROR
) {
    enum class Severity { WARNING, ERROR, FATAL }
}

interface CompilationStage {
    val name: String
    
    suspend fun execute(context: CompilationContext): Result<CompilationContext>
    
    suspend fun onComplete(context: CompilationContext) {}
    
    suspend fun onFailure(context: CompilationContext, error: Throwable) {}
}

interface CompilationPipeline {
    val stages: List<CompilationStage>
    
    suspend fun compile(context: CompilationContext): Result<CompilationResult>
    
    fun addStage(stage: CompilationStage): CompilationPipeline
    
    fun insertStageAfter(afterStage: String, stage: CompilationStage): Result<CompilationPipeline>
    
    fun removeStage(stageName: String): CompilationPipeline
}

@DslMarker
annotation class PipelineDsl

@PipelineDsl
class CompilationPipelineBuilder {
    private val stages = mutableListOf<CompilationStage>()
    private var errorHandler: (suspend (CompilationError) -> Unit)? = null
    private var progressReporter: (suspend (String, Int, Int) -> Unit)? = null
    
    fun stage(stage: CompilationStage) = apply { stages.add(stage) }
    
    fun stages(vararg stages: CompilationStage) = apply { this.stages.addAll(stages) }
    
    fun stage(name: String, execute: suspend (CompilationContext) -> Result<CompilationContext>) = apply {
        stages.add(object : CompilationStage {
            override val name = name
            override suspend fun execute(context: CompilationContext) = execute(context)
        })
    }
    
    fun onError(handler: suspend (CompilationError) -> Unit) = apply {
        this.errorHandler = handler
    }
    
    fun onProgress(reporter: suspend (stageName: String, current: Int, total: Int) -> Unit) = apply {
        this.progressReporter = reporter
    }
    
    fun build(): CompilationPipeline = KotlinStyleCompilationPipeline(
        stages = stages.toList(),
        errorHandler = errorHandler,
        progressReporter = progressReporter
    )
}

class KotlinStyleCompilationPipeline(
    override val stages: List<CompilationStage>,
    private val errorHandler: (suspend (CompilationError) -> Unit)? = null,
    private val progressReporter: (suspend (String, Int, Int) -> Unit)? = null
) : CompilationPipeline {
    
    override suspend fun compile(context: CompilationContext): Result<CompilationResult> = runCatching {
        var currentContext = context
        val totalStages = stages.size
        val errors = mutableListOf<CompilationError>()
        
        stages.forEachIndexed { index, stage ->
            try {
                progressReporter?.invoke(stage.name, index + 1, totalStages)
                val result = stage.execute(currentContext)
                if (result.isSuccess) {
                    currentContext = result.getOrThrow()
                    stage.onComplete(currentContext)
                } else {
                    val error = result.exceptionOrNull()
                    val compilationError = CompilationError(
                        message = "编译阶段 '${stage.name}' 失败: ${error?.message}",
                        severity = CompilationError.Severity.ERROR
                    )
                    errors.add(compilationError)
                    errorHandler?.invoke(compilationError)
                    stage.onFailure(currentContext, error ?: RuntimeException("未知错误"))
                    
                    return@runCatching CompilationResult.Failure(errors)
                }
            } catch (e: Exception) {
                val compilationError = CompilationError(
                    message = "编译阶段 '${stage.name}' 异常: ${e.message}",
                    severity = CompilationError.Severity.FATAL
                )
                errors.add(compilationError)
                errorHandler?.invoke(compilationError)
                stage.onFailure(currentContext, e)
                throw e
            }
        }
        
        if (errors.isNotEmpty()) {
            CompilationResult.Failure(errors)
        } else {
            CompilationResult.Success(
                outputPath = currentContext.outputPath.toString(),
                artifacts = listOf(currentContext.outputPath.toString())
            )
        }
    }
    
    override fun addStage(stage: CompilationStage): CompilationPipeline =
        copy(stages = stages + stage)
    
    override fun insertStageAfter(afterStage: String, stage: CompilationStage): Result<CompilationPipeline> =
        runCatching {
            val index = stages.indexOfFirst { it.name == afterStage }
            if (index >= 0) {
                val newStages = stages.toMutableList().apply { add(index + 1, stage) }
                copy(stages = newStages)
            } else {
                throw IllegalArgumentException("找不到编译阶段: $afterStage")
            }
        }
    
    override fun removeStage(stageName: String): CompilationPipeline =
        copy(stages = stages.filterNot { it.name == stageName })
    
    private fun copy(
        stages: List<CompilationStage> = this.stages,
        errorHandler: (suspend (CompilationError) -> Unit)? = this.errorHandler,
        progressReporter: (suspend (String, Int, Int) -> Unit)? = this.progressReporter
    ) = KotlinStyleCompilationPipeline(stages, errorHandler, progressReporter)
}

@OptIn(ExperimentalContracts::class)
inline fun buildCompilationPipeline(
    crossinline block: CompilationPipelineBuilder.() -> Unit
): CompilationPipeline {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return CompilationPipelineBuilder().apply(block).build()
}

context(ctx: CompilationContext)
suspend fun CompilationPipeline.executeWith(): Result<CompilationResult> =
    this.compile(ctx)

suspend fun executeStagesInParallel(
    context: CompilationContext,
    vararg stages: CompilationStage
): List<Result<CompilationContext>> = coroutineScope {
    stages.map { stage ->
        async { stage.execute(context) }
    }.awaitAll()
}

suspend fun CompilationStage.executeWithRetry(
    context: CompilationContext,
    maxRetries: Int = 3,
    delayMs: Long = 1000
): Result<CompilationContext> {
    repeat(maxRetries) { attempt ->
        val result = execute(context)
        if (result.isSuccess) return result
        
        if (attempt < maxRetries - 1) {
            delay(delayMs * (attempt + 1))
        }
    }
    return execute(context)
}

data class CompilationProgress(
    val stageName: String,
    val current: Int,
    val total: Int,
    val percentage: Double = if (total > 0) (current.toDouble() / total * 100) else 0.0
)

fun CompilationPipeline.progressFlow(): Flow<CompilationProgress> = flow {
    stages.forEachIndexed { index, stage ->
        emit(CompilationProgress(stage.name, index + 1, stages.size))
    }
}
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

/**
 * 编译阶段接口
 *
 * 编译阶段是编译流水线中的基本执行单元，每个阶段负责编译过程中的特定任务。
 * 阶段按顺序执行，每个阶段接收上一个阶段的编译上下文，并返回更新后的上下文。
 *
 * ## 核心职责
 *
 * - **执行特定任务**: 每个阶段专注于编译过程的一个方面（如验证、依赖解析、代码生成等）
 * - **上下文传递**: 接收并返回 [CompilationContext]，允许阶段间共享状态
 * - **错误处理**: 通过 [Result] 类型安全地处理执行错误
 * - **生命周期回调**: 提供成功和失败时的回调钩子
 *
 * ## 实现示例
 *
 * ```kotlin
 * class ValidationStage : CompilationStage {
 *     override val name = "validation"
 *
 *     override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
 *         return context.validate().map { context }
 *     }
 *
 *     override suspend fun onComplete(context: CompilationContext) {
 *         println("验证完成: ${context.sourceFiles.size} 个文件")
 *     }
 *
 *     override suspend fun onFailure(context: CompilationContext, error: Throwable) {
 *         println("验证失败: ${error.message}")
 *     }
 * }
 * ```
 *
 * ## 使用场景
 *
 * - **验证阶段**: 检查配置、源文件、依赖的有效性
 * - **依赖解析**: 下载和解析项目依赖
 * - **包编译**: 将源代码编译为静态库或动态库
 * - **链接阶段**: 将编译产物链接为可执行文件
 * - **后处理**: 代码混淆、打包、签名等
 *
 * @see CompilationPipeline
 * @see CompilationContext
 */
interface CompilationStage {
    val name: String

    context(context: CompilationContext)
    suspend fun execute(): Result<CompilationContext>

    context(context: CompilationContext)
    suspend fun onComplete() {}

    context(context: CompilationContext)
    suspend fun onFailure(error: Throwable) {}
}

interface CompilationPipeline {
    val stages: List<CompilationStage>

    suspend fun compile(context: CompilationContext): Result<CompilationResult>

    context(context: CompilationContext)
    suspend fun execute(): Result<CompilationResult>

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
                val result = with(currentContext) { stage.execute() }
                if (result.isSuccess) {
                    currentContext = result.getOrThrow()
                    with(currentContext) { stage.onComplete() }
                } else {
                    val error = result.exceptionOrNull()
                    val compilationError = CompilationError(
                        message = "编译阶段 '${stage.name}' 失败: ${error?.message}",
                        severity = CompilationError.Severity.ERROR
                    )
                    errors.add(compilationError)
                    errorHandler?.invoke(compilationError)
                    with(currentContext) { stage.onFailure(error ?: RuntimeException("未知错误")) }

                    return@runCatching CompilationResult.Failure(errors)
                }
            } catch (e: Exception) {
                val compilationError = CompilationError(
                    message = "编译阶段 '${stage.name}' 异常: ${e.message}",
                    severity = CompilationError.Severity.FATAL
                )
                errors.add(compilationError)
                errorHandler?.invoke(compilationError)
                with(currentContext) { stage.onFailure(e) }
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

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationResult> = compile(context)

    override fun addStage(stage: CompilationStage): CompilationPipeline =
        copy(stages = stages + stage)

    override fun insertStageAfter(afterStage: String, stage: CompilationStage): Result<CompilationPipeline> =
        runCatching {
            val index = stages.indexOfFirst { it.name == afterStage }
            if (index >= 0) {
                val newStages = stages.toMutableList().apply { add(index + 1, stage) }
                copy(stages = newStages)
            } else {
                throw IllegalArgumentException("Cannot find compilation stage: $afterStage")
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

context(context: CompilationContext)
suspend fun executeStagesInParallel(
    vararg stages: CompilationStage
): List<Result<CompilationContext>> = coroutineScope {
    stages.map { stage ->
        async { with(context) { stage.execute() } }
    }.awaitAll()
}

context(context: CompilationContext)
suspend fun CompilationStage.executeWithRetry(
    maxRetries: Int = 3,
    delayMs: Long = 1000
): Result<CompilationContext> {
    repeat(maxRetries) { attempt ->
        val result = execute()
        if (result.isSuccess) return result

        if (attempt < maxRetries - 1) {
            delay(delayMs * (attempt + 1))
        }
    }
    return execute()
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
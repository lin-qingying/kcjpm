package org.cangnova.kcjpm.build

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

sealed interface CompilationEvent {
    val timestamp: Long get() = System.currentTimeMillis()
}

sealed interface StageEvent : CompilationEvent {
    val stageName: String
}

data class StageStartedEvent(
    override val stageName: String,
    val stageIndex: Int,
    val totalStages: Int
) : StageEvent

data class StageCompletedEvent(
    override val stageName: String,
    val stageIndex: Int,
    val totalStages: Int
) : StageEvent

data class ValidationEvent(
    val message: String,
    val path: Path? = null
) : CompilationEvent

data class DependencyResolutionEvent(
    val message: String,
    val dependencyName: String? = null,
    val dependencyType: String? = null
) : CompilationEvent

data class PackageDiscoveryEvent(
    val totalPackages: Int,
    val packages: List<PackageDiscoveryInfo>
) : CompilationEvent

data class PackageDiscoveryInfo(
    val name: String,
    val path: Path,
    val sourceFileCount: Int,
    val sourceFiles: List<Path>
)

data class PackageCompilationStartedEvent(
    val packageName: String,
    val packagePath: Path
) : CompilationEvent

data class PackageCompilationCommandEvent(
    val packageName: String,
    val command: List<String>
) : CompilationEvent

data class CompilerOutputEvent(
    val packageName: String,
    val line: String,
    val isStderr: Boolean
) : CompilationEvent

data class PackageCompilationCompletedEvent(
    val packageName: String,
    val success: Boolean,
    val outputPath: Path?,
    val errors: List<CjcDiagnostic>,
    val warnings: List<CjcDiagnostic>
) : CompilationEvent

data class IncrementalCacheEvent(
    val message: String,
    val cacheDir: Path? = null
) : CompilationEvent

data class ChangeDetectionEvent(
    val packageName: String,
    val changeType: String,
    val details: String? = null
) : CompilationEvent

data class PipelineStartedEvent(
    val totalStages: Int
) : CompilationEvent

data class PipelineCompletedEvent(
    val success: Boolean
) : CompilationEvent

interface CompilationEventListener {
    fun onEvent(event: CompilationEvent)
}

class CompilationEventBus {
    private val listeners = CopyOnWriteArrayList<CompilationEventListener>()
    
    fun addListener(listener: CompilationEventListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: CompilationEventListener) {
        listeners.remove(listener)
    }
    
    fun emit(event: CompilationEvent) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun clear() {
        listeners.clear()
    }
}

class CompilationEventCollector : CompilationEventListener {
    private val _events = CopyOnWriteArrayList<CompilationEvent>()
    val events: List<CompilationEvent> get() = _events.toList()
    
    override fun onEvent(event: CompilationEvent) {
        _events.add(event)
    }
    
    fun clear() {
        _events.clear()
    }
}

context(ctx: CompilationContext)
fun emit(event: CompilationEvent) {
    ctx.eventBus?.emit(event)
}
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // 应用来自约定插件的共享构建逻辑。
    // 共享代码位于 `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`。
    id("buildsrc.convention.kotlin-jvm")

    // 应用 Application 插件以添加对构建可执行 JVM 应用程序的支持。
    application
    
    // Kotlin 序列化插件
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // 项目 "app" 依赖于项目 "utils"。（项目路径用 ":" 分隔，因此 ":utils" 指的是顶层的 "utils" 项目。）
    implementation(project(":utils"))
    
    // Kotlin 序列化和 TOML 解析（用于数据模型）
    implementation(libs.kotlinxSerialization)
    implementation(libs.tomlkt)
    
    // Kotlin 协程支持（用于编译流水线）
    implementation(libs.kotlinxCoroutines)
    
    // 测试依赖
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestProperty)
    testImplementation(project(":config-toml"))
    testImplementation(project(":config-official"))

}

application {
    // 定义应用程序主类的完全限定名
    // （注意 Kotlin 将 `App.kt` 编译为 FQN 为 `com.example.app.AppKt` 的类。）
    mainClass = "org.cangnova.app.AppKt"
}
// 配置所有 Kotlin 编译任务启用上下文参数
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}
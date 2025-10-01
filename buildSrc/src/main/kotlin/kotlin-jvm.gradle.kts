// 此文件中的代码是约定插件 - 一种用于共享可重用构建逻辑的 Gradle 机制。
// `buildSrc` 是 Gradle 识别的目录，其中的每个插件在构建的其余部分中都可以轻松使用。
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // 应用 Kotlin JVM 插件以在 JVM 项目中添加对 Kotlin 的支持。
    kotlin("jvm")
}

kotlin {
    // 使用特定的 Java 版本以便在不同环境中更容易工作。
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    // 配置所有测试 Gradle 任务以使用 JUnitPlatform。
    useJUnitPlatform()

    // 记录所有测试结果的信息，而不仅仅是失败的测试。
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator

plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":utils"))
    implementation(project(":config-toml"))
    implementation(project(":config-official"))
    
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxCli)
    implementation(libs.jna)
    
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestProperty)
}

application {
    mainClass = "org.cangnova.kcjpm.cli.MainKt"
    applicationName = "kcjpm"
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(
                "@if \"%DEBUG%\"==\"\" @echo off",
                "@if \"%DEBUG%\"==\"\" @echo off\r\nchcp 65001 >nul 2>&1"
            )
        )
    }
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}

tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

distributions {
    main {
        contents {
            from("README.md") {
                into("")
            }
        }
    }
}
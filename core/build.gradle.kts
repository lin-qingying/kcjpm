import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    jvm {
        withJava()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
                implementation(libs.kotlinxSerialization)
                implementation(libs.kotlinxDatetime)
                implementation(libs.tomlkt)
                implementation(libs.kotlinxCoroutines)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotestAssertionsCore)
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(libs.klogging)
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotestRunnerJunit5)
                implementation(libs.kotestProperty)
                implementation(project(":config-toml"))
                implementation(project(":config-official"))
            }
        }
        
        val nativeMain by getting {
            dependencies {
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}
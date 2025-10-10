plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(libs.tomlkt)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotestAssertionsCore)
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotestRunnerJunit5)
                implementation(libs.kotestProperty)
            }
        }
    }
}
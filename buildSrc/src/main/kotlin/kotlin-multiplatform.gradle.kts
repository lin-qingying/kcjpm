package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)
    
    jvm()
    
    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
            }
        }
        
        val jvmTest by getting {
            dependencies {
            }
        }
        
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
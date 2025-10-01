plugins {
    id("buildsrc.convention.kotlin-jvm")
    
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    
    implementation(libs.tomlkt)
    
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestProperty)
}
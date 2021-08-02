import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "com.bitbit"
version = "1.0"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "9"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
}
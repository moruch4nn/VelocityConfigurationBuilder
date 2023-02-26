plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.10"
    application
}

group = "dev.mr3n"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("com.akuleshov7:ktoml-core:0.4.0")
    implementation("com.akuleshov7:ktoml-file:0.4.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("dev.mr3n.vcb.cli.AppKt")
}
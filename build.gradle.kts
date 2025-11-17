plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "cloud.coffeesystems"
version = "1.0-SNAPSHOT"

val ascendVersion = "21.0.0"
val stackedVersion = "4.0.0"
val sparkleVersion = "1.0.0-PRE-22"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {

    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    implementation("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.47.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.47.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.47.0")
    implementation("com.h2database:h2:2.2.224")

    compileOnly("com.github.TheFruxz:Ascend:$ascendVersion")
    compileOnly("com.github.TheFruxz:Stacked:$stackedVersion")
    compileOnly("com.github.TheFruxz:Sparkle:$sparkleVersion")
    @Suppress("DependencyOnStdlib") implementation(kotlin("stdlib"))

}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
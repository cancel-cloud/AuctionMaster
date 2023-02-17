plugins {
    kotlin("jvm") version "1.8.10"
}

group = "cloud.coffeesystems"
version = "1.0-SNAPSHOT"

val ascendVersion = "21.0.0"
val stackedVersion = "4.0.0"
val sparkleVersion = "1.0.0-PRE-21a"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {

    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT")

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
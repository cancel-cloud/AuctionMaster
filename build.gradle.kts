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
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    compileOnly("com.github.TheFruxz:Ascend:$ascendVersion")
    compileOnly("com.github.TheFruxz:Stacked:$stackedVersion")
    compileOnly("com.github.TheFruxz:Sparkle:$sparkleVersion")
    @Suppress("DependencyOnStdlib") implementation(kotlin("stdlib"))


    compileOnly("org.purpurmc.purpur", "purpur-api", "1.19.2-R0.1-SNAPSHOT")
}
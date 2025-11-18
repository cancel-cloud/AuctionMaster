plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "cloud.coffeesystems"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {

    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    compileOnly("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT")

    // Database dependencies
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")

    implementation(kotlin("stdlib"))

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Relocate dependencies to avoid conflicts
    relocate("com.zaxxer.hikari", "cloud.coffeesystems.auctionmaster.libs.hikari")
    relocate("org.sqlite", "cloud.coffeesystems.auctionmaster.libs.sqlite")
    relocate("com.mysql", "cloud.coffeesystems.auctionmaster.libs.mysql")

    // Minimize the jar
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
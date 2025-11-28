import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "9.2.2"
}

// ============================================================================
// Project Metadata
// ============================================================================
group = "cloud.coffeesystems"
version = "1.0.0"
var host = "github.com/cancel-cloud/AuctionMaster"

// ============================================================================
// Toolchain
// ============================================================================
kotlin {
    jvmToolchain(21)
}

// ============================================================================
// Repositories
// ============================================================================
repositories {
    mavenCentral()
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://nexus.fruxz.dev/repository/public/")
    maven("https://jitpack.io")
}

// ============================================================================
// Force Exposed version to avoid beta package changes
// ============================================================================
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.exposed:exposed-core:0.57.0")
        force("org.jetbrains.exposed:exposed-dao:0.57.0")
        force("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    }
}

// ============================================================================
// Dependencies
// ============================================================================
dependencies {
    // Kotlin & Coroutines (provided by MCKotlin-Paper)
    compileOnly(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Server API
    compileOnly("org.purpurmc.purpur", "purpur-api", "1.21.4-R0.1-SNAPSHOT")
    //Vault API
    compileOnly ("com.github.MilkBowl", "VaultAPI", "1.7.1")

    // Database - Exposed Framework (using 0.57.0 to avoid beta package changes)
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.postgresql:postgresql:42.7.3")

    // Utilities (Fruxz)
    implementation("dev.fruxz:stacked:2025.8-d15c9d0")
    implementation("dev.fruxz:ascend:2025.8-64943c6")

    // Testing
    testImplementation(kotlin("test"))
}

// ============================================================================
// Build Tasks
// ============================================================================
tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "name" to project.name,
        "website" to "https://$host"
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Exclude Kotlin stdlib (provided by MCKotlin-Paper)
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
    }

    // Relocate dependencies to avoid conflicts
    // Note: JDBC drivers and ORM frameworks (like Exposed) should NOT be relocated
    // as they use reflection and dynamic class loading
    relocate("com.zaxxer.hikari", "cloud.coffeesystems.auctionmaster.libs.hikari")

    // Minimize the jar, but exclude Exposed framework (uses reflection)
    minimize {
        exclude(dependency("org.jetbrains.exposed:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}

// ============================================================================
// Local Development Helpers
// ============================================================================
tasks.register<Copy>("copyJar") {
    dependsOn(tasks.named<ShadowJar>("shadowJar"))
    from(tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile)
    into(file("/Users/cancelcloud/Developer/Minecraft/purpur21-4/plugins/"))
    rename { "AuctionMaster-${version}.jar" }
}

// Finalize the build task to automatically copy the JAR
tasks.named("build") {
    finalizedBy("copyJar")
}

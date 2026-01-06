plugins {
    kotlin("jvm") version "2.3.0-Beta1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "io.github.darkstarworks"
version = "1.2.17"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // GUI Framework
    implementation("com.github.stefvanschie.inventoryframework:IF:0.11.5")

    // JSON parsing for update checker
    implementation("com.google.code.gson:gson:2.10.1")

    // Economy (optional)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // Note: FoliaLib can be added later if Folia support is needed
    // For now, using standard Bukkit scheduler

    // Optional integrations
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("me.clip:placeholderapi:2.11.5")

    // FastAsyncWorldEdit (FAWE) - preferred over vanilla WorldEdit for performance
    // Using BOM for version management and transitive=false to avoid dependency bloat
    implementation(platform("com.intellectualsites.bom:bom-newest:1.55"))
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit") {
        isTransitive = false
    }

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Do not relocate Kotlin stdlib or kotlinx-coroutines to ensure Bukkit can find them
        // They will be shaded into the jar with their original package names
        // This avoids NoClassDefFoundError for kotlinx.coroutines.Dispatchers during plugin bootstrap
        // Important: Do NOT relocate org.sqlite, or the sqlite-jdbc native bindings (JNI) will fail to load
        relocate("com.zaxxer.hikari", "io.github.darkstarworks.tcp.hikari")
        relocate("com.github.stefvanschie.inventoryframework", "io.github.darkstarworks.tcp.inventoryframework")

        // Exclude unnecessary SQLite native binaries to reduce jar size
        // Keep only Windows (x86/x64), Linux (x86/x64), and Linux-ARM for common MC server platforms
        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Mac/**")

        // Do not exclude HikariCP metrics; needed at runtime to avoid NoClassDefFoundError
        // (HikariConfig references MetricsTrackerFactory via reflection)
    }

    // Disable building the plain/thin jar; only produce the shaded (fat) jar
    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Ensure IDEs and users invoking 'assemble' also get the shaded (fat) jar
tasks {
    assemble {
        dependsOn(shadowJar)
    }
}

plugins {
    java
    application
    id("io.freefair.lombok") version "8.10.2"
}

group = "rift.launcher"
version = "1.0.0"

// Match the version the fork's catalog pinned (kept consistent with the client's lombok).
lombok {
    version = "1.18.30"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
    // flatlaf here is net.runelite:flatlaf, published to the RuneLite maven repo.
    maven { url = uri("https://repo.runelite.net") }
}

application {
    mainClass.set("rift.launcher.RiftLauncher")
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("net.java.dev.jna:jna:5.9.0")
    implementation("net.java.dev.jna:jna-platform:5.9.0")
    implementation("org.slf4j:slf4j-api:1.7.25")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.9") {
        // Keep slf4j-api pinned to 1.7.25; logback 1.2.9 would otherwise pull 1.7.32.
        exclude("org.slf4j", "slf4j-api")
    }
    implementation("net.runelite:flatlaf:3.2.5-rl4")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("org.hamcrest:hamcrest-library:1.3")
}

// Runnable, dependency-bundled jar (fat jar) — the packaged app runs this single jar.
val shadowJar = tasks.register<Jar>("shadowJar") {
    dependsOn(configurations.runtimeClasspath)
    manifest {
        attributes["Main-Class"] = "rift.launcher.RiftLauncher"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { cfg -> cfg.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/module-info.class")
    archiveFileName.set("rift-launcher.jar")
}
tasks.assemble { dependsOn(shadowJar) }

// Push the runnable jar to the locations the installed launcher actually runs from (jpackage app-image
// loads $APPDIR\rift-launcher.jar). Replacing this jar updates the .exe without re-running jpackage.
tasks.register("deployLauncher") {
    dependsOn(shadowJar)
    doLast {
        val jar = shadowJar.get().archiveFile.get().asFile
        val riftDir = File(System.getProperty("user.home"), ".rift")
        listOf(
            File(riftDir, "rift-launcher.jar"),
            File(riftDir, "launcher-app/_input/rift-launcher.jar"),
            File(riftDir, "launcher-app/RiftLauncher/app/rift-launcher.jar")
        ).forEach { target ->
            if (target.parentFile.isDirectory) {
                jar.copyTo(target, overwrite = true)
                logger.lifecycle("Deployed launcher -> $target")
            } else {
                logger.lifecycle("Skipped (no such dir) -> $target")
            }
        }
    }
}

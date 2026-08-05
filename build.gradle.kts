plugins {
    java
    application
    id("io.freefair.lombok") version "8.10.2"
}

group = "rift.launcher"
version = "1.0.4"

// Match the version the fork's catalog pinned (kept consistent with the client's lombok).
lombok {
    version = "1.18.30"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// Sources are UTF-8. Without this javac uses the platform default (Cp1252 on Windows), which silently
// corrupts non-ASCII string literals -- a bullet character in the UI came out as mojibake.
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Javadoc>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach { systemProperty("file.encoding", "UTF-8") }

// Stamp the build version into a resource so the running launcher can report it. This is the single
// source of truth: the version used to live here AND as a constant in RiftLauncher, and the two had
// already drifted (1.0.0 vs 1.0.3).
tasks.processResources {
    inputs.property("projectVersion", project.version)
    filesMatching("rift/launcher/version.properties") {
        filter { it.replace("\${project.version}", project.version.toString()) }
    }
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
        // The app-image jar is the one the installed launcher actually runs. Windows holds it open
        // while the launcher is running, so a copy there fails -- and failing quietly means the next
        // launch silently runs the old build. Fail loudly instead.
        val appImageJar = File(riftDir, "launcher-app/RiftLauncher/app/rift-launcher.jar")
        var appImageUpdated = false

        listOf(
            File(riftDir, "rift-launcher.jar"),
            File(riftDir, "launcher-app/_input/rift-launcher.jar"),
            appImageJar
        ).forEach { target ->
            if (!target.parentFile.isDirectory) {
                logger.lifecycle("Skipped (no such dir) -> $target")
                return@forEach
            }
            try {
                jar.copyTo(target, overwrite = true)
                logger.lifecycle("Deployed launcher -> $target")
                if (target == appImageJar) appImageUpdated = true
            } catch (e: Exception) {
                logger.lifecycle("FAILED -> $target (${e.javaClass.simpleName})")
            }
        }

        if (appImageJar.parentFile.isDirectory && !appImageUpdated) {
            throw GradleException(
                "Could not update ${appImageJar}. The Rift launcher is probably running and holding " +
                    "it open — close it and re-run. (The installed launcher would otherwise keep " +
                    "running the previous build.)"
            )
        }
    }
}

// --- Packaging -------------------------------------------------------------------------------
//
// Rift must ship its own Java: a user should never need a JDK installed. The launcher app-image
// bundles a runtime, and the client is spawned from the launcher's java.home, so the whole chain
// runs on the bundled JRE.
//
// The bundled runtime MUST be Java 11. RuneLite reflects into java.base internals (ReflectUtil),
// which Java 16+ blocks under strong encapsulation (JEP 403). jpackage only exists in JDK 14+, so a
// newer JDK is used purely as the tool and is handed the Java 11 runtime via --runtime-image.
val jpackageHome: String = (project.findProperty("rift.jpackageHome") as String?)
    ?: System.getenv("RIFT_JPACKAGE_HOME")
    ?: """C:\Users\down_\.jdks\corretto-17.0.18"""
val java11Home: String = (project.findProperty("rift.java11Home") as String?)
    ?: System.getenv("JAVA_HOME")
    ?: """C:\Program Files\Eclipse Adoptium\jdk-11.0.30.7-hotspot"""

// A Java 11 runtime for the app-image. ALL-MODULE-PATH takes every module the JDK offers: a curated
// list would be smaller, but a module missing from a shipped installer only fails at runtime on a
// user's machine, and the client pulls in a wide surface (GPU, audio, TLS, JNA, reflection). Stripping
// debug info and compressing still leaves it well under a full JDK. (Java 11's jlink has no
// ALL-DEFAULT alias -- that is a jpackage-era name and errors here.)
val jlinkRuntime = tasks.register<Exec>("jlinkRuntime") {
    val out = layout.buildDirectory.dir("java11-runtime").get().asFile
    doFirst { out.deleteRecursively() }
    commandLine(
        "$java11Home/bin/jlink.exe",
        "--add-modules", "ALL-MODULE-PATH",
        "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2",
        "--output", out.absolutePath
    )
    doLast { logger.lifecycle("Java 11 runtime -> $out") }
}

tasks.register<Exec>("jpackageAppImage") {
    dependsOn(shadowJar, jlinkRuntime)
    val input = layout.buildDirectory.dir("jpackage-input").get().asFile
    val dest = layout.buildDirectory.dir("app-image").get().asFile
    val runtime = layout.buildDirectory.dir("java11-runtime").get().asFile

    doFirst {
        input.deleteRecursively()
        input.mkdirs()
        shadowJar.get().archiveFile.get().asFile.copyTo(File(input, "rift-launcher.jar"), true)
        dest.deleteRecursively()
        dest.mkdirs()
    }

    // Same icon the installer uses, so the exe, its shortcuts and the setup program all match rather
    // than RiftLauncher.exe showing the generic Java icon.
    val icon = rootProject.file("../installer/rift-installer.ico")

    commandLine(
        listOf(
            "$jpackageHome/bin/jpackage.exe",
            "--type", "app-image",
            "--name", "RiftLauncher",
            "--input", input.absolutePath,
            "--main-jar", "rift-launcher.jar",
            "--main-class", "rift.launcher.RiftLauncher",
            "--runtime-image", runtime.absolutePath,
            "--dest", dest.absolutePath
        ) + if (icon.isFile) listOf("--icon", icon.absolutePath) else emptyList()
    )

    doLast { logger.lifecycle("App-image built -> ${dest.resolve("RiftLauncher")}") }
}

// Everything the Inno Setup installer packages, so the .iss has one predictable source directory.
tasks.register<Copy>("stageInstallerPayload") {
    dependsOn(shadowJar, "jpackageAppImage")
    val staging = layout.buildDirectory.dir("installer-payload")
    // Wipe first: Copy leaves behind files whose source has gone, so a dropped artefact (the client
    // jar) would linger here and make the staging directory misrepresent what actually ships.
    doFirst { staging.get().asFile.deleteRecursively() }
    into(staging)
    from(shadowJar.get().archiveFile) { rename { "rift-launcher.jar" } }
    // The client jar is intentionally absent: the launcher downloads it from the client release
    // channel, so the installer can never overwrite a newer client with a stale bundled copy.
    from(layout.buildDirectory.dir("app-image/RiftLauncher")) { into("launcher-app/RiftLauncher") }
    doLast { logger.lifecycle("Installer payload staged -> ${staging.get().asFile}") }
}

import java.util.zip.ZipFile

plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.deepsymmetry:beat-link:8.0.0")
    implementation("io.javalin:javalin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.jar {
    // The updater reads the running version from here.
    manifest {
        attributes("Implementation-Title" to "LJ Link", "Implementation-Version" to project.version)
    }
}

// The program shows its own license, the notices and the license files the libraries ship (menu → Licenses), so they
// travel with every installer.
val legalFiles = tasks.register("legalFiles") {
    val out = layout.buildDirectory.dir("generated/legal")
    val jars = configurations.runtimeClasspath
    inputs.files(jars, "LICENSE", "THIRD_PARTY_NOTICES.md")
    outputs.dir(out)
    doLast {
        val legal = out.get().dir("web/legal").asFile
        legal.deleteRecursively()
        val libraries = File(legal, "libraries")
        libraries.mkdirs()
        file("LICENSE").copyTo(File(legal, "LICENSE.txt"))
        file("THIRD_PARTY_NOTICES.md").copyTo(File(legal, "THIRD_PARTY_NOTICES.md"))
        val index = mutableListOf<String>()
        jars.get().files.filter { it.name.endsWith(".jar") }.sortedBy { it.name }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && Regex("META-INF/(LICEN[CS]E|NOTICE)[^/]*", RegexOption.IGNORE_CASE).matches(it.name) }
                    .forEach { entry ->
                        val target = File(libraries, jar.nameWithoutExtension + "/" + entry.name.substringAfter("META-INF/"))
                        target.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                        index += jar.nameWithoutExtension + "/" + target.name
                    }
            }
        }
        File(libraries, "index.txt").writeText(index.joinToString("\n") + "\n")
    }
}

sourceSets.main {
    resources.srcDir(files(layout.buildDirectory.dir("generated/legal")).builtBy(legalFiles))
}

application {
    mainClass.set("dev.ljlinkpro.Main")
    applicationName = "ljlinkpro"
}

version = "1.7.5"

// Builds a native installer with a bundled Java runtime: .dmg on macOS, .exe on Windows.
// jpackage can only build installers for the OS it runs on, so each OS is built on its own (see CI).
tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Creates the native installer (.dmg on macOS, .exe on Windows) in build/installers"
    dependsOn("installDist")

    val os = org.gradle.internal.os.OperatingSystem.current()
    val type = when {
        os.isMacOsX -> "dmg"
        os.isWindows -> "exe"
        else -> "app-image"
    }
    val jpackage = File(System.getProperty("java.home"), "bin/jpackage" + if (os.isWindows) ".exe" else "")
    val libDir = layout.buildDirectory.dir("install/ljlinkpro/lib").get().asFile
    val outDir = layout.buildDirectory.dir("installers").get().asFile

    val cmd = mutableListOf(
        jpackage.absolutePath,
        "--type", type,
        "--name", "LJ Link",
        "--app-version", project.version.toString(),
        "--vendor", "NEXTPHILM",
        "--copyright", "© 2026 Philipp Spruck / NEXTPHILM. Eclipse Public License 2.0",
        "--description", "Live waveform monitor for DJ players",
        "--input", libDir.absolutePath,
        "--main-jar", "ljlinkpro-${project.version}.jar",
        "--main-class", "dev.ljlinkpro.Main",
        "--dest", outDir.absolutePath,
        // jdk.crypto.ec: HTTPS to GitHub (update check) needs elliptic curve TLS; without it the handshake fails.
        "--add-modules", "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata",
    )
    val icon = File(projectDir, "packaging/LJ-Link-PRO." + if (os.isMacOsX) "icns" else if (os.isWindows) "ico" else "png")
    if (icon.exists()) {
        cmd += listOf("--icon", icon.absolutePath)
    }
    // Same as in Main.main, but set before the JVM starts (see the comment there).
    cmd += listOf("--java-options", "-Djava.net.preferIPv4Stack=true")
    if (os.isMacOsX) {
        // Menu bar app: no Dock icon.
        cmd += listOf("--java-options", "-Dapple.awt.UIElement=true", "--mac-package-name", "LJ Link", "--mac-app-category", "public.app-category.music")
    }
    if (os.isWindows) {
        // The upgrade code jpackage derived from the old name "LJ-Link-PRO", so the renamed installer replaces old installs.
        cmd += listOf("--win-menu", "--win-shortcut", "--win-per-user-install", "--win-upgrade-uuid", "7d9e8398-d8b0-3dcb-9460-4c04a0aa91b3")
    }
    commandLine(cmd)

    doFirst {
        outDir.deleteRecursively()
        outDir.mkdirs()
    }
}

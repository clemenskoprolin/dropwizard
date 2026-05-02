import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("org.jetbrains.intellij.platform") version "2.3.0"
    kotlin("jvm") version "2.1.20"
}

group = "io.dropwizard.j2k"
version = "1.0.0-SNAPSHOT"

// Pin both IDEA and Kotlin plugin versions so J2K API changes don't go unnoticed.
val pinnedIdeaVersion = "2024.3.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(pinnedIdeaVersion)
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        // Needed to run IDE tests in the test harness
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = "headless-j2k-runner"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { "243.*" }
        }
    }
    buildSearchableOptions = false
    autoReload = false
}

// Disable the default runIde task to avoid accidental GUI launch.
tasks.named("runIde") { enabled = false }

// Custom task to run the headless conversion.
// Invoke with:
//   ./gradlew runJ2kHeadless \
//     -Pj2k.sourceRoot=<dir>    \
//     -Pj2k.outputRoot=<dir>    \
//     -Pj2k.classpathFile=<file>  (optional — colon-separated Maven classpath)
//     -Pj2k.files=<file>          (optional — path to newline-delimited Java file list)
//     -Pj2k.report=<file>         (output JSON report path)
tasks.register<RunIdeTask>("runJ2kHeadless") {
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf(
            "-Djava.awt.headless=true",
            "-Didea.is.internal=true",
            "-Didea.log.path=${layout.buildDirectory.get().asFile.absolutePath}/j2k-logs",
            "-Didea.skip.indices.initialization=true",
        )
    }

    argumentProviders += CommandLineArgumentProvider {
        val sourceRoot = project.findProperty("j2k.sourceRoot") as String?
            ?: error("Missing Gradle property: -Pj2k.sourceRoot")
        val outputRoot = project.findProperty("j2k.outputRoot") as String?
            ?: error("Missing Gradle property: -Pj2k.outputRoot")
        val classpathFile = project.findProperty("j2k.classpathFile") as String? ?: ""
        val filesPath    = project.findProperty("j2k.files")         as String? ?: ""
        val report       = project.findProperty("j2k.report")        as String?
            ?: error("Missing Gradle property: -Pj2k.report")

        buildList {
            add("j2k-headless")
            add("--source-root"); add(sourceRoot)
            add("--output-root"); add(outputRoot)
            if (classpathFile.isNotBlank()) { add("--classpath-file"); add(classpathFile) }
            if (filesPath.isNotBlank())     { add("--files");          add(filesPath)    }
            add("--report"); add(report)
        }
    }
}

tasks.test {
    // IntelliJ Platform tests require the IDE to be present; the platform plugin
    // sets this up automatically when running via Gradle.
}

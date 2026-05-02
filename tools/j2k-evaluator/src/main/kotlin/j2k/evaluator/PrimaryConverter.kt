package j2k.evaluator

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class PrimaryConverter(private val opts: Map<String, String>) {

    fun run() {
        val sourceDir  = Paths.get(opts.require("source"))
        val outputDir  = Paths.get(opts.require("output"))
        outputDir.createDirectories()

        val headlessRunnerDir = opts.opt("headless-runner-dir")
        val j2kBin            = opts.opt("j2k-bin")

        check(headlessRunnerDir.isNotBlank() || j2kBin.isNotBlank()) {
            "convert-primary requires --headless-runner-dir or --j2k-bin; neither was supplied. " +
            "Build tools/headless-j2k-runner first: ./gradlew -p tools/headless-j2k-runner buildPlugin"
        }

        val javaFiles = findByExtension(sourceDir, "java")
        println("convert-primary: ${javaFiles.size} Java files in $sourceDir")

        val result = if (headlessRunnerDir.isNotBlank()) {
            val classpathFile = opts.opt("classpath-file")
            headlessConvert(Paths.get(headlessRunnerDir), javaFiles, sourceDir, outputDir, classpathFile)
        } else {
            val j2kPath = Paths.get(j2kBin)
            check(j2kPath.exists() && j2kPath.isExecutable()) {
                "j2k binary not found or not executable: $j2kBin"
            }
            j2kConvert(j2kPath, javaFiles, sourceDir, outputDir, opts.opt("classpath"))
        }

        val reportsDir = outputDir.parent?.parent?.resolve("reports") ?: outputDir.resolve("../../reports")
        ReportWriter.writeConversionJson(result, reportsDir)
        println("Converted ${result.convertedCount}/${result.javaFileCount}, failed ${result.failedCount}")
    }

    // --- headless runner invocation ---

    private fun headlessConvert(
        runnerDir: Path,
        javaFiles: List<Path>,
        sourceRoot: Path,
        outputRoot: Path,
        classpathFile: String,
    ): ConversionResult {
        val filesListFile = Files.createTempFile("j2k-files-", ".txt")
        try {
            filesListFile.writeText(javaFiles.joinToString("\n") { it.toAbsolutePath().toString() })

            val reportFile = outputRoot.parent?.parent?.resolve("reports/conversion-result.json")
                ?: outputRoot.resolve("../../reports/conversion-result.json")
            reportFile.parent?.createDirectories()

            runGradleTask(
                runnerDir,
                sourceRoot    = sourceRoot.toAbsolutePath().toString(),
                outputRoot    = outputRoot.toAbsolutePath().toString(),
                classpathFile = classpathFile,
                filesPath     = filesListFile.toAbsolutePath().toString(),
                reportPath    = reportFile.toAbsolutePath().toString(),
            )

            val ktFiles    = findByExtension(outputRoot, "kt")
            val converted  = ktFiles.count { isRealKotlin(it.readText()) }
            val failed     = ktFiles.size - converted
            return ConversionResult(HEADLESS_RUNNER_VARIANT, javaFiles.size, converted, failed)
        } finally {
            filesListFile.toFile().delete()
        }
    }

    // --- kotlinc j2k invocation (per-file) ---

    private fun j2kConvert(
        j2kBin: Path,
        javaFiles: List<Path>,
        sourceRoot: Path,
        outputRoot: Path,
        classpath: String,
    ): ConversionResult {
        val workDir = Files.createTempDirectory("j2k-primary")
        var converted = 0
        var failed    = 0

        try {
            for (javaFile in javaFiles) {
                val relative = sourceRoot.relativize(javaFile)
                val workFile = workDir.resolve(relative)
                workFile.parent?.createDirectories()
                Files.copy(javaFile, workFile)

                val cmd = buildList {
                    add(j2kBin.toString())
                    if (classpath.isNotBlank()) { add("-classpath"); add(classpath) }
                    add(workFile.toString())
                }

                val proc = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()
                val exitedOk = proc.waitFor(120, TimeUnit.SECONDS) && proc.exitValue() == 0

                val ktWork = workFile.parent?.resolve("${workFile.nameWithoutExtension}.kt")
                    ?: workDir.resolve("${workFile.nameWithoutExtension}.kt")
                val destDir = outputRoot.resolve(relative.parent ?: Paths.get(""))
                destDir.createDirectories()
                val dest = destDir.resolve("${javaFile.nameWithoutExtension}.kt")

                if (exitedOk && ktWork.exists()) {
                    Files.copy(ktWork, dest)
                    converted++
                } else {
                    dest.writeText("// CONVERSION FAILED: ${javaFile.name}\n")
                    failed++
                }
            }
        } finally {
            workDir.toFile().deleteRecursively()
        }

        return ConversionResult(KOTLINC_VERSION, javaFiles.size, converted, failed)
    }

    // --- shared helpers ---

    companion object {
        fun findByExtension(root: Path, ext: String): List<Path> =
            root.toFile().walk()
                .filter { it.isFile && it.extension == ext }
                .map { it.toPath() }
                .toList()

        fun isRealKotlin(text: String) =
            !text.startsWith("// STUB") && !text.startsWith("// CONVERSION FAILED")

        fun runGradleTask(
            runnerDir: Path,
            sourceRoot: String,
            outputRoot: String,
            classpathFile: String,
            filesPath: String,
            reportPath: String,
        ) {
            val gradlew = runnerDir.resolve(
                if (System.getProperty("os.name", "").lowercase().contains("win")) "gradlew.bat" else "gradlew"
            )
            val exe = if (gradlew.exists()) gradlew.toString() else "gradle"

            val cmd = buildList {
                add(exe)
                add("runJ2kHeadless")
                add("-Pj2k.sourceRoot=$sourceRoot")
                add("-Pj2k.outputRoot=$outputRoot")
                if (classpathFile.isNotBlank()) add("-Pj2k.classpathFile=$classpathFile")
                if (filesPath.isNotBlank())     add("-Pj2k.files=$filesPath")
                add("-Pj2k.report=$reportPath")
                add("--no-daemon")
            }

            println("  [headless-runner] ${cmd.joinToString(" ")}")

            val proc = ProcessBuilder(cmd)
                .directory(runnerDir.toFile())
                .inheritIO()
                .start()

            val ok = proc.waitFor(1800, TimeUnit.SECONDS) && proc.exitValue() == 0
            check(ok) {
                "Headless J2K runner exited with code ${proc.exitValue()}. " +
                "Check the output above for details."
            }
        }
    }
}

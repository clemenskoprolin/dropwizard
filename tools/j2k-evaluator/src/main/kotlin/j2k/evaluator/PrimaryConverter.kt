package j2k.evaluator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class PrimaryConverter(private val opts: Map<String, String>) {

    fun run() {
        val sourceDir  = Paths.get(opts.require("source"))
        val outputDir  = Paths.get(opts.require("output"))
        val j2kBin     = opts.opt("j2k-bin")
        val classpath  = opts.opt("classpath")
        outputDir.createDirectories()

        val javaFiles = findByExtension(sourceDir, "java")
        println("convert-primary: ${javaFiles.size} Java files in $sourceDir (kotlinc $KOTLINC_VERSION)")

        val result = if (j2kBin.isBlank()) {
            println("WARNING: --j2k-bin not supplied; writing stub .kt files (set J2K_BIN in CI)")
            stubConvert(javaFiles, sourceDir, outputDir)
        } else {
            val j2kPath = Paths.get(j2kBin)
            check(j2kPath.exists() && j2kPath.isExecutable()) {
                "j2k binary not found or not executable: $j2kBin"
            }
            j2kConvert(j2kPath, javaFiles, sourceDir, outputDir, classpath)
        }

        val reportsDir = outputDir.parent?.parent?.resolve("reports") ?: outputDir.resolve("../../reports")
        ReportWriter.writeConversionJson(result, reportsDir)
        println("Converted ${result.convertedCount}/${result.javaFileCount}, failed ${result.failedCount}")
    }

    private fun j2kConvert(
        j2kBin: Path,
        javaFiles: List<Path>,
        sourceRoot: Path,
        outputRoot: Path,
        classpath: String,
    ): ConversionResult {
        val workDir = Files.createTempDirectory("j2k-primary")
        var converted = 0
        var failed = 0

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

    private fun stubConvert(
        javaFiles: List<Path>,
        sourceRoot: Path,
        outputRoot: Path
    ): ConversionResult {
        for (javaFile in javaFiles) {
            val relative = sourceRoot.relativize(javaFile)
            val destDir = outputRoot.resolve(relative.parent ?: Paths.get(""))
            destDir.createDirectories()
            destDir.resolve("${javaFile.nameWithoutExtension}.kt")
                .writeText("// STUB: j2k binary not available — supply --j2k-bin\n")
        }
        return ConversionResult(KOTLINC_VERSION, javaFiles.size, 0, 0, skipped = true, skipReason = "j2k binary not provided")
    }

    companion object {
        fun findByExtension(root: Path, ext: String): List<Path> =
            root.toFile().walk()
                .filter { it.isFile && it.extension == ext }
                .map { it.toPath() }
                .toList()
    }
}

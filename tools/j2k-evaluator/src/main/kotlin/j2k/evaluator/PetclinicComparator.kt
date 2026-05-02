package j2k.evaluator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class PetclinicComparator(private val opts: Map<String, String>) {

    private companion object {
        const val PETCLINIC_JAVA   = "https://github.com/spring-projects/spring-petclinic.git"
        const val PETCLINIC_KOTLIN = "https://github.com/spring-petclinic/spring-petclinic-kotlin.git"
    }

    fun run() {
        val j2kBin    = opts.opt("j2k-bin")
        val outputDir = Paths.get(opts.require("output"))
        outputDir.createDirectories()

        val workDir = Files.createTempDirectory("petclinic-work")
        try {
            val javaRepoDir    = workDir.resolve("java")
            val kotlinRepoDir  = workDir.resolve("kotlin")
            val convertedDir   = workDir.resolve("converted")

            println("Cloning spring-petclinic (Java)…")
            if (!cloneRepo(PETCLINIC_JAVA, javaRepoDir)) {
                println("WARNING: clone failed for $PETCLINIC_JAVA; skipping Petclinic benchmark")
                ReportWriter.writePetclinicSkipped("clone failed (java)", outputDir)
                return
            }

            println("Cloning spring-petclinic-kotlin (official Kotlin)…")
            if (!cloneRepo(PETCLINIC_KOTLIN, kotlinRepoDir)) {
                println("WARNING: clone failed for $PETCLINIC_KOTLIN; skipping Petclinic benchmark")
                ReportWriter.writePetclinicSkipped("clone failed (kotlin)", outputDir)
                return
            }

            val javaSrc = javaRepoDir.resolve("src/main/java")
            convertedDir.createDirectories()

            if (j2kBin.isNotBlank()) {
                val j2kPath = Paths.get(j2kBin)
                if (j2kPath.exists() && j2kPath.isExecutable()) {
                    convertTree(j2kPath, javaSrc, convertedDir)
                }
            }

            val convertedKt = findKtFiles(convertedDir).filter { isRealKotlin(it.readText()) }
            val officialKt  = findKtFiles(kotlinRepoDir.resolve("src/main/kotlin"))

            val result = compare(convertedKt, officialKt)
            ReportWriter.writePetclinicResult(result, outputDir)
            println("Petclinic comparison done — class match: ${"%.1f".format(result.classNameMatchPct)}%")
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private fun convertTree(j2kBin: Path, sourceRoot: Path, outputRoot: Path) {
        val javaFiles = sourceRoot.toFile().walk()
            .filter { it.isFile && it.extension == "java" }
            .map { it.toPath() }
            .toList()

        for (javaFile in javaFiles) {
            val workDir = Files.createTempDirectory("j2k-pc-file")
            try {
                val workCopy = workDir.resolve(javaFile.name)
                Files.copy(javaFile, workCopy)

                val proc = ProcessBuilder(j2kBin.toString(), workCopy.toString())
                    .redirectErrorStream(true)
                    .start()
                proc.waitFor(60, TimeUnit.SECONDS)

                val ktWork = workDir.resolve("${javaFile.nameWithoutExtension}.kt")
                if (ktWork.exists()) {
                    val relative = sourceRoot.relativize(javaFile)
                    val dest = outputRoot.resolve(relative.parent ?: Paths.get(""))
                        .resolve("${javaFile.nameWithoutExtension}.kt")
                    dest.parent?.createDirectories()
                    Files.copy(ktWork, dest)
                }
            } finally {
                workDir.toFile().deleteRecursively()
            }
        }
    }

    private fun compare(
        converted: List<Path>,
        official: List<Path>
    ): PetclinicComparisonResult {
        val convClasses  = extractClassNames(converted)
        val offClasses   = extractClassNames(official)
        val convPkgs     = extractPackages(converted)
        val offPkgs      = extractPackages(official)
        val convAnnots   = countAnnotations(converted)
        val offAnnots    = countAnnotations(official)

        val matched    = convClasses.intersect(offClasses).size
        val classPct   = pct(matched, offClasses.size)
        val pkgPct     = pct(convPkgs.intersect(offPkgs).size, offPkgs.size)
        val annotPct   = if (offAnnots == 0) 100.0 else pct(minOf(convAnnots, offAnnots), offAnnots)

        return PetclinicComparisonResult(
            variant                = KOTLINC_VERSION,
            convertedClassCount    = convClasses.size,
            officialClassCount     = offClasses.size,
            matchedClassCount      = matched,
            convertedPackageCount  = convPkgs.size,
            officialPackageCount   = offPkgs.size,
            convertedAnnotationCount = convAnnots,
            officialAnnotationCount  = offAnnots,
            classNameMatchPct      = classPct,
            packageMatchPct        = pkgPct,
            annotationParityPct    = annotPct
        )
    }

    private fun extractClassNames(files: List<Path>): Set<String> =
        files.flatMap { f ->
            Regex("""\b(?:class|interface|object)\s+(\w+)""")
                .findAll(f.readText()).map { it.groupValues[1] }
        }.toSet()

    private fun extractPackages(files: List<Path>): Set<String> =
        files.mapNotNull { f ->
            Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
                .find(f.readText())?.groupValues?.get(1)
        }.toSet()

    private fun countAnnotations(files: List<Path>): Int =
        files.sumOf { Regex("""@[A-Z][A-Za-z0-9_]*""").findAll(it.readText()).count() }

    private fun pct(num: Int, den: Int): Double =
        if (den == 0) 0.0 else num.toDouble() / den * 100.0

    private fun cloneRepo(url: String, dest: Path): Boolean {
        val proc = ProcessBuilder("git", "clone", "--depth=1", url, dest.toString())
            .inheritIO()
            .start()
        return proc.waitFor(300, TimeUnit.SECONDS) && proc.exitValue() == 0
    }

    private fun findKtFiles(root: Path): List<Path> =
        if (!root.exists()) emptyList()
        else root.toFile().walk().filter { it.isFile && it.extension == "kt" }.map { it.toPath() }.toList()

    private fun isRealKotlin(text: String) =
        !text.startsWith("// STUB") && !text.startsWith("// CONVERSION FAILED")
}

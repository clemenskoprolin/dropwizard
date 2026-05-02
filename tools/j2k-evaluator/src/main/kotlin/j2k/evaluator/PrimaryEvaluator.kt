package j2k.evaluator

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class PrimaryEvaluator(private val opts: Map<String, String>) {

    fun run() {
        val sourceDir    = Paths.get(opts.require("source"))
        val convertedDir = Paths.get(opts.require("converted"))
        val outputDir    = Paths.get(opts.require("output"))
        outputDir.createDirectories()

        val javaFiles = findByExtension(sourceDir, "java")
        val allKtFiles = findByExtension(convertedDir, "kt")
        val ktFiles = allKtFiles.filter { isRealKotlin(it.readText()) }

        println("evaluate-primary: ${javaFiles.size} Java files → ${ktFiles.size} usable Kotlin files (kotlinc $KOTLINC_VERSION)")

        val javaLoc   = javaFiles.sumOf { nonBlankLines(it) }
        val kotlinLoc = ktFiles.sumOf   { nonBlankLines(it) }

        val javaStruct   = analyzeJava(javaFiles)
        val kotlinStruct = analyzeKotlin(ktFiles)
        val heuristics   = computeHeuristics(ktFiles)
        val hotspots     = identifyHotspots(ktFiles, convertedDir)

        val conversionResult = ConversionResult(
            variant        = KOTLINC_VERSION,
            javaFileCount  = javaFiles.size,
            convertedCount = ktFiles.size,
            failedCount    = allKtFiles.size - ktFiles.size
        )

        val result = PrimaryEvaluationResult(
            variant          = KOTLINC_VERSION,
            javaLoc          = javaLoc,
            kotlinLoc        = kotlinLoc,
            conversion       = conversionResult,
            javaStructural   = javaStruct,
            kotlinStructural = kotlinStruct,
            kotlinHeuristics = heuristics,
            hotspots         = hotspots
        )

        ReportWriter.writePrimaryResult(result, outputDir)
        println("Evaluation complete — reports in $outputDir")
    }

    private fun analyzeJava(files: List<Path>): StructuralMetrics {
        var classes = 0; var interfaces = 0; var enums = 0
        var annotations = 0; var publicMethods = 0

        for (f in files) {
            val text = f.readText()
            classes       += count(text, Regex("""^\s*(public\s+)?(abstract\s+|final\s+)*(class)\s+\w+""", RegexOption.MULTILINE))
            interfaces    += count(text, Regex("""^\s*(public\s+)?interface\s+\w+""", RegexOption.MULTILINE))
            enums         += count(text, Regex("""^\s*(public\s+)?enum\s+\w+""", RegexOption.MULTILINE))
            annotations   += count(text, Regex("""@[A-Z][A-Za-z0-9_]*"""))
            publicMethods += count(text, Regex("""^\s+public\s+(?!class|interface|enum)\S+\s+\w+\s*\(""", RegexOption.MULTILINE))
        }
        return StructuralMetrics(classes, interfaces, enums, annotations, publicMethods)
    }

    private fun analyzeKotlin(files: List<Path>): StructuralMetrics {
        var classes = 0; var interfaces = 0; var enums = 0
        var annotations = 0; var publicFuns = 0

        for (f in files) {
            val text = f.readText()
            classes       += count(text, Regex("""^\s*(data\s+|sealed\s+|abstract\s+|open\s+)*class\s+\w+""", RegexOption.MULTILINE))
            interfaces    += count(text, Regex("""^\s*(sealed\s+|fun\s+)?interface\s+\w+""", RegexOption.MULTILINE))
            enums         += count(text, Regex("""^\s*enum\s+class\s+\w+""", RegexOption.MULTILINE))
            annotations   += count(text, Regex("""@[A-Z][A-Za-z0-9_]*"""))
            publicFuns    += count(text, Regex("""^\s*(override\s+)?(public\s+)?fun\s+\w+""", RegexOption.MULTILINE))
        }
        return StructuralMetrics(classes, interfaces, enums, annotations, publicFuns)
    }

    private fun computeHeuristics(files: List<Path>): KotlinHeuristicMetrics {
        var parseOk = 0; var parseFail = 0
        var unsafeCalls = 0; var unsafeCasts = 0
        var todos = 0; var dataClasses = 0; var objects = 0

        for (f in files) {
            val text = f.readText()
            if (looksLikeValidKotlin(text)) parseOk++ else parseFail++
            unsafeCalls += count(text, Regex("""!!"""))
            unsafeCasts += count(text, Regex("""\bas\s+[A-Z][A-Za-z0-9_]*"""))
            todos       += count(text, Regex("""//\s*(TODO|FIXME|HACK)\b"""))
            dataClasses += count(text, Regex("""^\s*data\s+class\s+\w+""", RegexOption.MULTILINE))
            objects     += count(text, Regex("""^\s*(companion\s+)?object(\s+\w+)?\s*[{(]""", RegexOption.MULTILINE))
        }

        return KotlinHeuristicMetrics(parseOk, parseFail, unsafeCalls, unsafeCasts, todos, dataClasses, objects)
    }

    private fun identifyHotspots(files: List<Path>, root: Path): List<HotspotFile> =
        files.mapNotNull { f ->
            val text   = f.readText()
            val unsafe = count(text, Regex("""!!"""))
            val casts  = count(text, Regex("""\bas\s+[A-Z][A-Za-z0-9_]*"""))
            val todos  = count(text, Regex("""//\s*(TODO|FIXME)\b"""))
            if (unsafe + casts + todos > 0)
                HotspotFile(root.relativize(f).toString(), unsafe, casts, todos)
            else null
        }.sortedByDescending { it.unsafeCalls + it.unsafeCasts + it.todoComments }

    private fun looksLikeValidKotlin(text: String): Boolean {
        if (text.isBlank() || text.startsWith("// STUB") || text.startsWith("// CONVERSION FAILED")) return false
        return Regex("""\b(package|import|class|fun|object|interface|val|var)\b""").containsMatchIn(text)
    }

    private fun nonBlankLines(f: Path) = f.readText().lines().count { it.isNotBlank() }
    private fun count(text: String, regex: Regex) = regex.findAll(text).count()

    private fun findByExtension(root: Path, ext: String): List<Path> =
        root.toFile().walk()
            .filter { it.isFile && it.extension == ext }
            .map { it.toPath() }
            .toList()

    private fun isRealKotlin(text: String) =
        !text.startsWith("// STUB") && !text.startsWith("// CONVERSION FAILED")
}

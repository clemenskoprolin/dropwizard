package j2k.evaluator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class EdgeCaseRunner(private val opts: Map<String, String>) {

    fun run() {
        val datasetDir = Paths.get(opts.require("dataset"))
        val j2kBin     = opts.opt("j2k-bin")
        val outputDir  = Paths.get(opts.require("output"))
        outputDir.createDirectories()

        val hypotheses = loadHypotheses(datasetDir.resolve("hypotheses.json"))
        println("run-edge-cases: ${hypotheses.size} hypotheses loaded (kotlinc $KOTLINC_VERSION)")

        val j2kPath = if (j2kBin.isNotBlank()) Paths.get(j2kBin) else null
        val casesDir = datasetDir.resolve("cases")

        val results = hypotheses.map { hyp ->
            val javaFile = casesDir.resolve(hyp.file)
            if (!javaFile.exists()) {
                println("  [SKIP] ${hyp.id}: ${hyp.file} not found in $casesDir")
                return@map EdgeCaseResult(hyp.id, hyp.file, hyp.hypothesis,
                    passed = false, failureNote = "source file not found",
                    unsafeCallCount = 0, unsafeCastCount = 0, conversionSucceeded = false)
            }
            val r = evaluateCase(hyp, javaFile, j2kPath)
            val status = if (r.passed) "PASS" else "FAIL"
            println("  [$status] ${hyp.id}: ${hyp.file} — ${r.failureNote.ifBlank { "ok" }}")
            r
        }

        ReportWriter.writeEdgeCaseResults(results, hypotheses, outputDir)
        val passed = results.count { it.passed }
        println("Edge cases: $passed/${results.size} passed")
    }

    private fun evaluateCase(
        hyp: EdgeCaseHypothesis,
        javaFile: Path,
        j2kBin: Path?
    ): EdgeCaseResult {
        val workDir = Files.createTempDirectory("ec-${hyp.id}")
        return try {
            val workFile = workDir.resolve(javaFile.name)
            Files.copy(javaFile, workFile)

            var conversionOk = false
            var ktText = ""

            if (j2kBin != null) {
                val proc = ProcessBuilder(j2kBin.toString(), workFile.toString())
                    .redirectErrorStream(true)
                    .start()
                conversionOk = proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0
                val ktFile = workDir.resolve("${javaFile.nameWithoutExtension}.kt")
                ktText = if (ktFile.exists()) ktFile.readText() else ""
            }

            val unsafeCalls = Regex("""!!""").findAll(ktText).count()
            val unsafeCasts = Regex("""\bas\s+[A-Z][A-Za-z0-9_]*""").findAll(ktText).count()
            val hasTodo     = Regex("""//\s*(TODO|FIXME)\b""").containsMatchIn(ktText)

            val passed = j2kBin != null
                && conversionOk
                && ktText.isNotBlank()
                && unsafeCalls == 0
                && !ktText.startsWith("// CONVERSION FAILED")

            val failureNote = buildList {
                if (j2kBin == null)      add("j2k binary not provided")
                if (!conversionOk)       add("conversion process failed")
                if (ktText.isBlank())    add("empty output")
                if (unsafeCalls > 0)     add("$unsafeCalls unsafe-call(s) (!!) in output")
                if (unsafeCasts > 0)     add("$unsafeCasts unsafe cast(s) (as T) in output")
                if (hasTodo)             add("manual cleanup markers present")
            }.joinToString("; ")

            EdgeCaseResult(hyp.id, hyp.file, hyp.hypothesis, passed, failureNote,
                unsafeCalls, unsafeCasts, conversionOk)
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private fun loadHypotheses(file: Path): List<EdgeCaseHypothesis> {
        check(file.exists()) { "Hypotheses file not found: $file" }
        val text = file.readText()
        return Regex("""\{[^{}]+\}""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(text)
            .map { m ->
                val obj = m.value
                EdgeCaseHypothesis(
                    id           = jsonStr(obj, "id"),
                    file         = jsonStr(obj, "file"),
                    category     = jsonStr(obj, "category"),
                    hypothesis   = jsonStr(obj, "hypothesis"),
                    expectedIssues = jsonStrArray(obj, "expectedIssues")
                )
            }
            .filter { it.id.isNotBlank() }
            .toList()
    }

    private fun jsonStr(obj: String, key: String): String =
        Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(obj)
            ?.groupValues?.get(1)?.unescape() ?: ""

    private fun jsonStrArray(obj: String, key: String): List<String> {
        val m = Regex(""""$key"\s*:\s*\[([^\]]*)\]""").find(obj) ?: return emptyList()
        return Regex(""""([^"]+)"""").findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private fun String.unescape(): String =
        replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
}

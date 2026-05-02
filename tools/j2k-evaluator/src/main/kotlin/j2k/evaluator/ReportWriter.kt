package j2k.evaluator

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

object ReportWriter {

    private val timestamp: String
        get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    // --- conversion ---

    fun writeConversionJson(result: ConversionResult, outputDir: Path) {
        outputDir.createDirectories()
        outputDir.resolve("conversion-result.json").writeText(
            """
            {
              "generatedAt": "${timestamp.esc()}",
              "variant": "${result.variant.esc()}",
              "javaFileCount": ${result.javaFileCount},
              "convertedCount": ${result.convertedCount},
              "failedCount": ${result.failedCount},
              "skipped": ${result.skipped},
              "skipReason": "${result.skipReason.esc()}"
            }
            """.trimIndent()
        )
    }

    // --- primary evaluation ---

    fun writePrimaryResult(r: PrimaryEvaluationResult, outputDir: Path) {
        outputDir.resolve("primary-result.json").writeText(buildPrimaryJson(r))
        outputDir.resolve("primary-summary.md").writeText(buildPrimaryMd(r))
        println("Written primary-result.json + primary-summary.md to $outputDir")
    }

    private fun buildPrimaryJson(r: PrimaryEvaluationResult): String = """
        {
          "generatedAt": "${timestamp.esc()}",
          "variant": "${r.variant.esc()}",
          "javaLoc": ${r.javaLoc},
          "kotlinLoc": ${r.kotlinLoc},
          "conversion": {
            "javaFileCount": ${r.conversion.javaFileCount},
            "convertedCount": ${r.conversion.convertedCount},
            "failedCount": ${r.conversion.failedCount},
            "skipped": ${r.conversion.skipped}
          },
          "javaStructural": {
            "classCount": ${r.javaStructural.classCount},
            "interfaceCount": ${r.javaStructural.interfaceCount},
            "enumCount": ${r.javaStructural.enumCount},
            "annotationCount": ${r.javaStructural.annotationCount},
            "publicMethodCount": ${r.javaStructural.publicMethodCount}
          },
          "kotlinStructural": {
            "classCount": ${r.kotlinStructural.classCount},
            "interfaceCount": ${r.kotlinStructural.interfaceCount},
            "enumCount": ${r.kotlinStructural.enumCount},
            "annotationCount": ${r.kotlinStructural.annotationCount},
            "publicMethodCount": ${r.kotlinStructural.publicMethodCount}
          },
          "kotlinHeuristics": {
            "parseSuccessCount": ${r.kotlinHeuristics.parseSuccessCount},
            "parseFailureCount": ${r.kotlinHeuristics.parseFailureCount},
            "unsafeCallCount": ${r.kotlinHeuristics.unsafeCallCount},
            "unsafeCastCount": ${r.kotlinHeuristics.unsafeCastCount},
            "todoCommentCount": ${r.kotlinHeuristics.todoCommentCount},
            "dataClassCount": ${r.kotlinHeuristics.dataClassCount},
            "objectCount": ${r.kotlinHeuristics.objectCount}
          }
        }
    """.trimIndent()

    private fun buildPrimaryMd(r: PrimaryEvaluationResult): String {
        val locRatio   = if (r.javaLoc > 0) "%.2f".format(r.kotlinLoc.toDouble() / r.javaLoc) else "n/a"
        val classPct   = pct(r.kotlinStructural.classCount,      r.javaStructural.classCount)
        val methodPct  = pct(r.kotlinStructural.publicMethodCount, r.javaStructural.publicMethodCount)

        val hotspotsSection = if (r.hotspots.isEmpty()) "" else buildString {
            appendLine()
            appendLine("### Cleanup hotspots (top ${minOf(r.hotspots.size, 10)})")
            appendLine()
            appendLine("| File | `!!` | `as T` | TODO |")
            appendLine("|---|---|---|---|")
            r.hotspots.take(10).forEach { h ->
                appendLine("| `${h.path}` | ${h.unsafeCalls} | ${h.unsafeCasts} | ${h.todoComments} |")
            }
        }

        return """
            ## J2K Evaluation — Primary Benchmark (`dropwizard-example`)

            | Metric | Value |
            |---|---|
            | Converter variant | `${r.variant}` |
            | Java files | ${r.conversion.javaFileCount} |
            | Converted successfully | ${r.conversion.convertedCount} |
            | Conversion failures | ${r.conversion.failedCount} |
            | Java LOC (non-blank) | ${r.javaLoc} |
            | Kotlin LOC (non-blank) | ${r.kotlinLoc} |
            | LOC ratio (Kotlin/Java) | $locRatio |

            ### Structural parity

            | Declaration | Java | Kotlin | Parity |
            |---|---|---|---|
            | Classes | ${r.javaStructural.classCount} | ${r.kotlinStructural.classCount} | $classPct |
            | Interfaces | ${r.javaStructural.interfaceCount} | ${r.kotlinStructural.interfaceCount} | — |
            | Enums | ${r.javaStructural.enumCount} | ${r.kotlinStructural.enumCount} | — |
            | Public methods/funs | ${r.javaStructural.publicMethodCount} | ${r.kotlinStructural.publicMethodCount} | $methodPct |
            | Annotations retained | — | ${r.kotlinStructural.annotationCount} | — |

            ### Kotlin quality heuristics

            | Heuristic | Count |
            |---|---|
            | Parse-success files | ${r.kotlinHeuristics.parseSuccessCount} |
            | Parse-failure files | ${r.kotlinHeuristics.parseFailureCount} |
            | Unsafe-call operators (`!!`) | ${r.kotlinHeuristics.unsafeCallCount} |
            | Unsafe casts (`as T`) | ${r.kotlinHeuristics.unsafeCastCount} |
            | Manual-cleanup markers (TODO/FIXME) | ${r.kotlinHeuristics.todoCommentCount} |
            | Data classes generated | ${r.kotlinHeuristics.dataClassCount} |
            | Objects generated | ${r.kotlinHeuristics.objectCount} |
            $hotspotsSection
            _Generated at ${timestamp}_
        """.trimIndent()
    }

    // --- petclinic ---

    fun writePetclinicResult(r: PetclinicComparisonResult, outputDir: Path) {
        outputDir.resolve("petclinic-result.json").writeText(
            """
            {
              "generatedAt": "${timestamp.esc()}",
              "variant": "${r.variant.esc()}",
              "convertedClassCount": ${r.convertedClassCount},
              "officialClassCount": ${r.officialClassCount},
              "matchedClassCount": ${r.matchedClassCount},
              "convertedPackageCount": ${r.convertedPackageCount},
              "officialPackageCount": ${r.officialPackageCount},
              "convertedAnnotationCount": ${r.convertedAnnotationCount},
              "officialAnnotationCount": ${r.officialAnnotationCount},
              "classNameMatchPct": ${"%.4f".format(r.classNameMatchPct)},
              "packageMatchPct": ${"%.4f".format(r.packageMatchPct)},
              "annotationParityPct": ${"%.4f".format(r.annotationParityPct)}
            }
            """.trimIndent()
        )
        outputDir.resolve("petclinic-summary.md").writeText(buildPetclinicMd(r))
    }

    fun writePetclinicSkipped(reason: String, outputDir: Path) {
        outputDir.resolve("petclinic-summary.md").writeText(
            "## J2K Evaluation — Secondary Benchmark (spring-petclinic)\n\n_Skipped: ${reason}_\n"
        )
    }

    private fun buildPetclinicMd(r: PetclinicComparisonResult): String = """
        ## J2K Evaluation — Secondary Benchmark (spring-petclinic)

        Comparing J2K-converted `spring-petclinic` Java sources against the official
        `spring-petclinic/spring-petclinic-kotlin` repository using structural signatures.

        | Metric | Converted (J2K) | Official Kotlin | Match % |
        |---|---|---|---|
        | Packages | ${r.convertedPackageCount} | ${r.officialPackageCount} | ${"%.1f".format(r.packageMatchPct)}% |
        | Classes / interfaces | ${r.convertedClassCount} | ${r.officialClassCount} | ${"%.1f".format(r.classNameMatchPct)}% |
        | Annotations | ${r.convertedAnnotationCount} | ${r.officialAnnotationCount} | ${"%.1f".format(r.annotationParityPct)}% |

        _Generated at ${timestamp}_
    """.trimIndent()

    // --- edge cases ---

    fun writeEdgeCaseResults(results: List<EdgeCaseResult>, hypotheses: List<EdgeCaseHypothesis>, outputDir: Path) {
        val hypMap = hypotheses.associateBy { it.id }

        val jsonItems = results.joinToString(",\n  ") { r ->
            """
            {
              "id": "${r.id.esc()}",
              "file": "${r.file.esc()}",
              "passed": ${r.passed},
              "failureNote": "${r.failureNote.esc()}",
              "unsafeCallCount": ${r.unsafeCallCount},
              "unsafeCastCount": ${r.unsafeCastCount},
              "conversionSucceeded": ${r.conversionSucceeded}
            }""".trimIndent()
        }
        outputDir.resolve("edge-cases-result.json").writeText("[\n  $jsonItems\n]")
        outputDir.resolve("edge-cases-summary.md").writeText(buildEdgeCasesMd(results, hypMap))
    }

    private fun buildEdgeCasesMd(
        results: List<EdgeCaseResult>,
        hypMap: Map<String, EdgeCaseHypothesis>
    ): String {
        val passed = results.count { it.passed }
        val rows = results.joinToString("\n") { r ->
            val status   = if (r.passed) "✓ pass" else "✗ fail"
            val category = hypMap[r.id]?.category ?: "—"
            val note     = r.failureNote.ifBlank { "—" }
            "| `${r.id}` | `${r.file}` | $category | $status | ${r.hypothesis.take(80)} | $note |"
        }

        return """
            ## J2K Evaluation — Edge-Case Dataset

            **$passed / ${results.size} cases passed**

            | ID | File | Category | Result | Hypothesis (truncated) | Failure note |
            |---|---|---|---|---|---|
            $rows

            _Generated at ${timestamp}_
        """.trimIndent()
    }

    // --- helpers ---

    private fun pct(num: Int, den: Int): String =
        if (den == 0) "n/a" else "${num * 100 / den}%"

    private fun String.esc(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
}

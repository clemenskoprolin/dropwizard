package j2k.evaluator

import kotlin.system.exitProcess

const val KOTLINC_VERSION = "2.1.20"
const val HEADLESS_RUNNER_VARIANT = "headless-j2k"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        exitProcess(1)
    }

    val subcommand = args[0]
    val opts = parseOptions(args.drop(1))

    when (subcommand) {
        "convert-primary"   -> PrimaryConverter(opts).run()
        "evaluate-primary"  -> PrimaryEvaluator(opts).run()
        "compare-petclinic" -> PetclinicComparator(opts).run()
        "run-edge-cases"    -> EdgeCaseRunner(opts).run()
        "--help", "-h", "help" -> { printHelp(); exitProcess(0) }
        else -> {
            System.err.println("Unknown subcommand: $subcommand")
            printHelp()
            exitProcess(1)
        }
    }
}

fun parseOptions(args: List<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        if (key.startsWith("--") && i + 1 < args.size && !args[i + 1].startsWith("--")) {
            result[key.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return result
}

fun Map<String, String>.require(key: String): String =
    this[key] ?: error("Missing required option: --$key")

fun Map<String, String>.opt(key: String, default: String = ""): String =
    this[key] ?: default

private fun printHelp() {
    println(
        """
        j2k-evaluator — JetBrains J2K Evaluation Toolchain (dropwizard fork)

        Usage: j2k-evaluator <subcommand> [options]

        Subcommands:
          convert-primary    Convert dropwizard-example Java sources via J2K
          evaluate-primary   Score the converted Kotlin sources
          compare-petclinic  Secondary benchmark vs official spring-petclinic-kotlin
          run-edge-cases     Custom edge-case dataset pass/fail report

        Common options:
          --j2k-bin <path>           Absolute path to the kotlinc j2k binary
          --headless-runner-dir <dir> Path to tools/headless-j2k-runner (preferred)
          --output <dir>             Output directory for reports and converted sources

          At least one of --j2k-bin or --headless-runner-dir must be supplied.
          --headless-runner-dir takes precedence when both are given.

        convert-primary options:
          --source <dir>       dropwizard-example/src root
          --classpath <cp>     Colon-separated classpath (used with --j2k-bin)
          --classpath-file <f> File containing Maven classpath (used with --headless-runner-dir)

        evaluate-primary options:
          --source <dir>       Original Java source root (dropwizard-example/src)
          --converted <dir>    Directory with converted .kt files

        compare-petclinic — clones repos at runtime, no extra required options

        run-edge-cases options:
          --dataset <dir>      Path to benchmarks/edge-cases
        """.trimIndent()
    )
}

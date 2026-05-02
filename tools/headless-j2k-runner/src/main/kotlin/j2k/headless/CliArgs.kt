package j2k.headless

import java.io.File

data class CliArgs(
    val sourceRoot: String,
    val outputRoot: String,
    /** Path to a file whose content is a colon-separated (Unix) Maven classpath.  Blank = no classpath. */
    val classpathFile: String,
    /** Path to a newline-delimited file listing Java source paths to convert.  Blank = convert all under sourceRoot. */
    val filesListPath: String,
    /** Destination path for the conversion-result JSON report. */
    val reportPath: String,
) {
    val classpathEntries: List<String>
        get() {
            if (classpathFile.isBlank()) return emptyList()
            val content = File(classpathFile).readText().trim()
            return content.split(File.pathSeparatorChar).filter { it.isNotBlank() }
        }

    val targetFiles: List<String>
        get() {
            if (filesListPath.isBlank()) return emptyList()
            return File(filesListPath).readLines().map { it.trim() }.filter { it.isNotBlank() }
        }

    companion object {
        fun parse(args: List<String>): CliArgs {
            val map = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val key = args[i]
                if (key.startsWith("--") && i + 1 < args.size) {
                    map[key.removePrefix("--")] = args[i + 1]
                    i += 2
                } else {
                    i++
                }
            }
            fun require(key: String) = map[key] ?: error("Missing required argument: --$key")
            return CliArgs(
                sourceRoot    = require("source-root"),
                outputRoot    = require("output-root"),
                classpathFile = map["classpath-file"] ?: "",
                filesListPath = map["files"]          ?: "",
                reportPath    = require("report"),
            )
        }
    }
}

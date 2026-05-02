package j2k.headless

import org.junit.Assert.*
import org.junit.Test

class CliArgsTest {

    @Test
    fun `parse required args only`() {
        val a = CliArgs.parse(listOf("--source-root", "/src", "--output-root", "/out", "--report", "/r.json"))
        assertEquals("/src",   a.sourceRoot)
        assertEquals("/out",   a.outputRoot)
        assertEquals("/r.json", a.reportPath)
        assertEquals("",       a.classpathFile)
        assertEquals("",       a.filesListPath)
    }

    @Test
    fun `parse all args`() {
        val a = CliArgs.parse(listOf(
            "--source-root",    "/src",
            "--output-root",    "/out",
            "--classpath-file", "/cp.txt",
            "--files",          "/files.txt",
            "--report",         "/r.json",
        ))
        assertEquals("/cp.txt",    a.classpathFile)
        assertEquals("/files.txt", a.filesListPath)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing source-root throws`() {
        CliArgs.parse(listOf("--output-root", "/out", "--report", "/r.json"))
    }

    @Test(expected = IllegalStateException::class)
    fun `missing output-root throws`() {
        CliArgs.parse(listOf("--source-root", "/src", "--report", "/r.json"))
    }

    @Test(expected = IllegalStateException::class)
    fun `missing report throws`() {
        CliArgs.parse(listOf("--source-root", "/src", "--output-root", "/out"))
    }

    @Test
    fun `targetFiles empty when filesListPath blank`() {
        val a = CliArgs.parse(listOf("--source-root", "/s", "--output-root", "/o", "--report", "/r.json"))
        assertTrue(a.targetFiles.isEmpty())
    }

    @Test
    fun `classpathEntries empty when classpathFile blank`() {
        val a = CliArgs.parse(listOf("--source-root", "/s", "--output-root", "/o", "--report", "/r.json"))
        assertTrue(a.classpathEntries.isEmpty())
    }

    @Test
    fun `unknown flags are silently ignored`() {
        val a = CliArgs.parse(listOf(
            "--source-root", "/s",
            "--output-root", "/o",
            "--report",      "/r.json",
            "--unknown-flag", "value",
        ))
        assertEquals("/s", a.sourceRoot)
    }
}

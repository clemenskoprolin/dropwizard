package j2k.headless

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension

/**
 * Smoke test: converts a trivial Java class through J2K inside the IDEA test harness
 * and asserts a non-empty Kotlin file is produced.
 *
 * Run via: ./gradlew test
 */
@Suppress("UnstableApiUsage")
class ConversionSmokeTest : BasePlatformTestCase() {

    fun `test converts simple Java class to Kotlin`() {
        val javaSource = """
            public class Greeter {
                private final String prefix;

                public Greeter(String prefix) {
                    this.prefix = prefix;
                }

                public String greet(String name) {
                    return prefix + ", " + name + "!";
                }
            }
        """.trimIndent()

        val psiFile = myFixture.configureByText("Greeter.java", javaSource)
            as PsiJavaFile

        val extension = J2kConverterExtension.extension(J2kConverterExtension.Kind.K1_NEW)
        val converter = extension.createJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        val result = converter.filesToKotlin(
            listOf(psiFile),
            extension.createPostProcessor(formatCode = false),
            EmptyProgressIndicator(),
            emptyList(),
            emptyList(),
        )

        val ktText = result.results.firstOrNull() as? String
            ?: error("filesToKotlin returned no results")

        assertFalse("Converted text must be non-empty", ktText.isBlank())
        assertTrue(
            "Converted text should contain 'fun' keyword (got: $ktText)",
            ktText.contains("fun ")
        )
        assertFalse(
            "Converted text must not start with '// CONVERSION FAILED'",
            ktText.startsWith("// CONVERSION FAILED")
        )
    }

    fun `test does not produce stub output`() {
        val javaSource = "public class Empty {}"
        val psiFile = myFixture.configureByText("Empty.java", javaSource)
            as PsiJavaFile

        val extension = J2kConverterExtension.extension(J2kConverterExtension.Kind.K1_NEW)
        val converter = extension.createJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        val result = converter.filesToKotlin(
            listOf(psiFile),
            extension.createPostProcessor(formatCode = false),
            EmptyProgressIndicator(),
            emptyList(),
            emptyList(),
        )

        val ktText = result.results.firstOrNull() as? String ?: ""
        assertFalse("Output must not be a stub", ktText.startsWith("// STUB"))
    }
}

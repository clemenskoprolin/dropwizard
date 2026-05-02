package j2k.headless

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

class ConversionRunner(private val args: CliArgs) {

    private val sourceRoot = Paths.get(args.sourceRoot).toAbsolutePath()
    private val outputRoot = Paths.get(args.outputRoot).toAbsolutePath()

    /** Returns the number of files that failed conversion. */
    fun run(): Int {
        outputRoot.createDirectories()

        val javaFiles: List<Path> = if (args.targetFiles.isEmpty()) {
            walkJavaFiles(sourceRoot)
        } else {
            args.targetFiles.map { Paths.get(it).toAbsolutePath() }
        }

        println("j2k-headless: ${javaFiles.size} Java files from $sourceRoot")

        val project = openHeadlessProject()
        val module  = setupModule(project, sourceRoot, args.classpathEntries)

        // Force a VFS refresh so PSI can resolve newly registered roots.
        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
        }

        val psiFiles: List<PsiJavaFile> = ReadAction.compute<List<PsiJavaFile>, Throwable> {
            javaFiles.mapNotNull { path ->
                val vf = LocalFileSystem.getInstance().findFileByPath(path.toString())
                    ?: run { System.err.println("  [WARN] VFS: not found: $path"); return@mapNotNull null }
                PsiManager.getInstance(project).findFile(vf) as? PsiJavaFile
                    ?: run { System.err.println("  [WARN] PSI: not a Java file: $path"); null }
            }
        }

        println("j2k-headless: loaded ${psiFiles.size}/${javaFiles.size} PSI files")

        val (converted, failed) = convert(project, module, psiFiles, javaFiles.size)

        println("j2k-headless: Converted $converted/${javaFiles.size}, failed $failed")

        writeReport(javaFiles.size, converted, failed)

        ApplicationManager.getApplication().invokeAndWait {
            ProjectManagerEx.getInstanceEx().forceCloseProject(project)
        }

        return failed
    }

    // --- project / module setup ---

    private fun openHeadlessProject(): Project {
        val tempDir = Files.createTempDirectory("j2k-headless-project")
        // Mark as .idea-less lightweight project.
        val project = ProjectManagerEx.getInstanceEx().openProject(
            projectStoreBaseDir = tempDir,
            openProjectTask = OpenProjectTask(
                isNewProject     = true,
                runConfigurators = false,
                projectName      = "j2k-headless",
            )
        ) ?: error("ProjectManagerEx failed to create headless project")

        return project
    }

    @Suppress("UnstableApiUsage")
    private fun setupModule(
        project: Project,
        sourceRoot: Path,
        classpathEntries: List<String>,
    ): Module {
        var module: Module? = null

        WriteAction.runAndWait<Throwable> {
            val mm    = ModuleManager.getInstance(project).getModifiableModel()
            module    = mm.newModule(
                project.basePath + "/j2k-module.iml",
                ModuleType.EMPTY.id,
            )
            mm.commit()
        }

        val m = module!!

        WriteAction.runAndWait<Throwable> {
            val rootModel = ModuleRootManager.getInstance(m).modifiableModel

            // Source root
            val sourceVf = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourceRoot.toString())
            if (sourceVf != null) {
                rootModel.addContentEntry(sourceVf).addSourceFolder(sourceVf, false)
            }

            // Classpath entries as a single library
            if (classpathEntries.isNotEmpty()) {
                val lib  = rootModel.moduleLibraryTable.createLibrary("j2k-classpath")
                val libm = lib.modifiableModel
                for (entry in classpathEntries) {
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(entry)?.let { vf ->
                        libm.addRoot(vf, OrderRootType.CLASSES)
                    }
                }
                libm.commit()
            }

            rootModel.commit()
        }

        return m
    }

    // --- J2K conversion ---

    @Suppress("UnstableApiUsage")
    private fun convert(
        project: Project,
        module: Module,
        psiFiles: List<PsiJavaFile>,
        totalJavaCount: Int,
    ): Pair<Int, Int> {
        if (psiFiles.isEmpty()) return Pair(0, totalJavaCount)

        // Use the new J2K (NJ2K) converter.  The API is internal and pinned to the
        // IDEA version declared in build.gradle.kts.
        val extension = J2kConverterExtension.extension(useNewJ2k = true)
        val converter = extension.createConverter(ConverterSettings.defaultSettings, project, module)
        val postProcessor = extension.createPostProcessor(formatCode = false)

        // filesToKotlin must run outside any read action to avoid invokeAndWait deadlock
        // (NJ2K post-processing internally calls invokeAndWait; if we hold a read lock the
        // EDT can't proceed to run those write actions).
        val filesResult = try {
            converter.filesToKotlin(psiFiles, postProcessor)
        } catch (e: Exception) {
            System.err.println("j2k-headless: filesToKotlin threw: ${e.message}")
            e.printStackTrace(System.err)
            return Pair(0, totalJavaCount)
        }

        var converted = 0
        var failed    = 0

        for ((psiFile, elementResult) in psiFiles.zip(filesResult.results)) {
            val sourcePath = Paths.get(psiFile.virtualFile.path)
            val relative   = try { sourceRoot.relativize(sourcePath) } catch (_: IllegalArgumentException) { sourcePath.fileName }
            val destDir    = outputRoot.resolve(relative.parent ?: Paths.get(""))
            destDir.createDirectories()
            val dest = destDir.resolve("${sourcePath.nameWithoutExtension}.kt")

            val ktText = elementResult.text
            if (ktText.isNotBlank()) {
                dest.writeText(ktText)
                converted++
            } else {
                dest.writeText("// CONVERSION FAILED: ${sourcePath.fileName}\n")
                System.err.println("  [FAIL] empty output for $sourcePath")
                failed++
            }
        }

        // Files that couldn't even load as PSI count as failures.
        failed += (totalJavaCount - psiFiles.size)

        return Pair(converted, failed)
    }

    // --- report ---

    private fun writeReport(total: Int, converted: Int, failed: Int) {
        val reportPath = Paths.get(args.reportPath)
        reportPath.parent?.createDirectories()
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        reportPath.writeText(
            """
            {
              "generatedAt": "${ts.esc()}",
              "variant": "headless-j2k",
              "javaFileCount": $total,
              "convertedCount": $converted,
              "failedCount": $failed,
              "skipped": false,
              "skipReason": ""
            }
            """.trimIndent()
        )
    }

    // --- helpers ---

    private fun walkJavaFiles(root: Path): List<Path> =
        root.toFile().walk()
            .filter { it.isFile && it.extension == "java" }
            .map { it.toPath().toAbsolutePath() }
            .toList()

    private fun String.esc(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

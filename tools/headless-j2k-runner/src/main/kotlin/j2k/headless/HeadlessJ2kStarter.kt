package j2k.headless

import com.intellij.openapi.application.ApplicationStarter
import kotlin.system.exitProcess

class HeadlessJ2kStarter : ApplicationStarter {

    override val commandName: String = "j2k-headless"

    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    override val isHeadless: Boolean = true

    override fun main(args: List<String>) {
        // args[0] is the command name itself ("j2k-headless"); drop it before parsing.
        val exitCode = try {
            val cliArgs = CliArgs.parse(args.drop(1))
            val failed = ConversionRunner(cliArgs).run()
            if (failed > 0) 1 else 0
        } catch (e: Exception) {
            System.err.println("j2k-headless error: ${e.message}")
            e.printStackTrace(System.err)
            1
        }
        exitProcess(exitCode)
    }
}

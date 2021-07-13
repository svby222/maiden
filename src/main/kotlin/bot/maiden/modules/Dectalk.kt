package bot.maiden.modules

import bot.maiden.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object Dectalk : Module {
    private val LOGGER = LoggerFactory.getLogger(Schedule::class.java)

    private lateinit var tmpdir: Path
    private lateinit var xvfbProcess: Process

    override suspend fun initialize(bot: Bot) {
        withContext(Dispatchers.IO) {
            // Create virtual framebuffer
            xvfbProcess = ProcessBuilder()
                .command("Xvfb", ":0", "-screen", "0", "1024x768x16")
                .start()

            // TODO: handle Xvfb failure?

            // Create temp directory
            tmpdir = Files.createTempDirectory("maiden_dectalk-files")
            LOGGER.info("DECtalk files extracted to '$tmpdir'")

            tmpdir.toFile().deleteOnExit()

            // Copy resources
            Dectalk::class.java.getResourceAsStream("/dectalk.zip")
                .let(::ZipInputStream)
                .use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        tmpdir.resolve(entry.name).toFile().outputStream().use { out -> zis.copyTo(out) }

                        entry = zis.nextEntry
                    }
                }
        }
    }

    override fun close() {
        xvfbProcess.destroyForcibly()
        tmpdir.toFile().deleteRecursively()
    }

    @Command
    @HelpText(
        summary = "DECtalk text-to-speech",
        description = "Uses the DECtalk speech synthesizer to render the specified text as audio."
    )
    suspend fun dsay(context: CommandContext, @JoinRemaining text: String) {
        val outputTmpdir = withContext(Dispatchers.IO) { Files.createTempDirectory("maiden_dectalk-out") }

        try {
            val outputFile = outputTmpdir.resolve("output.wav")

            // TODO Windows host?
            val process =
                withContext(Dispatchers.IO) {
                    ProcessBuilder()
                        .command(
                            "wine", "say.exe",
                            "-pre", "[:phoneme on]",
                            "-w", outputFile.toAbsolutePath().toString(),
                            text
                        )
                        .directory(tmpdir.toFile())
                        .apply { environment()["DISPLAY"] = ":0.0" }
                        .start()
                }

            val exitCode = withContext(Dispatchers.IO) { process.waitFor() }

            if (exitCode == 0) {
                context.channel.sendFile(outputFile.toFile(), "output.wav")
                    .apply {
                        context.message?.let {
                            reference(it)
                            mentionRepliedUser(false)
                        }
                    }
                    .await()
            } else {
                context.replyAsync("Command failed with non-zero exit code ($exitCode)")
            }
        } finally {
            outputTmpdir.toFile().deleteRecursively()
        }
    }

}

package bot.maiden.modules

import bot.maiden.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

object Reddit : Module {
    private val LOGGER = LoggerFactory.getLogger(Reddit::class.java)

    private lateinit var tmpdir: Path

    private lateinit var clientId: String
    private lateinit var clientSecret: String

    override suspend fun initialize(bot: Bot) {
        val missingFields = listOf(
            "maiden.core.reddit.clientId",
            "maiden.core.reddit.clientSecret",
        ).filterNot { bot.config.hasPath(it) }

        if (missingFields.isNotEmpty()) {
            LOGGER.error("Cannot initialize Reddit module, missing config fields $missingFields")
            return
        }

        withContext(Dispatchers.IO) {
            // Create temp directory
            tmpdir = Files.createTempDirectory("maiden_praw_wrapper")
            LOGGER.info("praw_wrapper files extracted to '$tmpdir'")

            tmpdir.toFile().deleteOnExit()

            // Copy resources
            Reddit::class.java.getResourceAsStream("/praw_wrapper.py").use { input ->
                FileOutputStream(tmpdir.resolve("praw_wrapper.py").toFile()).use { output ->
                    input.copyTo(output)
                }
            }
        }

        clientId = bot.config.getString("maiden.core.reddit.clientId")
        clientSecret = bot.config.getString("maiden.core.reddit.clientSecret")
    }

    override fun close() {
        tmpdir.toFile().deleteRecursively()
    }

    private fun fetchPost(subreddit: String): Result<String> {
        val process = ProcessBuilder()
            .command(
                "python3", "praw_wrapper.py",
                subreddit
            )
            .directory(tmpdir.toFile())
            .apply {
                environment()["CLIENT_ID"] = clientId
                environment()["CLIENT_SECRET"] = clientSecret
            }
            .start()

        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            Result.success(output.trim())
        } else {
            val error = process.errorStream.bufferedReader().use { it.readText() }
            Result.failure(Exception(error))
        }
    }

    private suspend fun weeklyPost(context: CommandContext, subreddit: String) {
        var url = withContext(Dispatchers.IO) {
            fetchPost(subreddit).getOrThrow()
        }

        url = url.substringBefore("?")

        // TODO
        // We can't embed videos, and we can't upload them as they may be too large
        // Just send the URL as text for now :/

        context.replyAsync(buildString {
            appendLine("Here's your post:")
            appendLine(url)

            if (url.endsWith("mp4")) {
                appendLine("Unfortunately, it's not possible to use the Discord API to embed videos properly at the moment, so this is the best we can do. Sorry!")
            }
        })
    }

    @Command
    @HelpText("Fetch an image from [r/RATS](https://www.reddit.com/r/RATS/).")
    suspend fun rat(context: CommandContext) = weeklyPost(context, "RATS")

    @Command
    @HelpText("Fetch an image from [r/Hedgehog](https://www.reddit.com/r/Hedgehog/).")
    suspend fun hedgehog(context: CommandContext) = weeklyPost(context, "hedgehog")

    @Command
    @HelpText("Fetch an image from [r/budgies](https://www.reddit.com/r/budgies/).")
    suspend fun budgie(context: CommandContext) = weeklyPost(context, "budgies")

    @Command
    @HelpText("Fetch an image from [r/frogs](https://www.reddit.com/r/frogs/).")
    suspend fun frog(context: CommandContext) = weeklyPost(context, "frogs")

    @Command
    @HelpText("Fetch an image from [r/cats](https://www.reddit.com/r/cats/).")
    suspend fun cat(context: CommandContext) = weeklyPost(context, "cats")

    @Command
    @HelpText("Fetch an image from [r/rarepuppers](https://www.reddit.com/r/rarepuppers/) or [r/dogpictures](https://www.reddit.com/r/dogpictures/).")
    suspend fun dog(context: CommandContext) =
        weeklyPost(context, listOf("rarepuppers", "dogpictures").random())

    @Command
    @HelpText("Fetch an image from [r/babyelephantgifs](https://www.reddit.com/r/babyelephantgifs/).")
    suspend fun elephant(context: CommandContext) = weeklyPost(context, "babyelephantgifs")

    @Command
    @HelpText("Fetch an image from [r/partyparrot](https://www.reddit.com/r/partyparrot/).")
    suspend fun bird(context: CommandContext) = weeklyPost(context, "partyparrot")
}

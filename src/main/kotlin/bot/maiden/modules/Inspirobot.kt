package bot.maiden.modules

import bot.maiden.Command
import bot.maiden.CommandContext
import bot.maiden.Module
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.EmbedBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

object Inspirobot : Module {
    val http = HttpClient.newHttpClient()

    @Command
    suspend fun inspire(context: CommandContext, ignore: String) {
        val url = http
            .sendAsync(
                HttpRequest.newBuilder(URI.create("https://inspirobot.me/api?generate=true"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
            ).await().body()

        context.reply(
            EmbedBuilder()
                .setTitle("Your inspiration")
                .setAuthor(
                    "Inspirobot",
                    "https://inspirobot.me/",
                    "https://inspirobot.me/website/images/inspirobot-dark-green.png"
                )
                .setImage(url)
                .setFooter("Requested by ${context.requester.asTag}")
                .setTimestamp(Instant.now())
                .build()
        )
    }
}

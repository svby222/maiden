package bot.maiden.modules

import bot.maiden.Command
import bot.maiden.CommandContext
import bot.maiden.Module
import bot.maiden.common.baseEmbed
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object Inspirobot : Module {
    val http = HttpClient.newHttpClient()

    @Command
    suspend fun inspire(context: CommandContext) {
        val url = http
            .sendAsync(
                HttpRequest.newBuilder(URI.create("https://inspirobot.me/api?generate=true"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
            ).await().body()

        context.replyAsync(
            baseEmbed(context)
                .setTitle("Your inspiration")
                .setAuthor(
                    "Inspirobot",
                    "https://inspirobot.me/",
                    "https://inspirobot.me/website/images/inspirobot-dark-green.png"
                )
                .setImage(url)
                .build()
        )
    }
}

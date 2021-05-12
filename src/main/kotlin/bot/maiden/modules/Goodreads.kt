package bot.maiden.modules

import bot.maiden.*
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.EmbedBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

object Goodreads : Module {
    val http = HttpClient.newHttpClient()

    @Command
    suspend fun quote(context: CommandContext, tag: String) {
        data class Quote(
            val text: String,
            val author: String?,
            val title: String?,
            val image: String?,
            val url: String?,
            val authorUrl: String?,
        )

        val tagTransformed = tag.replace(Regex("\\s+"), "-").lowercase()

        val uri = URI.create("https://www.goodreads.com/quotes/tag/${urlencode(tagTransformed)}")
        val page = http.sendAsync(
            HttpRequest.newBuilder(uri).GET().build(),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
        ).await().body()

        val document = Jsoup.parse(page, uri.toString())

        val quotes = mutableListOf<Quote>()

        val quoteBlocks = document.getElementsByClass("quote")
        for (quote in quoteBlocks) {
            val element = quote.getElementsByClass("quoteText").firstOrNull() ?: continue
            val author = quote.selectFirst(".authorOrTitle")
                ?.text()
                ?.trim(' ', ',')
                ?.takeIf { it.isNotBlank() }

            val title = quote.selectFirst("span > .authorOrTitle")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val image = quote.selectFirst(".quoteDetails img")
                ?.attr("src")?.takeIf { it.isNotBlank() }

            val url = quote.selectFirst(".quoteFooter .right a")
                ?.attr("abs:href")?.takeIf { it.isNotBlank() }

            val authorUrl = quote.selectFirst(".quoteDetails a.leftAlignedImage")
                ?.attr("abs:href")?.takeIf { it.isNotBlank() }

            val text = buildString {
                var newlines = 0

                for (child in element.childNodes()) {
                    if (child is Element && child.tagName().equals("br", ignoreCase = true)) {
                        if (newlines < 2) {
                            newlines++
                            appendLine()
                        }
                    } else if (child is TextNode) {
                        val text = child.text().trim()

                        newlines = 0
                        append(text.substringAfter('―'))
                        append(" ")
                    }
                }
            }

            if (text.length >= 2048) continue

            quotes.add(
                Quote(
                    text,
                    author,
                    title,
                    image,
                    url, authorUrl
                )
            )
        }

        if (quotes.isEmpty()) context.message.channel.sendMessage("There are no quotes with that tag").await()
        else {
            val quote = quotes.random()

            context.message.channel.sendMessage(
                EmbedBuilder()
                    .apply {
                        quote.title?.let { setTitle(it, quote.url) }
                        quote.image?.let { setThumbnail(it) }
                    }
                    .setAuthor(quote.author ?: "Anonymous", quote.authorUrl)
                    .setDescription(quote.text)
                    .setFooter("Requested by ${context.message.author.asTag}")
                    .setTimestamp(Instant.now())
                    .build()
            ).await()
        }
    }
}

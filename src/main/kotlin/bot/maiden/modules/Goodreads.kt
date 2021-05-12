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

        suspend fun fail() {
            context.message.channel.sendMessage(
                failureEmbed(context.message.jda)
                    .appendDescription("There are no quotes with the specified tag")
                    .build()
            ).await()
        }

        val tagTransformed = tag.replace(Regex("\\s+"), "-").lowercase()

        val baseUrl = "https://www.goodreads.com/quotes/tag/${urlencode(tagTransformed)}"
        val firstPage = http.sendAsync(
            HttpRequest.newBuilder(URI.create(baseUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
        ).await().body()

        val firstDocument = Jsoup.parse(firstPage, baseUrl)

        val showingText = firstDocument.selectFirst(".leftContainer span.smallText").text()
        val (perPage, total) =
            showingText
                .substringAfter("-")
                .trim()
                .split(" of ")
                .map { it.trim().replace(",", "").toIntOrNull() }

        if (perPage == null || total == null) {
            context.message.channel.sendMessage(
                """
                    Something went wrong while trying to parse Goodreads.
                    This might indicate that the site has updated its markup, or that something else has gone wrong.
                    
                    I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                """.trimIndent()
            ).await()

            return
        }

        if (total == 0) return fail()

        val index = (0 until total).random()
        val chosenPage = index / perPage
        val chosenOffset = index % perPage

        val document =
            if (chosenPage == 0) firstDocument
            else {
                val pageUrl = "$baseUrl?page=${chosenPage}"
                val body = http.sendAsync(
                    HttpRequest.newBuilder(URI.create(pageUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
                ).await().body()

                Jsoup.parse(body, pageUrl)
            }

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

            var text = buildString {
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

            if (text.length >= 2048) {
                text = "${text.dropLast(2)} …"
            }

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

        if (quotes.isEmpty()) return fail()
        else {
            val quote = quotes[chosenOffset]

            context.message.channel.sendMessage(
                EmbedBuilder()
                    .apply {
                        quote.title?.let { setTitle(it, quote.url) }
                        quote.image?.let { setThumbnail(it) }
                    }
                    .setAuthor(quote.author ?: "Anonymous", quote.authorUrl)
                    .setDescription(quote.text)
                    .setFooter("#${index + 1} of $total | Requested by ${context.message.author.asTag}")
                    .setTimestamp(Instant.now())
                    .build()
            ).await()
        }
    }
}

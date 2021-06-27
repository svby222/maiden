package bot.maiden.modules

import bot.maiden.*
import bot.maiden.common.baseEmbed
import kotlinx.coroutines.future.await
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Horoscope : Module {
    val http = HttpClient.newHttpClient()
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    val horoscopeCached = Array<String?>(12) { null }
    var horoscopeCachedDay = LocalDate.now()

    val indexMap = mapOf(
        "aries" to 1,
        "taurus" to 2,
        "gemini" to 3,
        "cancer" to 4,
        "leo" to 5,
        "virgo" to 6,
        "libra" to 7,
        "scorpio" to 8,
        "sagittarius" to 9,
        "capricorn" to 10,
        "aquarius" to 11,
        "pisces" to 12,
    )

    @Command
    @HelpText("Fetches today's horoscope for the specified astrological sign.")
    suspend fun horoscope(context: CommandContext, sign: String) {
        suspend fun fail() {
            context.replyAsync(
                failureEmbed(context.jda)
                    .appendDescription("Invalid sign specified")
                    .build()
            )
        }

        val index = if (sign.length == 1 && sign[0].code in 9800..9811) sign[0].code - 9800 + 1
        else indexMap[sign.lowercase()] ?: return fail()

        val today = LocalDate.now()
        if (horoscopeCachedDay != today) {
            // Clear cache
            for (i in horoscopeCached.indices) horoscopeCached[i] = null
        }

        val url = "https://www.horoscope.com/us/horoscopes/general/horoscope-general-daily-today.aspx?sign=$index"

        val text =
            horoscopeCached[index - 1].let {
                if (it != null) it
                else {
                    val page = http.sendAsync(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
                    ).await().body()

                    val document = Jsoup.parse(page, url)

                    val contentP = document.selectFirst(".main-horoscope > p")

                    val date = contentP.selectFirst("strong").text()
                    val parsed = LocalDate.from(dateFormatter.parse(date))

                    val fetched = contentP
                        .text().substringAfter("-").trim()

                    if (parsed != today) {
                        // Site hasn't updated yet, don't update cache
                        horoscopeCachedDay = parsed
                    } else {
                        horoscopeCached[index - 1] = fetched
                    }

                    fetched
                }
            }

        context.replyAsync(
            baseEmbed(context)
                .setTitle(today.format(dateFormatter), url)
                .setDescription(text)
                .build()
        )
    }

    var moonCached: Pair<Map<String, String>, String?>? = null
    var moonCachedDay = LocalDate.now().dayOfYear

    @Command
    @HelpText("Displays information about the current moon phase.")
    suspend fun moon(context: CommandContext) {
        suspend fun fail() {
            context.replyAsync(
                """
                    Something went wrong while trying to parse moongiant.
                    This might indicate that the site has updated its markup, or that something else has gone wrong.
                    
                    I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                """.trimIndent()
            )
        }

        val today = LocalDate.now()
        if (moonCachedDay != today.dayOfYear) {
            // Clear cache
            moonCached = null
        }

        val url = "https://www.moongiant.com/phase/${today.month.value}/${today.dayOfMonth}/${today.year}"

        val (fields, moonImageUrl) = moonCached.let {
            if (it != null) it
            else {
                val page = http.sendAsync(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)
                ).await().body()

                val document = Jsoup.parse(page, url)

                val moonImage = document.selectFirst("#todayMoonContainer img") ?: null
                val phaseBlock = document.selectFirst("#moonDetails") ?: return fail()

                var currentField: String? = null
                val fields = mutableMapOf<String, String>()
                for (node in phaseBlock.childNodes()) {
                    if (node is TextNode && currentField == null) currentField = node.text().trim().trimEnd(':')
                    else {
                        if (node is Element && node.tagName() == "br") currentField = null
                        else if (currentField != null) {
                            val text = if (node is TextNode) node.text() else if (node is Element) node.text() else null
                            text?.let { fields[currentField] = fields.getOrDefault(currentField, "") + it }
                        }
                    }
                }

                Pair(fields, moonImage?.attr("abs:src")).also { moonCached = it }
            }
        }

        context.replyAsync(
            baseEmbed(context)
                .setThumbnail(moonImageUrl)
                .apply {
                    for ((key, value) in fields) {
                        addField(key, value, true)
                    }
                }
                .setTitle(LocalDate.now().format(dateFormatter), url)
                .build()
        )
    }

    val eightBallAnswers = listOf(
        "It is certain.",
        "It is decidedly so.",
        "Without a doubt.",
        "Yes definitely.",
        "You may rely on it.",

        "As I see it, yes.",
        "Most likely.",
        "Outlook good.",
        "Yes.",
        "Signs point to yes.",

        "Reply hazy, try again.",
        "Ask again later.",
        "Better not tell you now.",
        "Cannot predict now.",
        "Concentrate and ask again.",

        "Don't count on it.",
        "My reply is no.",
        "My sources say no.",
        "Outlook not so good.",
        "Very doubtful."
    )

    @Command
    @HelpText("Provides answers to life's most urgent questions.")
    suspend fun `8ball`(
        context: CommandContext,
        @Suppress("UNUSED_PARAMETER") @JoinRemaining query: String? = null
    ) {
        context.replyAsync(":8ball: ${eightBallAnswers.random()}")
    }
}

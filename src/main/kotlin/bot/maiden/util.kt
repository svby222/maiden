package bot.maiden

import net.dv8tion.jda.api.requests.RestAction
import java.net.URLEncoder
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun <T> RestAction<T>.await() = suspendCoroutine<T> { cont -> queue { cont.resume(it) } }

fun filterLatin(text: String) = text.replace(Regex("\\P{InBasic_Latin}"), "")

fun urlencode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

fun Duration.toPrettyString(): String {
    val segments = mapOf(
        "d" to Duration::toDaysPart,
        "h" to Duration::toHoursPart,
        "m" to Duration::toMinutesPart,
        "s" to Duration::toSecondsPart,
        "ms" to Duration::toMillisPart,
    )

    return segments.mapNotNull { (suffix, accessor) ->
        val value = accessor(this)

        if (value.toLong() != 0L) "$value $suffix"
        else null
    }.joinToString(" ")
}

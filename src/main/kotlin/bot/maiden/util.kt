package bot.maiden

import kotlinx.coroutines.channels.ReceiveChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.concurrent.Task
import java.net.URLEncoder
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> RestAction<T>.await() = suspendCoroutine<T> { cont ->
    queue({ cont.resume(it) }, { cont.resumeWithException(it) })
}

suspend fun <T> Task<T>.await() = suspendCoroutine<T> { cont ->
    onSuccess { cont.resume(it) }
    onError { cont.resumeWithException(it) }
}

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

suspend inline fun <T> ReceiveChannel<T>.awaitFirstMatching(predicate: (T) -> Boolean): T {
    for (item in this) {
        if (predicate(item)) return item
    }

    throw NoSuchElementException("No element matching the given predicate was received, or ReceiveChannel was closed")
}

fun Int.pluralize(singular: String, plural: String = singular + "s"): String {
    return if (this == 1) singular else plural
}

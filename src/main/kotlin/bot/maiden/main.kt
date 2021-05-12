package bot.maiden

import bot.maiden.modules.Administration
import bot.maiden.modules.Goodreads
import bot.maiden.modules.Inspirobot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import java.net.http.HttpClient
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.exitProcess

val START_TIMESTAMP = System.currentTimeMillis()

fun main(args: Array<String>) {
    val token = args.getOrNull(0) ?: run {
        System.err.println("No token provided, exiting")
        exitProcess(1)
    }

    val scope = CoroutineScope(Dispatchers.Default)

    val commands = listOf(
        Administration,

        Inspirobot,
        Goodreads
    )
        .flatMap { `object` -> `object`::class.functions.map { function -> Pair(`object`, function) } }
        .filter { (_, function) -> function.isSuspend }
        .filter { (_, function) ->
            function.parameters
                .firstOrNull { it.kind == KParameter.Kind.VALUE }
                ?.type?.jvmErasure == CommandContext::class
        }

    JDABuilder.createDefault(token)
        .addEventListeners(object : EventListener {
            override fun onEvent(event: GenericEvent) {
                scope.launch {
                    when (event) {
                        is ReadyEvent -> {
                            println("Ready")
                        }
                        is MessageReceivedEvent -> {
                            val content = event.message.contentRaw
                            if (content.startsWith("m!")) {
                                val unprefixed = content.substring(2).trim()

                                val spaceIndex = unprefixed.indexOf(' ')

                                val (command, args) = if (spaceIndex < 0) {
                                    Pair(unprefixed, "")
                                } else {
                                    Pair(unprefixed.substring(0, spaceIndex), unprefixed.substring(spaceIndex + 1))
                                }

                                dispatch(commands, CommandContext(event.message, commands), command, args)
                            }
                        }
                    }
                }
            }
        })
        .build()
}

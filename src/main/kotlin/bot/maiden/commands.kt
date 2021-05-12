package bot.maiden

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

annotation class Command(
    val hidden: Boolean = false
)

interface Module: AutoCloseable {
    suspend fun initialize(jda: JDA) = Unit
    override fun close() = Unit
}

data class CommandContext(
    val message: Message,
    val handlers: List<Pair<Any, KFunction<*>>>
)

suspend fun dispatch(handlers: List<Pair<Any, KFunction<*>>>, context:CommandContext, command: String, args: String) {
    val handler = handlers.firstOrNull { (_, function) -> function.name == command } ?: run {
        System.err.println("No handler for command $command")
        return
    }

    val (receiver, function) = handler
    function.callSuspend(receiver, context, args)
}

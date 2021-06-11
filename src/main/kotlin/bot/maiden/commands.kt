package bot.maiden

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.requests.restaction.MessageAction
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

annotation class Command(
    val hidden: Boolean = false
)

interface Module : AutoCloseable {
    suspend fun initialize(bot: Bot) = Unit
    override fun close() = Unit

    suspend fun onMessage(message: Message): Boolean = true
}

enum class CommandSource {
    User,
    Scheduled,
    Other,
}

data class CommandContext(
    val source: CommandSource,

    val message: Message?,
    val requester: User?,
    val guild: Guild, // TODO guild should be nullable
    val channel: MessageChannel,

    val bot: Bot,

    val reply: (Message) -> MessageAction
) {
    val jda get() = bot.jda
    val modules get() = bot.modules
    val commands get() = bot.commands

    val database get() = bot.database

    suspend fun replyAsync(message: Message, transform: MessageAction.() -> Unit = {}): Message {
        return reply(message).apply(transform).await()
    }

    suspend fun replyAsync(text: String, transform: MessageAction.() -> Unit = {}) =
        replyAsync(MessageBuilder(text).build(), transform)

    suspend fun replyAsync(embed: MessageEmbed, transform: MessageAction.() -> Unit = {}) =
        replyAsync(MessageBuilder(embed).build(), transform)

    companion object {
        @JvmStatic
        fun fromMessage(message: Message, bot: Bot) = CommandContext(
            CommandSource.User,

            message,
            message.author,
            message.guild,
            message.channel,

            bot,

            { message.reply(it).mentionRepliedUser(false) }
        )

        @JvmStatic
        fun fromScheduled(requester: User?, channel: TextChannel, bot: Bot) = CommandContext(
            CommandSource.Scheduled,

            null,
            requester,
            channel.guild,
            channel,

            bot,

            { channel.sendMessage(it) }
        )
    }
}

suspend fun dispatch(
    handlers: List<Pair<Any, KFunction<*>>>,
    context: CommandContext,
    command: String,
    args: String
): Boolean {
    val handler = handlers.firstOrNull { (_, function) -> function.name == command } ?: run {
        System.err.println("No handler for command $command")
        return false
    }

    val (receiver, function) = handler
    function.callSuspend(receiver, context, args)
    return true
}

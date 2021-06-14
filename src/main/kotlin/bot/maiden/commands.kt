package bot.maiden

import bot.maiden.common.ArgumentConverter
import bot.maiden.common.ConversionSet
import bot.maiden.common.convertInitial
import bot.maiden.common.parseArguments
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.requests.restaction.MessageAction
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

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
    conversions: ConversionSet,
    handlers: List<Pair<Any, KFunction<*>>>,
    context: CommandContext,
    command: String,
    args: String
): Boolean {
    val handler = handlers.firstOrNull { (_, function) -> function.name == command } ?: run {
        context.replyAsync(
            failureEmbed(context.jda)
                .appendDescription("No command with the name `${command}` was found")
                .build()
        )
        return false
    }

    val (receiver, function) = handler

    // Argument conversion
    val parsedArgs = parseArguments(args)
    val converted = convertInitial(parsedArgs)

    // TODO: list/vararg parameter support

    val requiredArgumentCount = function.valueParameters.filter { it.type.jvmErasure != CommandContext::class }.size
    if (converted.size != requiredArgumentCount) {
        context.replyAsync(
            failureEmbed(context.jda)
                .appendDescription("Parameter count mismatch: got ${converted.size}, expected $requiredArgumentCount")
                .build()
        )
        return false
    }

    val invokeArguments = mutableMapOf<KParameter, Any?>()

    val convertedIterator = converted.iterator()
    for (parameter in function.parameters) {
        val value = when (parameter.kind) {
            KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> receiver
            KParameter.Kind.VALUE -> {
                if (parameter.type.jvmErasure == CommandContext::class) context
                else {
                    val arg = convertedIterator.next()

                    val conversionList =
                        conversions.getConverterList(arg.convertedValue::class, parameter.type.jvmErasure)

                    if (conversionList == null) {
                        context.replyAsync(
                            failureEmbed(context.jda)
                                .appendDescription("Invalid arguments; could not convert '${arg.stringValue}' to the expected type ${parameter.type.jvmErasure}")
                                .build()
                        )
                        return false
                    } else {
                        var value = arg.convertedValue
                        for (converter in conversionList) {
                            // TODO: proper error message
                            @Suppress("UNCHECKED_CAST")
                            value = (converter as ArgumentConverter<Any, Any>).convert(value).getOrThrow()
                        }

                        value
                    }
                }
            }
        }

        invokeArguments[parameter] = value
    }

    function.callSuspendBy(invokeArguments)

    return true
}

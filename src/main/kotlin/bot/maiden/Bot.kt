package bot.maiden

import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

class Bot private constructor(config: Config, private val token: String) : AutoCloseable {
    val config = config.withoutPath("maiden.core.discord.botToken")
    lateinit var database: Database

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Bot::class.java)

        @JvmStatic
        fun create(config: Config): Bot {
            if (!config.hasPath("maiden.core.discord.botToken"))
                throw IllegalArgumentException("No token provided in configuration")

            return Bot(config, config.getString("maiden.core.discord.botToken"))
        }
    }

    lateinit var jda: JDA

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _modules = mutableListOf<Module>()
    internal var _commands = emptyList<Pair<Module, KFunction<*>>>()

    val modules get() = Collections.unmodifiableList(_modules)
    val commands get() = Collections.unmodifiableList(_commands)

    fun addModules(modules: Iterable<Module>) {
        this._modules.addAll(modules)

        // Recreate commands list
        _commands = this._modules
            .flatMap { `object` -> `object`::class.functions.map { function -> Pair(`object`, function) } }
            .filter { (_, function) -> function.hasAnnotation<Command>() }
            .filter { (_, function) -> function.isSuspend }
            .filter { (_, function) ->
                function.parameters
                    .firstOrNull { it.kind == KParameter.Kind.VALUE }
                    ?.type?.jvmErasure == CommandContext::class
            }
    }

    fun start() {
        this.database = Database(config).also { it.init() }

        this.jda = JDABuilder.createDefault(token)
            .addEventListeners(object : EventListener {
                override fun onEvent(event: GenericEvent) {
                    scope.launch { this@Bot.onEvent(event) }
                }
            })
            .build()
    }

    private suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is ReadyEvent -> {
                """
                    ========================
                    | READY EVENT RECEIVED |
                    ========================
                """.trimIndent()
                    .lines()
                    .forEach(LOGGER::info)

                event.jda.presence.activity = Activity.listening("m!help")

                event.jda.guilds.forEach(database::createGuildEntity)
            }
            is GuildJoinEvent -> {
                database.createGuildEntity(event.guild)
            }
            is MessageReceivedEvent -> {
                // TODO handle DMs at some point? Ignore them for now.
                if (!event.message.isFromGuild) return

                if (event.message.type == MessageType.INLINE_REPLY && event.message.referencedMessage?.author?.idLong == event.jda.selfUser.idLong) {
                    LOGGER.info("User ${event.message.author.asTag} replied to ${event.message.referencedMessage?.idLong}: ${event.message.contentRaw} (${event.message.guild.idLong}/${event.message.channel.idLong})")
                } else {
                    val content = event.message.contentRaw

                    if (content.startsWith("m!", ignoreCase = true)) {
                        val unprefixed = content.substring(2).trim()

                        val spaceIndex = unprefixed.indexOf(' ')

                        val (command, args) = if (spaceIndex < 0) {
                            Pair(unprefixed, "")
                        } else {
                            Pair(
                                unprefixed.substring(0, spaceIndex),
                                unprefixed.substring(spaceIndex + 1)
                            )
                        }

                        LOGGER.info("User ${event.message.author.asTag} used command $command($args) in guild \"${event.message.guild.name}\" (${event.message.guild.idLong}/${event.message.channel.idLong})")

                        try {
                            dispatch(_commands, CommandContext.fromMessage(event.message, this), command, args)
                        } catch (e: Exception) {
                            val wrapped = if (e is InvocationTargetException) (e.cause ?: e) else e

                            e.printStackTrace()
                            event.message.channel.sendMessage(
                                failureEmbed(event.jda)
                                    .appendDescription("`${wrapped::class.simpleName}`: ${wrapped.message}")
                                    .apply {
                                        var next = wrapped

                                        while (next.cause != null) {
                                            next = next.cause ?: continue
                                            appendDescription("\ncaused by `${next::class.simpleName}`: ${next.message}")
                                        }
                                    }
                                    .build()
                            ).await()
                        }
                    } else {
                        for (it in _modules) {
                            if (!it.onMessage(event.message)) break
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        jda.shutdown()
        scope.cancel()
    }
}

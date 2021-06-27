package bot.maiden

import bot.maiden.common.ArgumentConverter
import bot.maiden.common.ConversionSet
import bot.maiden.common.addPrimitiveConverters
import bot.maiden.modules.Common.USER_MENTION_REGEX
import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.properties.Delegates
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

private val DEFAULT_STATUS = "m!help"

class Bot private constructor(config: Config, private val token: String) : AutoCloseable {
    val config = config.withoutPath("maiden.core.discord.botToken")
    lateinit var database: Database

    val ownerId by lazy { config.getLong("maiden.core.ownerId") }

    var motd: String? by Delegates.observable(null) { _, _, value ->
        value?.let { jda.presence.activity = Activity.listening("$DEFAULT_STATUS | $it") }
            ?: run { jda.presence.activity = Activity.listening(DEFAULT_STATUS) }
    }

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

    val conversions = ConversionSet()

    init {
        addPrimitiveConverters(conversions)

        conversions.addConverter(object : ArgumentConverter<String, User> {
            override val fromType get() = String::class
            override val toType get() = User::class

            override suspend fun convert(from: String): Result<User> {
                // TODO wrap for exceptions

                return USER_MENTION_REGEX.matchEntire(from)?.let {
                    it.groups[1]?.value?.toLongOrNull()?.let { id -> Result.success(jda.retrieveUserById(id).await()) }
                        ?: Result.failure(Exception("Invalid ID for mention $from"))
                    // TODO message
                } ?: Result.failure(Exception(""))
            }
        }, 1)
    }

    fun addModules(modules: Iterable<Module>) {
        this._modules.addAll(modules)

        // Recreate commands list
        _commands = this._modules
            .flatMap { `object` -> `object`::class.functions.map { function -> Pair(`object`, function) } }
            .filter { (_, function) -> function.hasAnnotation<Command>() }
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

                LOGGER.info("Initializing modules...")
                _modules.forEach {
                    LOGGER.debug("Initializing module ${it::class.qualifiedName}")
                    it.initialize(this)
                    LOGGER.info("Initialized module ${it::class.qualifiedName}")
                }
                LOGGER.info("Modules initialized")

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
                            dispatch(
                                conversions,
                                _commands,
                                CommandContext.fromMessage(event.message, this),
                                command,
                                args
                            )
                        } catch (e: Throwable) {
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
        _modules.forEach { it.close() }
        jda.shutdown()
        scope.cancel()
    }
}

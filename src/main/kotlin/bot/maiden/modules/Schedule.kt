package bot.maiden.modules

import bot.maiden.*
import bot.maiden.model.GuildScheduledEvent
import bot.maiden.utilities.MultistepDialog
import bot.maiden.utilities.multistepDialog
import kotlinx.coroutines.*
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.time.Instant

object Schedule : Module {
    private val LOGGER = LoggerFactory.getLogger(Schedule::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun initialize(bot: Bot) {
        bot.database.withSession {
            val criteria = it.criteriaBuilder.createQuery(GuildScheduledEvent::class.java)
                .apply { from(GuildScheduledEvent::class.java) }

            val data = it.createQuery(criteria).resultList

            for (event in data) {
                startScheduled(bot, event, startImmediately = false)
            }
        }
    }

    private fun startScheduled(bot: Bot, eventData: GuildScheduledEvent, startImmediately: Boolean) {
        scope.launch {
            if (!startImmediately) {
                // Initial delay
                val intervalMillis = eventData.intervalSeconds * 1000L

                val now = Instant.now().toEpochMilli()
                val startAt = eventData.startAt?.toEpochMilli() ?: now

                val elapsed = now - startAt
                val intervalProgress = elapsed % intervalMillis

                val delay = if (intervalProgress == 0L) 0L else intervalMillis - intervalProgress
                delay(delay)
            }

            while (true) {
                // TODO extract execution logic

                val unprefixed = eventData.command ?: return@launch

                val spaceIndex = unprefixed.indexOf(' ')

                val (command, args) = if (spaceIndex < 0) {
                    Pair(unprefixed, "")
                } else {
                    Pair(
                        unprefixed.substring(0, spaceIndex),
                        unprefixed.substring(spaceIndex + 1)
                    )
                }

                val requester = try {
                    bot.jda.retrieveUserById(eventData.requesterId).await()
                } catch (e: ErrorResponseException) {
                    null
                }

                val targetGuild = bot.jda.getGuildById(eventData.guildId) ?: run {
                    LOGGER.error("Event scheduled in ${eventData.guildId}/${eventData.channelId} cannot be executed (guild not found)")
                    return@launch
                }

                val targetChannel = targetGuild.getTextChannelById(eventData.channelId) ?: run {
                    LOGGER.error("Event scheduled in ${eventData.guildId}/${eventData.channelId} cannot be executed (channel not found)")
                    return@launch
                }

                val requesterName = requester?.asTag ?: "unknown user"
                LOGGER.info("Executed command $command($args) scheduled by $requesterName in guild \"${targetGuild.name}\" (${eventData.guildId}/${eventData.channelId})")

                try {
                    dispatch(
                        bot._commands,
                        CommandContext.fromScheduled(
                            requester,
                            targetChannel,
                            bot
                        ),
                        command,
                        args
                    )
                } catch (e: Exception) {
                    val wrapped = if (e is InvocationTargetException) (e.cause ?: e) else e

                    e.printStackTrace()
                    targetChannel.sendMessage(
                        failureEmbed(bot.jda)
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

                delay(eventData.intervalSeconds * 1000)
            }
        }
    }

    @Command(hidden = true)
    suspend fun schedule(context: CommandContext, command: String) {
        // TODO configure whether some commands can be run without requester

        context.requester ?: return

        if (!context.requester.isOwner(context.bot)) {
            context.replyAsync("This command is unfinished and is currently only usable by the bot owner")
            return
        }

        if (command.isBlank()) {
            context.replyAsync("No command was specified.")
        }

        val trimmed = (if (command.startsWith("m!")) command.substring(2) else command).trim()

        val dialog = multistepDialog {
            var interval = 0L

            step {
                text = """
                    How often should that command be executed?
                    
                    Every...
                """.trimIndent()

                option("Minute", 0)
                option("Hour", 1)
                option("3 hours", 5)
                option("6 hours", 4)
                option("12 hours", 3)
                option("Day", 2)

                onResponse { _, data ->
                    interval = when (data) {
                        0 -> 60
                        1 -> 60 * 60
                        2 -> 24 * 60 * 60
                        3 -> 12 * 60 * 60
                        4 -> 6 * 60 * 60
                        5 -> 3 * 60 * 60
                        else -> throw IllegalStateException("Unknown response data $data")
                    }

                    MultistepDialog.StepResult.Next
                }
            }

            onFinish {
                context.replyAsync("Ok, scheduling every $interval s")

                val event = GuildScheduledEvent().apply {
                    this.guildId = context.guild.idLong
                    this.channelId = context.channel.idLong
                    this.requesterId = context.requester.idLong
                    this.startAt = Instant.now()
                    this.intervalSeconds = interval
                    this.command = trimmed
                }

                context.database.withSession { session ->
                    session.beginTransaction().let { tx ->
                        session.save(event)
                        tx.commit()
                    }
                }

                startScheduled(context.bot, event, startImmediately = true)
            }
        }

        if (!Dialog.beginDialog(context.channel, context.requester, dialog)) {
            context.replyAsync("There is already an active assistant")
        }
    }

    @Command(hidden = true)
    suspend fun scheduled(context: CommandContext, ignore: String) {
        val count = context.bot.database.withSession {
            it.createQuery("select count(*) from GuildScheduledEvent where guild_id = :guild_id")
                .setParameter("guild_id", context.guild.idLong)
                .uniqueResult() as Long
        }

        context.replyAsync("There are $count events scheduled in this server")
    }

    override fun close() {
        scope.cancel()
    }
}

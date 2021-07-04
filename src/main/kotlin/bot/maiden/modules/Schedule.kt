package bot.maiden.modules

import bot.maiden.*
import bot.maiden.model.GuildScheduledEvent
import bot.maiden.utilities.MultistepDialog
import bot.maiden.utilities.multistepDialog
import kotlinx.coroutines.*
import net.dv8tion.jda.api.Permission
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

                // Check if this event has been deleted
                val eventStillExists = bot.database.withSession {
                    it.find(GuildScheduledEvent::class.java, eventData.eventId) != null
                }

                if (!eventStillExists) break

                try {
                    dispatch(
                        bot.conversions,
                        bot._commands,
                        CommandContext.fromScheduled(
                            requester,
                            targetChannel,
                            bot
                        ),
                        command,
                        args
                    )
                } catch (e: Throwable) {
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

    private val INTERVAL_REGEX =
        Regex("^(?:(?:(\\d+) ?d(?:ay(?:s)?)?))?(?:,? *(?:(\\d+) ?h(?:our(?:s)?)?))?(?:,? *(?:(\\d+) ?m(?:inute(?:s)?)?)\$)?")

    @Command
    @HelpText(
        "Schedule command execution at regular intervals. Only available to server administrators.",
        group = "schedule"
    )
    suspend fun schedule(context: CommandContext, @JoinRemaining command: String) {
        // TODO configure whether some commands can be run without requester

        context.requester ?: return

        if (!context.requester.isOwner(context.bot) &&
            context.guild.getMember(context.requester)?.permissions?.contains(Permission.ADMINISTRATOR) != true
        ) {
            // TODO actual permission handling
            context.replyAsync("You can't do that (not an administrator)")
            return
        }

        if (command.isBlank()) {
            context.replyAsync("No command was specified.")
        }

        val trimmed = (if (command.startsWith("m!")) command.substring(2) else command).trim()

        val dialog = multistepDialog {
            title = "Scheduler"

            var interval = 0L

            step {
                title = "`$trimmed`"

                text = """
                    How often should that command be executed?
                """.trimIndent()

                optionsText = """
                    Enter the interval in the format `x days x hours x minutes`.
                    **Note: the minimum interval is 3 hours.**
                """.trimIndent()

                cancelOption("cancel")

                onResponse { _, data ->
                    val matches = INTERVAL_REGEX.matchEntire(data as String)
                        ?: return@onResponse MultistepDialog.StepResult.Invalid

                    val days = matches.groups[1]?.value?.toIntOrNull() ?: 0
                    val hours = matches.groups[2]?.value?.toIntOrNull() ?: 0
                    val minutes = matches.groups[3]?.value?.toIntOrNull() ?: 0

                    interval =
                        days * 86400L + hours * 3600L + minutes * 60L

                    if (!context.requester.isOwner(context.bot) && interval < 3 * 3600L) {
                        interval = 0
                        return@onResponse MultistepDialog.StepResult.Invalid
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

    @Command
    @HelpText(
        "Clear all scheduled events in the current server. Only available to server administrators.",
        group = "schedule"
    )
    suspend fun `clear-scheduled`(context: CommandContext) {
        context.requester ?: return

        if (!context.requester.isOwner(context.bot) &&
            context.guild.getMember(context.requester)?.permissions?.contains(Permission.ADMINISTRATOR) != true
        ) {
            // TODO actual permission handling
            context.replyAsync("You can't do that (not an administrator)")
            return
        }

        val count = context.bot.database.withSession {
            it.beginTransaction().let { tx ->
                val result = it.createQuery("delete from GuildScheduledEvent where guild_id = :guild_id")
                    .setParameter("guild_id", context.guild.idLong)
                    .executeUpdate()
                tx.commit()

                result
            }
        }

        context.replyAsync("$count scheduled events were deleted.")
    }

    override fun close() {
        scope.cancel()
    }
}

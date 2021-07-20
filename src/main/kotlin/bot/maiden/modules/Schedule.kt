package bot.maiden.modules

import bot.maiden.*
import bot.maiden.common.failureEmbed
import bot.maiden.model.GuildScheduledEvent
import bot.maiden.modules.modal.DialogStepModal
import bot.maiden.modules.modal.Modals
import bot.maiden.modules.modal.StepModal
import bot.maiden.modules.modal.buildDialog
import kotlinx.coroutines.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

object Schedule : Module {
    private val LOGGER = LoggerFactory.getLogger(Schedule::class.java)
    private var scope = CoroutineScope(Dispatchers.Default)

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

        var interval = 0L
        val dialog = buildDialog {
            title = "Scheduler"

            addStep {
                optionMode = DialogStepModal.DialogStep.OptionMode.Text

                val truncated = trimmed.substringBefore(' ').let {
                    if (it.length > 64) it.take(61) + "..."
                    else it
                }

                title = "Scheduling `$truncated`"

                mainText = "How often should that command be executed?"
                optionsText = """
                    Enter the interval in the format `x days x hours x minutes`.
                    **Note: the minimum interval is 3 hours.**
                """.trimIndent()

                option(
                    DialogStepModal.StepOption(
                        "Interval (enter text)",
                        data = DialogStepModal.StepOption.FreeInputData
                    )
                )
                option(DialogStepModal.StepOption("cancel", data = DialogStepModal.StepOption.CancelData))

                onComplete { _, data, _ ->
                    val matches = INTERVAL_REGEX.matchEntire(data)
                        ?: return@onComplete StepModal.StepResult.Invalid

                    val days = matches.groups[1]?.value?.toIntOrNull() ?: 0
                    val hours = matches.groups[2]?.value?.toIntOrNull() ?: 0
                    val minutes = matches.groups[3]?.value?.toIntOrNull() ?: 0

                    interval =
                        days * 86400L + hours * 3600L + minutes * 60L

                    if (!context.requester.isOwner(context.bot) && interval < 3 * 3600L) {
                        interval = 0
                        return@onComplete StepModal.StepResult.Invalid
                    }

                    StepModal.StepResult.GotoNext
                }
            }

            addStep(dynamic = true) {
                mainText = "Ok, scheduling every ${Duration.ofSeconds(interval).toPrettyString()}."

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

        Modals.beginModal(context.channel, context, dialog).await()
    }

    @Command
    @HelpText(
        summary = "Clear all scheduled events",
        description = "Clear all scheduled events in the current server. Only available to server administrators.",
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

        // TODO store events and stop them individually rather than reinitializing the entire module
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Default)
        initialize(context.bot)

        context.replyAsync("$count scheduled events were deleted.")
    }

    @Command
    @HelpText(
        summary = "Displays an editable list of all scheduled events",
        description = "Displays a list of all scheduled events, with the option to delete individual events. Only available to server administrators.",
        group = "schedule"
    )
    suspend fun scheduled(context: CommandContext) {
        context.requester ?: return

        if (!context.requester.isOwner(context.bot) &&
            context.guild.getMember(context.requester)?.permissions?.contains(Permission.ADMINISTRATOR) != true
        ) {
            // TODO actual permission handling
            context.replyAsync("You can't do that (not an administrator)")
            return
        }

        // TODO db pagination? Stop fetching all at once
        val events = context.bot.database.withSession {
            it.createQuery("from GuildScheduledEvent where guild_id = :guild_id")
                .setParameter("guild_id", context.guild.idLong)
                .resultList
        }.filterIsInstance<GuildScheduledEvent>()

        val chunks = events.chunked(5)
        var currentChunkIndex = 0

        val dialog = buildDialog {
            title = "Scheduled events (${events.size})"

            if (events.isEmpty()) {
                addStep {
                    mainText = "There are no events scheduled in this server."
                    defaultResult = StepModal.StepResult.Finish
                }
            }

            addStep(dynamic = true) {
                replacePrevious = true

                val previous =
                    option(
                        DialogStepModal.StepOption("Previous", icon = Emoji.fromMarkdown("⬅️"))
                    )
                val next =
                    option(
                        DialogStepModal.StepOption("Next", icon = Emoji.fromMarkdown("➡️"))
                    )
                val delete =
                    option(
                        DialogStepModal.StepOption(
                            "Remove one",
                            icon = Emoji.fromMarkdown("🚮"),
                            buttonStyle = ButtonStyle.DANGER
                        )
                    )

                val close =
                    option(
                        DialogStepModal.StepOption(
                            "Done",
                            icon = Emoji.fromMarkdown("✅"),
                            buttonStyle = ButtonStyle.SUCCESS
                        )
                    )

                editEmbed {
                    for ((index, event) in chunks[currentChunkIndex].withIndex()) {
                        addField("#${index + 1}: `${event.command}`", buildString {
                            val durationPretty = Duration.ofSeconds(event.intervalSeconds).toPrettyString()

                            appendLine("Requested by <@${event.requesterId}>")
                            appendLine("Scheduled on ${event.startAt}")
                            appendLine("Executes every $durationPretty in <#${event.channelId}>")

                            val now = Instant.now()
                            var nextExecutionTime = event.startAt!!
                            while (nextExecutionTime.isBefore(now)) {
                                nextExecutionTime = nextExecutionTime.plus(event.intervalSeconds, ChronoUnit.SECONDS)
                            }

                            appendLine("Next execution on $nextExecutionTime")
                        }, false)
                    }
                }

                onComplete { option, _, _ ->
                    when (option) {
                        previous -> {
                            currentChunkIndex = max(0, currentChunkIndex - 1)
                        }
                        next -> {
                            currentChunkIndex = min(chunks.lastIndex, currentChunkIndex + 1)
                        }

                        delete -> {
                            return@onComplete StepModal.StepResult.GotoNext
                        }
                        close -> {
                            return@onComplete StepModal.StepResult.Finish
                        }
                    }

                    StepModal.StepResult.GotoCurrent
                }
            }

            var targetEvent = 0
            addStep(dynamic = true) {
                optionMode = DialogStepModal.DialogStep.OptionMode.Text

                mainText = "Which event should be removed?"
                optionsText = "Respond with a number from 1 to ${chunks[currentChunkIndex].size}."

                optionMode = DialogStepModal.DialogStep.OptionMode.Text

                option(
                    DialogStepModal.StepOption(
                        "Index (enter text)",
                        data = DialogStepModal.StepOption.FreeInputData
                    )
                )
                option(DialogStepModal.StepOption("cancel", data = DialogStepModal.StepOption.CancelData))

                onComplete { _, response, _ ->
                    val intValue = response.toIntOrNull().takeIf { (it) in 1..chunks[currentChunkIndex].size }
                    if (intValue == null) StepModal.StepResult.Invalid
                    else {
                        targetEvent = intValue - 1
                        val event = chunks[currentChunkIndex][targetEvent]

                        context.bot.database.withSession {
                            it.beginTransaction().let { tx ->
                                it.delete(event)
                                tx.commit()
                            }
                        }

                        // TODO store events and stop them individually rather than reinitializing the entire module
                        scope.cancel()
                        scope = CoroutineScope(Dispatchers.Default)
                        initialize(context.bot)

                        StepModal.StepResult.GotoNext
                    }
                }
            }

            addStep {
                mainText = "The selected event has been deleted."
            }
        }

        Modals.beginModal(context.channel, context, dialog).await()
    }

    override fun close() {
        scope.cancel()
    }
}

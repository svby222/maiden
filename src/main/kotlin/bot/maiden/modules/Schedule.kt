package bot.maiden.modules

import bot.maiden.*
import bot.maiden.utilities.MultistepDialog
import bot.maiden.utilities.multistepDialog
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.TextChannel
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

object Schedule : Module {
    private val LOGGER = LoggerFactory.getLogger(Schedule::class.java)
    private val scope = CoroutineScope(Dispatchers.Default)

    @Command(hidden = true)
    suspend fun schedule(context: CommandContext, command: String) {
        if (!context.requester.isOwner()) {
            context.reply("This command is unfinished and is currently only usable by the bot owner")
            return
        }

        if (command.isBlank()) {
            context.reply("No command was specified.")
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
                context.reply("Ok, scheduling every $interval s")

                scope.launch {
                    while (true) {
                        // TODO extract execution logic

                        val unprefixed = trimmed

                        val spaceIndex = unprefixed.indexOf(' ')

                        val (command, args) = if (spaceIndex < 0) {
                            Pair(unprefixed, "")
                        } else {
                            Pair(
                                unprefixed.substring(0, spaceIndex),
                                unprefixed.substring(spaceIndex + 1)
                            )
                        }

                        LOGGER.info("Executed command $command($args) scheduled by ${context.requester.asTag} in guild \"${context.guild.name}\" (${context.guild.idLong}/${context.channel.idLong})")

                        try {
                            dispatch(
                                context.bot._commands,
                                CommandContext.fromScheduled(
                                    context.requester,
                                    context.channel as TextChannel,
                                    context.bot
                                ),
                                command,
                                args
                            )
                        } catch (e: Exception) {
                            val wrapped = if (e is InvocationTargetException) (e.cause ?: e) else e

                            e.printStackTrace()
                            context.channel.sendMessage(
                                failureEmbed(context.jda)
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

                        delay(interval * 1000)
                    }
                }

//                context.database.withSession { session ->
//                    session.beginTransaction().let { tx ->
//                        val event = GuildScheduledEvent().apply {
//                            guildId = context.message.guild.idLong
//                            channelId = context.message.channel.idLong
//                            startAt = Instant.now()
//                            intervalSeconds = interval
//                        }
//
//                        session.save(event)
//
//                        tx.commit()
//                    }
//                }
            }
        }

        if (!Dialog.beginDialog(context.channel, context.requester, dialog)) {
            context.reply("There is already an active assistant")
        }
    }

    override fun close() {
        scope.cancel()
    }
}

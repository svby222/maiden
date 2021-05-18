package bot.maiden.modules

import bot.maiden.Module
import bot.maiden.await
import bot.maiden.utilities.MultistepDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object Dialog : Module {
    private data class ScheduledDialog(
        val sendChannel: SendChannel<Message>,
        val channelId: Long,
        val requesterId: Long
    )

    private val scope = CoroutineScope(Dispatchers.Default)
    private val currentDialogs = ConcurrentHashMap<Long, ScheduledDialog>()

    fun beginDialog(channel: MessageChannel, requester: User, dialog: MultistepDialog): Boolean {
        if (dialog.steps.isEmpty()) return false

        val messageChannel = Channel<Message>()
        val data = ScheduledDialog(
            messageChannel,
            channel.idLong,
            requester.idLong
        )

        if (currentDialogs.putIfAbsent(channel.idLong, data) != null) {
            messageChannel.close()
            return false
        }

        suspend fun sendStep(step: MultistepDialog.Step) {
            channel.sendMessage(
                EmbedBuilder()
                    .setDescription(step.text)
                    .addField(
                        "Options",
                        step.options
                            .mapIndexed { index, pair -> Pair(index + 1, pair.first) }
                            .joinToString("\n") { (_, text) -> " •  $text" },
                        false
                    )
                    .build()
            ).await()
        }

        scope.launch {
            var stepIndex = 0
            sendStep(dialog.steps[stepIndex])

            try {
                for (message in messageChannel) {
                    val step = dialog.steps[stepIndex]

                    val response = message.contentRaw
                    val option =
                        step.options.firstOrNull { it.first.equals(response, ignoreCase = true) }
                            ?: run {
                                // "Other" option
                                step.options.firstOrNull { it.second == null }
                            }

                    if (option == null) {
                        channel.sendMessage("Invalid option").await()
                        continue
                    } else {
                        val text = option.first.takeIf { option.second != null } ?: response

                        when (step.handler(text, option.second)) {
                            MultistepDialog.StepResult.Previous -> {
                                stepIndex = max(0, stepIndex - 1)
                            }
                            MultistepDialog.StepResult.Next -> {
                                stepIndex++
                            }
                            MultistepDialog.StepResult.Cancel -> {
                                break
                            }
                            MultistepDialog.StepResult.Invalid -> {
                                // TODO
                                channel.sendMessage("Invalid option result").await()
                                continue
                            }
                        }

                        if (stepIndex >= dialog.steps.size) {
                            dialog.finishHandler()
                            break
                        }

                        sendStep(dialog.steps[stepIndex])
                    }
                }
            } finally {
                currentDialogs.remove(channel.idLong)
            }
        }

        return true
    }

    override suspend fun onMessage(message: Message): Boolean {
        val dialogData = currentDialogs[message.channel.idLong] ?: return true

        if (message.author.idLong == dialogData.requesterId && message.channel.idLong == dialogData.channelId) {
            dialogData.sendChannel.send(message)
            return false
        } else return true
    }

    override fun close() {
        scope.cancel()
    }
}

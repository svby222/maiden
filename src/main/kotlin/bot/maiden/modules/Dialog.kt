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

    object OtherData
    object CancelData

    private val scope = CoroutineScope(Dispatchers.Default)
    private val currentDialogs = ConcurrentHashMap<Long, ScheduledDialog>()

    private fun isSpecialOptionData(data: Any?) = data in listOf(OtherData, CancelData)

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
                    .apply {
                        val titleElements = listOfNotNull(dialog.title, step.title)
                        val title = if (titleElements.isEmpty()) "Dialog" else titleElements.joinToString(" / ")
                        setTitle(title)
                    }
                    .setDescription(step.text)
                    .apply {
                        if (!step.optionsText.isNullOrEmpty() || step.options.isNotEmpty()) {
                            addField(
                                "Options",
                                buildString {
                                    step.optionsText?.let {
                                        append(it)
                                        append("\n\n")
                                    }

                                    append(step.options
                                        .filter {
                                            // Don't show cancel options in list
                                            it.second !in listOf(CancelData)
                                        }
                                        .mapIndexed { index, pair -> Pair(index + 1, pair.first) }
                                        .joinToString("\n") { (_, text) -> " •  $text" })
                                },
                                false
                            )
                        }
                    }
                    .apply {
                        val cancelOption = step.options.firstOrNull { it.second == CancelData }
                        cancelOption?.let {
                            setFooter("Type \"${it.first}\" to cancel.")
                        }
                    }
                    .build()
            ).await()
        }

        scope.launch {
            var stepIndex = 0
            sendStep(dialog.steps[stepIndex])

            try {
                var canceled = false

                for (message in messageChannel) {
                    val step = dialog.steps[stepIndex]

                    val response = message.contentRaw
                    val option =
                        step.options.firstOrNull { it.first.equals(response, ignoreCase = true) }
                            ?: run {
                                // "Other" option
                                step.options.firstOrNull { it.second == OtherData }
                            }

                    val (text, optionData) =
                        if (option == null) {
                            if (step.options.all { !isSpecialOptionData(it.second) }) {
                                channel.sendMessage("Invalid option").await()
                                continue
                            } else Pair(response, response)
                        } else {
                            Pair(
                                option.first.takeIf { option.second != null } ?: response,
                                option.second
                            )
                        }

                    if (optionData == CancelData) {
                        canceled = true
                        break
                    }

                    var result = MultistepDialog.StepResult.Fallthrough
                    for (handler in step.handlers) {
                        result = handler(text, optionData)
                        if (result != MultistepDialog.StepResult.Fallthrough) break
                    }

                    when (result) {
                        MultistepDialog.StepResult.Previous -> {
                            stepIndex = max(0, stepIndex - 1)
                        }
                        MultistepDialog.StepResult.Next -> {
                            stepIndex++
                        }
                        MultistepDialog.StepResult.Cancel -> {
                            canceled = true
                            break
                        }
                        MultistepDialog.StepResult.Fallthrough, MultistepDialog.StepResult.Invalid -> {
                            // TODO
                            channel.sendMessage("Invalid response").await()
                            continue
                        }
                    }

                    if (stepIndex >= dialog.steps.size) {
                        dialog.finishHandler()
                        break
                    }

                    sendStep(dialog.steps[stepIndex])
                }

                if (canceled) {
                    channel.sendMessage("Canceled dialog.").await()
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

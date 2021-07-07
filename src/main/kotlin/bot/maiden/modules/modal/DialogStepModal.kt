package bot.maiden.modules.modal

import bot.maiden.CommandContext
import bot.maiden.await
import bot.maiden.awaitFirstMatching
import kotlinx.coroutines.channels.ReceiveChannel
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.Button

class DialogStepModal(
    val title: String,
    steps: List<LazyDialogStep>
) : StepModal(steps) {
    fun interface LazyDialogStep : Step {
        fun getStep(): DialogStep

        override suspend fun accept(
            context: CommandContext,
            messages: ReceiveChannel<GenericEvent>
        ): StepResult = getStep().accept(context, messages)
    }

    abstract class DialogStep(
        val title: String?,
        val mainText: String?,
        val optionsText: String?,

        val options: List<StepOption>,
        val useIcons: Boolean = true,

        val optionMode: OptionMode
    ) : Step {
        enum class OptionMode {
            // Dropdown,
            Buttons,
            Text
        }

        internal constructor(builder: DialogStepBuilder)
                : this(
            builder.title,
            builder.mainText,
            builder.optionsText,
            builder.options,
            builder.useIcons,
            builder.optionMode
        )
    }

    data class StepOption(
        val text: String,
        val icon: Emoji? = null,
        val data: Any = text
    ) {
        object CancelData
    }

    internal constructor(builder: DialogStepModalBuilder) : this(builder.title, builder.steps)
}

@DslMarker
annotation class DialogStepModalBuilderDsl

class DialogStepModalBuilder(
    var title: String = "Dialog",
    val steps: MutableList<DialogStepModal.LazyDialogStep> = mutableListOf()
) {
    @DialogStepModalBuilderDsl
    fun addStep(dynamic: Boolean = false, block: DialogStepBuilder.() -> Unit) {
        val builder = DialogStepBuilder().apply(block)

        val step = if (dynamic) {
            DialogStepModal.LazyDialogStep {
                DialogStepBuilder().apply(block).buildStatic(this@DialogStepModalBuilder)
            }
        } else {
            // Pre-build
            val static = DialogStepBuilder().apply(block).buildStatic(this@DialogStepModalBuilder)
            DialogStepModal.LazyDialogStep { static }
        }
        steps.add(step)
    }
}

class DialogStepBuilder(
    var title: String? = null,
    var mainText: String? = null,
    var optionsText: String? = null,

    var useIcons: Boolean = true,
    var optionMode: DialogStepModal.DialogStep.OptionMode = DialogStepModal.DialogStep.OptionMode.Buttons,

    val options: MutableList<DialogStepModal.StepOption> = mutableListOf()
) {
    internal var onComplete: (DialogStepModal.StepOption, GenericEvent) -> StepModal.StepResult =
        { _, _ -> StepModal.StepResult.GotoNext }

    fun onComplete(action: (DialogStepModal.StepOption, GenericEvent) -> StepModal.StepResult) {
        this.onComplete = action
    }

    fun option(option: DialogStepModal.StepOption) {
        options.add(option)
    }

    fun buildStatic(modalBuilder: DialogStepModalBuilder): DialogStepModal.DialogStep {
        val onComplete = this.onComplete

        when (optionMode) {
            DialogStepModal.DialogStep.OptionMode.Text -> {
                return object : DialogStepModal.DialogStep(this) {
                    override suspend fun accept(
                        context: CommandContext,
                        messages: ReceiveChannel<GenericEvent>
                    ): StepModal.StepResult {
                        context.replyAsync(
                            EmbedBuilder()
                                .setTitle(
                                    listOfNotNull(modalBuilder.title, this.title)
                                        .joinToString(" / ")
                                )
                                .setDescription(mainText)
                                .apply {
                                    if (!(optionsText.isNullOrBlank() && options.isEmpty())) {
                                        val parts = listOfNotNull(
                                            optionsText,
                                            options.takeIf { it.isNotEmpty() }
                                                ?.map { " •  ${it.text}" }
                                                ?.joinToString("\n")
                                        )
                                        if (parts.isNotEmpty()) {
                                            addField("Options", parts.joinToString("\n\n"), false)
                                        }
                                    }
                                }
                                .build()
                        )

                        if (options.isEmpty()) return StepModal.StepResult.GotoNext

                        // TODO handle other event types
                        val event = messages.awaitFirstMatching {
                            it is MessageReceivedEvent && it.author.idLong == context.requester?.idLong
                        } as MessageReceivedEvent

                        val selectedOption =
                            options.firstOrNull { event.message.contentRaw.equals(it.text, ignoreCase = true) }

                        if (selectedOption == null) return StepModal.StepResult.Invalid
                        else {
                            val result = onComplete(selectedOption, event)
                            return result
                        }
                    }
                }
            }
            DialogStepModal.DialogStep.OptionMode.Buttons -> {
                if (options.size > 5) {
                    // Too many options for an action row
                    throw IllegalArgumentException("${DialogStepModal.DialogStep.OptionMode.Buttons} option mode requires <= 5 options, but ${options.size} were added")
                }

                return object : DialogStepModal.DialogStep(this) {
                    override suspend fun accept(
                        context: CommandContext,
                        messages: ReceiveChannel<GenericEvent>
                    ): StepModal.StepResult {
                        val message = context.replyAsync(
                            EmbedBuilder()
                                .setTitle(
                                    listOfNotNull(modalBuilder.title, this.title)
                                        .joinToString(" / ")
                                )
                                .setDescription(mainText)
                                .apply {
                                    if (!useIcons) {
                                        optionsText?.let { addField("Options", optionsText, false) }
                                    } else {
                                        if (!(optionsText.isNullOrBlank() && options.isEmpty())) {
                                            val parts = listOfNotNull(
                                                optionsText,
                                                options.takeIf { it.isNotEmpty() }
                                                    ?.map { " ${it.icon?.asMention ?: "•"}  ${it.text}" }
                                                    ?.joinToString("\n")
                                            )
                                            if (parts.isNotEmpty()) {
                                                addField("Options", parts.joinToString("\n\n"), false)
                                            }
                                        }
                                    }
                                }
                                .build()
                        ) {
                            if (options.isNotEmpty()) {
                                setActionRow(
                                    options.map {
                                        val buttonId = "${context.channel.idLong}:${it.data}"

                                        it.icon?.takeIf { useIcons }?.let { Button.secondary(buttonId, it) }
                                            ?: Button.secondary(buttonId, it.text)
                                    }
                                )
                            }
                        }

                        if (options.isEmpty()) return StepModal.StepResult.GotoNext

                        // TODO verify event IDs
                        val event = messages.awaitFirstMatching {
                            it is ButtonClickEvent
                                    && it.channel.idLong == context.channel.idLong
                                    && it.user.idLong == context.requester?.idLong
                        } as ButtonClickEvent

                        val id = event.componentId.substringAfter(':')
                        val selectedOption = options.firstOrNull { it.data == id }

                        event.deferEdit().await()

                        if (selectedOption == null) return StepModal.StepResult.Invalid
                        else {
                            val result = onComplete(selectedOption, event)
                            return result
                        }
                    }
                }
            }
        }
    }
}

@DialogStepModalBuilderDsl
fun buildDialog(block: DialogStepModalBuilder.() -> Unit): DialogStepModal {
    return DialogStepModal(DialogStepModalBuilder().apply(block))
}

package bot.maiden.modules.modal

import bot.maiden.CommandContext
import bot.maiden.await
import bot.maiden.awaitFirstMatching
import kotlinx.coroutines.channels.ReceiveChannel
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle

class DialogStepModal(
    val title: String,
    steps: List<LazyDialogStep>
) : StepModal(steps) {
    fun interface LazyDialogStep : Step {
        fun getStep(): DialogStep

        override suspend fun accept(
            context: CommandContext,
            modal: StepModal,
            messages: ReceiveChannel<GenericEvent>
        ): StepResult = getStep().accept(context, modal, messages)
    }

    internal var lastMessage: Message? = null

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
        val data: Any = text,

        val buttonStyle: ButtonStyle = ButtonStyle.SECONDARY
    ) {
        object FreeInputData
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
        val step = if (dynamic) {
            DialogStepModal.LazyDialogStep {
                DialogStepBuilder().apply(block).buildStatic()
            }
        } else {
            // Pre-build
            val static = DialogStepBuilder().apply(block).buildStatic()
            DialogStepModal.LazyDialogStep { static }
        }
        steps.add(step)
    }
}

class DialogStepBuilder(
    var title: String? = null,
    var mainText: String? = null,
    var optionsText: String? = null,
    var replacePrevious: Boolean = false,

    var defaultResult: StepModal.StepResult = StepModal.StepResult.GotoNext,

    var useIcons: Boolean = true,
    var optionMode: DialogStepModal.DialogStep.OptionMode = DialogStepModal.DialogStep.OptionMode.Buttons,

    val options: MutableList<DialogStepModal.StepOption> = mutableListOf()
) {
    internal var embedAdapter: EmbedBuilder.() -> Unit = { Unit }

    internal var onComplete: suspend (DialogStepModal.StepOption, String, GenericEvent) -> StepModal.StepResult =
        { _, _, _ -> StepModal.StepResult.GotoNext }

    fun onComplete(action: suspend (DialogStepModal.StepOption, String, GenericEvent) -> StepModal.StepResult) {
        this.onComplete = action
    }

    fun editEmbed(action: EmbedBuilder.() -> Unit) {
        this.embedAdapter = action
    }

    fun option(option: DialogStepModal.StepOption): DialogStepModal.StepOption {
        options.add(option)
        return option
    }

    fun buildStatic(): DialogStepModal.DialogStep {
        val onComplete = this.onComplete

        when (optionMode) {
            DialogStepModal.DialogStep.OptionMode.Text -> {
                return object : DialogStepModal.DialogStep(this) {
                    override suspend fun accept(
                        context: CommandContext,
                        modal: StepModal,
                        messages: ReceiveChannel<GenericEvent>
                    ): StepModal.StepResult {
                        modal as? DialogStepModal
                            ?: throw AssertionError("DialogStep must only be used with DialogStepModal")

                        val newEmbed = EmbedBuilder()
                            .setTitle(
                                listOfNotNull(modal.title, this.title)
                                    .joinToString(" / ")
                            )
                            .setDescription(mainText)
                            .apply(embedAdapter)
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

                        val message = modal.lastMessage?.takeIf { replacePrevious }
                            ?.editMessage(newEmbed)?.setActionRows(emptyList())?.await()
                            ?: context.replyAsync(newEmbed) { setActionRows(emptyList()) }

                        try {
                            modal.lastMessage = message

                            if (options.isEmpty()) return defaultResult

                            // TODO handle other event types
                            val event = messages.awaitFirstMatching {
                                it is MessageReceivedEvent && it.author.idLong == context.requester?.idLong
                            } as MessageReceivedEvent

                            val selectedOption =
                                options.firstOrNull { event.message.contentRaw.equals(it.text, ignoreCase = true) }

                            val result = if (selectedOption == null) {
                                val freeInputOption =
                                    options.firstOrNull { it.data == DialogStepModal.StepOption.FreeInputData }
                                if (freeInputOption == null) StepModal.StepResult.Invalid
                                else onComplete(freeInputOption, event.message.contentRaw, event)
                            } else {
                                onComplete(selectedOption, event.message.contentRaw, event)
                            }
                            return result
                        } finally {
                            val oldEmbed = message.embeds.first()

                            val expiredEmbed = EmbedBuilder(oldEmbed)
                                .apply {
                                    val prefix = "🔒 "
                                    val postfix = " (expired)"
                                    val oldTitle =
                                        (oldEmbed.title ?: "Dialog").take(256 - prefix.length - postfix.length)
                                    setTitle("$prefix$oldTitle$postfix")
                                }
                                .build()
                            message.editMessage(
                                MessageBuilder(message).setEmbed(expiredEmbed).build()
                            ).setActionRows(emptyList()).await()
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
                        modal: StepModal,
                        messages: ReceiveChannel<GenericEvent>
                    ): StepModal.StepResult {
                        modal as? DialogStepModal
                            ?: throw AssertionError("DialogStep must only be used with DialogStepModal")

                        val newEmbed = EmbedBuilder()
                            .setTitle(
                                listOfNotNull(modal.title, this.title)
                                    .joinToString(" / ")
                            )
                            .setDescription(mainText)
                            .apply(embedAdapter)
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

                        val newActionRows =
                            if (options.isEmpty()) emptyList()
                            else listOf(
                                ActionRow.of(
                                    options.map {
                                        val buttonId = "${context.channel.idLong}:${it.data}"

                                        it.icon?.takeIf { useIcons }
                                            ?.let { icon -> Button.of(it.buttonStyle, buttonId, icon) }
                                            ?: Button.of(it.buttonStyle, buttonId, it.text)
                                    }
                                )
                            )

                        val message = modal.lastMessage?.takeIf { replacePrevious }
                            ?.editMessage(newEmbed)?.setActionRows(newActionRows)?.await()
                            ?: context.replyAsync(newEmbed) { setActionRows(newActionRows) }

                        val event: ButtonClickEvent
                        val selectedOption: DialogStepModal.StepOption?

                        try {
                            modal.lastMessage = message

                            if (options.isEmpty()) return defaultResult

                            // TODO verify event IDs
                            event = messages.awaitFirstMatching {
                                it is ButtonClickEvent
                                        && it.channel.idLong == context.channel.idLong
                                        && it.user.idLong == context.requester?.idLong
                            } as ButtonClickEvent

                            event.deferEdit().await()

                            val id = event.componentId.substringAfter(':')
                            selectedOption = options.firstOrNull { it.data == id }
                        } finally {
                            val oldEmbed = message.embeds.first()

                            val expiredEmbed = EmbedBuilder(oldEmbed)
                                .apply {
                                    val prefix = "🔒 "
                                    val postfix = " (expired)"
                                    val oldTitle =
                                        (oldEmbed.title ?: "Dialog").take(256 - prefix.length - postfix.length)
                                    setTitle("$prefix$oldTitle$postfix")
                                }
                                .build()
                            message.editMessage(
                                MessageBuilder(message).setEmbed(expiredEmbed).build()
                            ).setActionRows(emptyList()).await()
                        }

                        if (selectedOption == null) return StepModal.StepResult.Invalid
                        else {
                            val result = onComplete(selectedOption, selectedOption.text, event)
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

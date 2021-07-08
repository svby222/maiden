package bot.maiden.modules

import bot.maiden.*
import bot.maiden.modules.modal.DialogStepModal
import bot.maiden.modules.modal.Modals
import bot.maiden.modules.modal.StepModal
import bot.maiden.modules.modal.buildDialog
import net.dv8tion.jda.api.entities.Emoji
import kotlin.math.max
import kotlin.math.min

object Test : Module {
    @Command
    @HelpText("Run function tests for debugging purposes. Not available in the production bot!")
    suspend fun test(context: CommandContext, testName: String) {
        if (!context.bot.isDebug) {
            context.replyAsync(
                failureEmbed(context.jda)
                    .appendDescription("Tests are disabled! (`isDebug == false`)")
                    .build()
            )
            return
        }

        when (testName) {
            "dialog1" -> {
                val modal = buildDialog {
                    var selected = "unknown"

                    title = "Input test"

                    addStep {
                        title = "Step 1"

                        mainText = "This is a dialog test."
                        optionsText = "Pick an option:"
                        optionMode = DialogStepModal.DialogStep.OptionMode.Text

                        option(DialogStepModal.StepOption("Option 1", data = "A"))
                        option(DialogStepModal.StepOption("Option 2", data = "B"))

                        onComplete { option, _, _ ->
                            selected = option.data.toString()

                            StepModal.StepResult.GotoNext
                        }
                    }

                    addStep(dynamic = true) {
                        title = "Results"

                        mainText = "You picked option $selected"
                    }
                }

                Modals.beginModal(context.channel, context, modal)
                    .join()
            }
            "dialog2" -> {
                val modal = buildDialog {
                    var selected = "unknown"

                    title = "Input test"

                    addStep {
                        title = "Step 1"

                        mainText = "This is a dialog test."
                        optionsText = "Pick an option:"
                        optionMode = DialogStepModal.DialogStep.OptionMode.Buttons

                        option(DialogStepModal.StepOption("Option 1", icon = Emoji.fromMarkdown("🅰️"), data = "A"))
                        option(DialogStepModal.StepOption("Option 2", icon = Emoji.fromMarkdown("🅱️"), data = "B"))

                        onComplete { option, _, _ ->
                            selected = option.data.toString()

                            StepModal.StepResult.GotoNext
                        }
                    }

                    addStep(dynamic = true) {
                        title = "Results"

                        mainText = "You picked option $selected"
                    }
                }

                Modals.beginModal(context.channel, context, modal)
                    .join()
            }
            "paginate1" -> {
                val items = (1..100).toList()
                val chunks = items.chunked(10)

                var chunkIndex = 0

                val modal = buildDialog {
                    title = "Pagination test"

                    addStep(dynamic = true) {
                        replacePrevious = true

                        val previous = option(
                            DialogStepModal.StepOption("Previous", icon = Emoji.fromMarkdown("⬅️"), data = "previous")
                        )
                        val next =
                            option(
                                DialogStepModal.StepOption("Next", icon = Emoji.fromMarkdown("➡️"), data = "next")
                            )

                        mainText = chunks[chunkIndex].joinToString("\n", prefix = "```\n", postfix = "\n```")

                        onComplete { option, _, _ ->
                            when (option) {
                                previous -> {
                                    chunkIndex = max(0, chunkIndex - 1)
                                }
                                next -> {
                                    chunkIndex = min(chunks.lastIndex, chunkIndex + 1)
                                }
                            }

                            StepModal.StepResult.GotoCurrent
                        }
                    }
                }

                Modals.beginModal(context.channel, context, modal)
                    .join()
            }
            else -> context.replyAsync("That test doesn't exist!")
        }
    }
}

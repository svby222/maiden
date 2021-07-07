package bot.maiden.modules.modal

import bot.maiden.CommandContext
import kotlinx.coroutines.channels.ReceiveChannel
import net.dv8tion.jda.api.events.message.GenericMessageEvent

open class StepModal(val steps: List<Step>) {
    sealed class StepResult {
        object GotoNext : StepResult()
        object Finish : StepResult()
        object Cancel : StepResult()
        object Invalid : StepResult()
        data class GotoStep(val step: Step) : StepResult()
        data class GotoIndex(val index: Int) : StepResult()
    }

    fun interface Step {
        suspend fun accept(context: CommandContext, messages: ReceiveChannel<GenericMessageEvent>): StepResult
    }

    suspend fun start(context: CommandContext, messages: ReceiveChannel<GenericMessageEvent>) {
        var currentStep = steps.first()

        while (true) {
            val result = currentStep.accept(context, messages)

            when (result) {
                StepResult.Cancel -> {
                    context.replyAsync("Cancelled")
                    break
                }
                StepResult.Finish -> break
                StepResult.GotoNext -> {
                    // TODO error handling
                    val currentIndex = steps.lastIndexOf(currentStep)
                    currentStep = steps[currentIndex + 1]
                }
                is StepResult.GotoIndex -> currentStep = steps[result.index]
                is StepResult.GotoStep -> currentStep = result.step
                StepResult.Invalid -> {
                    context.replyAsync("Invalid")
                    continue
                }
            }
        }
    }
}

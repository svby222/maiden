package bot.maiden.modules.modal

import bot.maiden.CommandContext
import bot.maiden.await
import kotlinx.coroutines.channels.ReceiveChannel
import net.dv8tion.jda.api.events.GenericEvent

open class StepModal(val steps: List<Step>) {
    sealed class StepResult {
        object GotoNext : StepResult()
        object GotoCurrent : StepResult()
        object Finish : StepResult()
        object Cancel : StepResult()
        object Invalid : StepResult()
        data class GotoStep(val step: Step) : StepResult()
        data class GotoIndex(val index: Int) : StepResult()
    }

    fun interface Step {
        suspend fun accept(
            context: CommandContext,
            modal: StepModal,
            messages: ReceiveChannel<GenericEvent>
        ): StepResult
    }

    suspend fun start(context: CommandContext, messages: ReceiveChannel<GenericEvent>) {
        var currentStep = steps.first()

        while (true) {
            val result = currentStep.accept(context, this, messages)

            when (result) {
                StepResult.Cancel -> {
                    context.channel.sendMessage("Canceled").await()
                    break
                }
                StepResult.Finish -> break
                StepResult.GotoNext -> {
                    // TODO error handling
                    val currentIndex = steps.lastIndexOf(currentStep)
                    if (currentIndex == steps.lastIndex) break

                    currentStep = steps[currentIndex + 1]
                }
                StepResult.GotoCurrent -> Unit
                is StepResult.GotoIndex -> currentStep = steps[result.index]
                is StepResult.GotoStep -> currentStep = result.step
                StepResult.Invalid -> {
                    context.channel.sendMessage("Invalid response").await()
                    continue
                }
            }
        }
    }
}

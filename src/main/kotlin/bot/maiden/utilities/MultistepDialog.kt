package bot.maiden.utilities

import bot.maiden.modules.Dialog

class MultistepDialog(val title: String?, val steps: List<Step>, val finishHandler: suspend () -> Unit) {
    enum class StepResult {
        Fallthrough,
        Previous,
        Next,
        Cancel,
        Invalid,
    }

    class Step(
        val title: String?,
        val text: String?,
        val optionsText: String?,
        val options: List<Pair<String, Any?>>,
        val handlers: List<suspend (String, Any?) -> StepResult>
    )
}

@DslMarker
internal annotation class MultistepDialogDsl

@MultistepDialogDsl
class MultistepDialogBuilder {
    var title: String? = null

    private val steps = mutableListOf<MultistepDialog.Step>()
    private var finishHandler: suspend () -> Unit = { }

    fun step(configure: StepBuilder.() -> Unit): MultistepDialogBuilder {
        steps.add(StepBuilder().apply(configure).build())
        return this
    }

    fun onFinish(handler: suspend () -> Unit): MultistepDialogBuilder {
        this.finishHandler = handler
        return this
    }

    fun build() = MultistepDialog(title, steps, finishHandler)
}

@MultistepDialogDsl
class StepBuilder {
    var title: String? = null

    var text: String? = null
    var optionsText: String? = null
    var options = mutableListOf<Pair<String, Any?>>()

    val handlers = mutableListOf<suspend (String, Any?) -> MultistepDialog.StepResult>()

    fun option(text: String, data: Any = text): StepBuilder {
        options.add(Pair(text, data))
        return this
    }

    fun otherOption(text: String): StepBuilder {
        options.add(Pair(text, Dialog.OtherData))
        return this
    }

    fun onResponse(handler: suspend (text: String, data: Any?) -> MultistepDialog.StepResult): StepBuilder {
        handlers += handler
        return this
    }

    fun cancelOption(cancelText: String = "cancel") {
        options.add(0, Pair(cancelText, Dialog.CancelData))
    }

    fun build() = MultistepDialog.Step(title, text, optionsText, options, handlers)
}

fun multistepDialog(configure: MultistepDialogBuilder.() -> Unit): MultistepDialog {
    return MultistepDialogBuilder().apply(configure).build()
}

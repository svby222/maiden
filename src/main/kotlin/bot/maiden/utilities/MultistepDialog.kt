package bot.maiden.utilities

class MultistepDialog(val steps: List<Step>, val finishHandler: suspend () -> Unit) {
    enum class StepResult {
        Previous,
        Next,
        Cancel,
        Invalid,
    }

    class Step(val text: String?, val options: List<Pair<String, Any?>>, val handler: (String, Any?) -> StepResult)
}

@DslMarker
internal annotation class MultistepDialogDsl

@MultistepDialogDsl
class MultistepDialogBuilder {
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

    fun build() = MultistepDialog(steps, finishHandler)
}

@MultistepDialogDsl
class StepBuilder {
    var text: String? = null
    var options = mutableListOf<Pair<String, Any?>>()
    private var handler: (String, Any?) -> MultistepDialog.StepResult = { _, _ -> MultistepDialog.StepResult.Next }

    fun option(text: String, data: Any = text): StepBuilder {
        options.add(Pair(text, data))
        return this
    }

    fun otherOption(text: String): StepBuilder {
        options.add(Pair(text, null))
        return this
    }

    fun onResponse(handler: (text: String, data: Any?) -> MultistepDialog.StepResult): StepBuilder {
        this.handler = handler
        return this
    }

    fun build() = MultistepDialog.Step(text, options, handler)
}

fun multistepDialog(configure: MultistepDialogBuilder.() -> Unit): MultistepDialog {
    return MultistepDialogBuilder().apply(configure).build()
}

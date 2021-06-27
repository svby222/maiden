package bot.maiden.common

data class Arg(
    val stringValue: String,
    val rawStringValue: String,
    val leadingSpaces: Int,
    val quoted: Boolean,

    val convertedValue: Any,
) {
//    override fun toString() = buildString {
//        append(" ".repeat(leadingSpaces))
//        if (quoted) append('"')
//        append(stringValue)
//        if (quoted) append('"')
//    }
}

internal fun parseArguments(args: String): List<Arg> {
    val results = mutableListOf<Arg>()

    var current = StringBuilder()
    var leadingSpaces = 0
    var quoted = false

    fun pushCurrent(): Boolean {
        if (current.isEmpty()) return false

        val string = current.toString()
        results.add(
            Arg(
                string,
                if (quoted) "\"$string\"" else string,
                leadingSpaces,
                quoted,

                string
            )
        )

        current = StringBuilder()
        leadingSpaces = 0

        return true
    }

    for (char in args) {
        when (char) {
            ' ' -> {
                if (!quoted) {
                    pushCurrent()
                    leadingSpaces++
                } else {
                    current.append(char)
                }
            }
            '"' -> {
                pushCurrent()
                quoted = !quoted
            }
            else -> current.append(char)
        }
    }

    // TODO: make trimming quoted args the default behavior

    // If still quoted, append the unfinished quote to the previous argument
    // Otherwise, push as a separate arg
    if (quoted) {
        val string = "\"" + current.toString()
        val lastArg = results.lastOrNull()
        if (lastArg?.quoted == false) {
            val mergedValue = lastArg.stringValue + string
            results[results.lastIndex] = lastArg.copy(
                stringValue = mergedValue,
                rawStringValue = mergedValue,
                quoted = false,
                convertedValue = mergedValue
            )
        } else {
            results.add(
                Arg(
                    string,
                    string,
                    leadingSpaces,
                    false,

                    string
                )
            )
        }
    } else {
        pushCurrent()
    }

    return results
}

internal fun reconstructArgumentString(args: List<Arg>): String {
    return args.joinToString("") {
        buildString {
            append(" ".repeat(it.leadingSpaces))
            if (it.quoted) append('"')
            append(it.stringValue)
            if (it.quoted) append('"')
        }
    }.trim()
}

// Try to infer a sensible type for each argument
internal fun convertInitial(args: List<Arg>): List<Arg> {
    return args.map { arg ->
        val valueTrimmed = arg.stringValue.trim()

        // Taken from https://stackoverflow.com/a/23872060
        if (Regex("^[+-]?(\\d+)\$").matches(valueTrimmed))
            return@map arg.copy(convertedValue = valueTrimmed.toBigInteger())
        if (Regex("^[+-]?((\\d+(\\.\\d*)?)|(\\.\\d+))\$").matches(valueTrimmed))
            return@map arg.copy(convertedValue = valueTrimmed.toBigDecimal())

        arg
    }
}

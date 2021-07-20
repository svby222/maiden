package bot.maiden

import bot.maiden.common.Arg
import bot.maiden.modules.Administration
import info.debatty.java.stringsimilarity.JaroWinkler
import net.dv8tion.jda.api.entities.MessageEmbed

private val JARO_WINKLER = JaroWinkler()
private const val MIN_SUGGESTION_SIMILARITY = 0.80

fun commandNotFoundEmbed(
    context: CommandContext,
    handlers: List<Bot.RegisteredCommand>,
    commandName: String
): MessageEmbed {
    val similarCommands = handlers
        .asSequence()
        .map { Pair(it, JARO_WINKLER.similarity(commandName, it.name)) }
        .filter { (_, similarity) -> similarity >= MIN_SUGGESTION_SIMILARITY }
        .sortedByDescending { (_, similarity) -> similarity }
        .map { (command, _) -> command.name }
        .distinct()
        .take(3)
        .toList()

    return failureEmbed(context.jda)
        .appendDescription(
            buildString {
                appendLine("No command with the name `${commandName}` was found.")

                if (similarCommands.isNotEmpty()) {
                    appendLine()
                    appendLine("Did you mean:")

                    for (similarCommandName in similarCommands) {
                        appendLine("• `$similarCommandName`")
                    }
                }
            })
        .build()
}

private fun providedTypes(args: List<Arg>): String {
    return args.takeIf { it.isNotEmpty() }
        ?.joinToString(
            separator = " ",
            prefix = "`",
            postfix = "`"
        ) {
            val `class` = it.convertedValue::class

            Administration.typeNameOverrides[`class`]
                ?: `class`.simpleName
                ?: "[unknown type]"
        } ?: "(none)"
}

fun noMatchingOverloadEmbed(
    context: CommandContext,
    handlers: List<Bot.RegisteredCommand>,
    commandName: String,
    args: List<Arg>
): MessageEmbed {
    return failureEmbed(context.jda)
        .appendDescription(buildString {
            appendLine("No version of the command `$commandName` accepts the provided values:")
            appendLine(providedTypes(args))

            appendLine()
            appendLine("This command accepts the following sets of values:")

            for (overload in handlers) {
                append("• ")
                appendLine(Administration.createDisplayTitle(overload))
            }
        })
        .addField("Resolution", "**Use `m!help ${commandName}` for more information.**", false)
        .build()
}

fun multipleMatchingOverloadsEmbed(
    context: CommandContext,
    handlers: List<Bot.RegisteredCommand>,
    commandName: String,
    args: List<Arg>,
    score: Int,
): MessageEmbed {
    return failureEmbed(context.jda)
        .appendDescription(buildString {
            appendLine("Multiple versions of the command `$commandName` accept the provided values:")
            appendLine(providedTypes(args))

            appendLine()
            appendLine("This command accepts the following sets of values:")

            for (overload in handlers) {
                append("• ")
                appendLine(Administration.createDisplayTitle(overload))
            }
        })
        // TODO: make quoted arguments auto-resolve to String
//        .addField(
//            "Resolution", """
//                Try resolving ambiguities by:
//                • surrounding text arguments with double quotes (`"`)
//                • adding decimal points (i.e. `3` → `3.0`)
//
//                **Use `m!help $commandName` for more information.**
//            """.trimIndent(),
//            false
//        )
        .addField(
            "Resolution", """
                **Use `m!help $commandName` for more information.**
            """.trimIndent(),
            false
        )
        .addField("Debug information", "Argument match score: $score", false)
        .build()
}

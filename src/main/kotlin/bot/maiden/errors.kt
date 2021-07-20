package bot.maiden

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

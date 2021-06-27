package bot.maiden.modules

import bot.maiden.CommandContext
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

object Common {
    val USER_MENTION_REGEX = Regex("<@!?(\\d+)>")
    val GENERAL_MENTION_REGEX = Regex("@(\\w+)")

    val COMMAND_PARAMETER_PREDICATE: (KParameter) -> Boolean = {
        it.kind == KParameter.Kind.VALUE && it.type.jvmErasure !in listOf(CommandContext::class)
    }
}

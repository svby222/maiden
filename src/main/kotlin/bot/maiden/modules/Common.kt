package bot.maiden.modules

object Common {
    val USER_MENTION_REGEX = Regex("<@!?(\\d+)>")
    val GENERAL_MENTION_REGEX = Regex("@(\\w+)")
}

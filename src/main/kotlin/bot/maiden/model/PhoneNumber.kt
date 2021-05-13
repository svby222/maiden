package bot.maiden.model

@JvmInline
value class PhoneNumber(val value: String) {

    override fun toString(): String {
        if (value.length < 7) return value

        val first3 = value.take(3)
        var nextSplit = value.substring(3).windowed(3, 3, partialWindows = true)

        if (nextSplit.last().length != 3) {
            nextSplit = nextSplit.dropLast(2) + nextSplit.takeLast(2).joinToString("")
        }

        return "($first3) ${nextSplit.joinToString("-")}"
    }

}

package bot.maiden.modules

import bot.maiden.*
import java.math.BigInteger
import kotlin.random.Random
import kotlin.random.asJavaRandom


object Tabletop : Module {
    private val random = Random(System.currentTimeMillis())
    private val DIE_REGEX = Regex("^(?:(\\d+)?d)?(\\d+)$")

    private fun randomBigInteger(until: BigInteger): BigInteger {
        var randomNumber: BigInteger
        do {
            randomNumber = BigInteger(until.bitLength(), random.asJavaRandom())
        } while (randomNumber >= until)

        return randomNumber
    }

    private val ONE_HUNDRED = 100.toBigInteger()

    @Command
    @HelpText(
        summary = "Roll some dice",
        description = "Roll the specified dice.\n`dice` should be a list of die rolls, like `6`, `5d6` or `d20`, separated by spaces.\nThe maximum number of total rolls is 100."
    )
    suspend fun roll(context: CommandContext, @JoinRemaining @Optional dice: String = "d6") {
        data class RollData(val count: BigInteger, val size: BigInteger)

        val rolls = dice.split(Regex(" +"))
        val converted = rolls.mapNotNull {
            DIE_REGEX.matchEntire(it)
                ?.let { match ->
                    val count = match.groups[1]?.value?.toBigInteger() ?: BigInteger.ONE
                    val size = match.groups[2]?.value?.toBigInteger()

                    size?.let { RollData(count, size) }
                }
        }

        val invalidCount = rolls.size - converted.size
        val grouped = converted.groupBy { it.size }
            .mapValues { it.value.sumOf { it.count } }

        val totalRolls = grouped.values.sumOf { it }

        if (totalRolls > ONE_HUNDRED) {
            context.replyAsync(
                failureEmbed(context.jda)
                    .appendDescription("The maximum number of dice rolls is 100 (received $totalRolls.)")
                    .build()
            )

            return
        }

        val results = grouped.mapValues { (key, value) ->
            var sum = BigInteger.ZERO
            repeat(value.toInt()) { sum += (randomBigInteger(key) + BigInteger.ONE) }

            Pair(value, sum)
        }

        val sum = results.values.sumOf { it.second }

        context.replyAsync(
            buildString {
                if (invalidCount != 0) {
                    append("$invalidCount provided ${invalidCount.pluralize("roll was", "rolls were")} invalid.\n\n")
                }

                for ((size, data) in results) {
                    appendLine("Rolled ${data.first}d${size}: ${data.second}!")
                }

                if (results.size > 1) {
                    appendLine()
                    appendLine("Sum: $sum")
                }
            }
        )
    }
}

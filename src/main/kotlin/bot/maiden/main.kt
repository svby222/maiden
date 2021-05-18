package bot.maiden

import bot.maiden.modules.*
import com.typesafe.config.ConfigFactory
import kotlin.io.path.Path

val START_TIMESTAMP = System.currentTimeMillis()

fun main(args: Array<String>) {
    val configPath = args.getOrNull(0)?.let(::Path) ?: Path("./maiden.conf")
    println("Loading configuration (${configPath})")

    val fileConfig = ConfigFactory.parseFile(configPath.toFile())
    val config = ConfigFactory.load(fileConfig)

    val bot = Bot.create(config)
    bot.addModules(
        listOf(
            Administration,

            Inspirobot,
            Goodreads,
            Horoscope,

            Phone
        )
    )

    bot.start()
}

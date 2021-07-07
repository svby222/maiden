package bot.maiden

import bot.maiden.modules.*
import bot.maiden.modules.modal.Modals
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import kotlin.io.path.Path

val START_TIMESTAMP = System.currentTimeMillis()
private val MAIN_LOGGER = LoggerFactory.getLogger("main")

fun main(args: Array<String>) {
    val configPath = args.getOrNull(0)?.let(::Path) ?: Path("./maiden.conf")
    MAIN_LOGGER.info("Loading configuration (from '${configPath}')")

    val fileConfig = ConfigFactory.parseFile(configPath.toFile())
    val config = ConfigFactory.load(fileConfig)

    val bot = Bot.create(config)
    bot.addModules(
        listOf(
            Modals,
            Dialog,

            Administration,
            Schedule,

            Inspirobot,
            Goodreads,
            Horoscope,

            Phone,
            Dectalk,
        )
    )

    bot.start()
}

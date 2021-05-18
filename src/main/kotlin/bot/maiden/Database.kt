package bot.maiden

import bot.maiden.model.GuildData
import com.typesafe.config.Config
import net.dv8tion.jda.api.entities.Guild
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.exception.ConstraintViolationException
import java.util.*
import javax.persistence.PersistenceException

class Database(config: Config) {
    lateinit var sessionFactory: SessionFactory
    var version: String? = null

    private val hibernateProperties = mapOf(
        "hibernate.connection.url" to config.getString("maiden.core.database.connectionString"),
        "hibernate.connection.username" to config.getString("maiden.core.database.username"),
        "hibernate.connection.password" to config.getString("maiden.core.database.password"),

        "hibernate.dialect" to "org.hibernate.dialect.PostgreSQLDialect",
        "hibernate.connection.driver_class" to "org.postgresql.Driver",
        "show_sql" to "true",
    )

    fun init() {
        // TODO clean up database operations (urgent!)

        val properties = Properties().apply { putAll(hibernateProperties) }

        val configuration = Configuration()
            .addProperties(properties)
            .addAnnotatedClass(GuildData::class.java)

        val factory = configuration.buildSessionFactory()
        this.sessionFactory = factory

        withSession {
            val query = it.createNativeQuery("select version()")
            val result = query.uniqueResult() as? String

            result?.let {
                this.version = it.substringBefore("on").substringBefore("(").trim()
            }
        }
    }

    inline fun <T> withSession(block: (Session) -> T) = sessionFactory.openSession().use(block)

    fun createGuildEntity(guild: Guild) {
        try {
            withSession { session ->
                session.beginTransaction().let { tx ->
                    val newData = GuildData()
                    newData.guildId = guild.idLong

                    // Generate a phone number
                    newData.phoneNumber = buildString {
                        append(guild.id.take(3))
                        repeat(7) { append(('0'..'9').random().toString()) }
                    }

                    session.save(newData)

                    tx.commit()
                }
            }
        } catch (e: PersistenceException) {
            if (e.cause is ConstraintViolationException) {
                // Ignore
                return
            } else throw e
        }

        println("Inserted guild entity for ${guild.idLong}")
    }
}

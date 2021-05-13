package bot.maiden

import bot.maiden.model.GuildData
import net.dv8tion.jda.api.entities.Guild
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.exception.ConstraintViolationException
import java.util.*
import javax.persistence.PersistenceException

object Database {
    lateinit var sessionFactory: SessionFactory
    var version: String? = null

    fun init() {
        // TODO This relies on trust authentication (with no password!)
        // This is bad for obvious reasons, but this is just here for testing purposes.
        // TODO clean up database operations (urgent!)
        val properties = Properties().apply {
            putAll(
                mapOf(
                    "hibernate.connection.url" to "jdbc:postgresql://localhost/",
                    "hibernate.connection.username" to "postgres",
                    "hibernate.dialect" to "org.hibernate.dialect.PostgreSQLDialect",
                    "hibernate.connection.driver_class" to "org.postgresql.Driver",
                    "show_sql" to "true",
                )
            )
        }

        val configuration = Configuration()
            .addProperties(properties)
            .addAnnotatedClass(GuildData::class.java)

        val factory = configuration.buildSessionFactory()
        this.sessionFactory = factory

        factory.openSession().use {
            val query = it.createNativeQuery("select version()")
            val result = query.uniqueResult() as? String

            result?.let {
                this.version = it.substringBefore("on").substringBefore("(").trim()
            }
        }
    }

    fun createGuildEntity(sessionFactory: SessionFactory, guild: Guild) {
        try {
            sessionFactory.openSession().use { session ->
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

package bot.maiden.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(schema = "maidendb")
class GuildData {
    @Id
    @Column(name = "guild_id")
    var guildId: Long = 0

    @Column(name = "phone_number", length = 16, nullable = true)
    var phoneNumber: String? = null

    @Column(name = "phone_channel", nullable = true)
    var phoneChannel: Long? = null
}

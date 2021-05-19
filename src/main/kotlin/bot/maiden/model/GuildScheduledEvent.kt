package bot.maiden.model

import java.time.Instant
import javax.persistence.*

@Entity
@Table(schema = "maidendb")
class GuildScheduledEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    var eventId: Int = 0

    @Column(name = "guild_id")
    var guildId: Long = 0

    @Column(name = "channel_id", nullable = false)
    var channelId: Long = 0

    @Column(name = "requester_id", nullable = false)
    var requesterId: Long = 0

    @Column(name = "start_at", nullable = false)
    var startAt: Instant? = null

    @Column(name = "interval_s", nullable = false)
    var intervalSeconds: Long = 0

    @Lob
    @Column(name = "command_string", nullable = false)
    var command: String? = null
}

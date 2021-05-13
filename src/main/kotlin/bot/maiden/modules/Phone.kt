package bot.maiden.modules

import bot.maiden.*
import bot.maiden.Database.sessionFactory
import bot.maiden.model.GuildData
import bot.maiden.model.PhoneNumber
import bot.maiden.modules.Administration.OWNER_ID
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Message
import java.util.concurrent.ConcurrentHashMap

object Phone : Module {
    private val USER_MENTION_REGEX = Regex("<@!?(\\d+)>")
    private val GENERAL_MENTION_REGEX = Regex("@(\\w+)")

    enum class ConnectionState {
        Closed,
        Requested,
        RequestedRecipient,
        Active
    }

    data class PartnerState(
        val connectionState: ConnectionState,
        val source: Long,
        val partner: Long,
        val sourceChannel: Long,
        val partnerChannel: Long,
    ) {
        fun flip() = PartnerState(
            connectionState = when (this.connectionState) {
                ConnectionState.Requested -> ConnectionState.RequestedRecipient
                ConnectionState.RequestedRecipient -> ConnectionState.Requested
                else -> this.connectionState
            },
            source = this.partner,
            partner = this.source,
            sourceChannel = this.partnerChannel,
            partnerChannel = this.sourceChannel
        )
    }

    private val partners = ConcurrentHashMap<Long, PartnerState>()

    @Command
    suspend fun call(context: CommandContext, target: String) {
        fun abort() {
            partners.remove(context.message.guild.idLong)
        }

        val data = sessionFactory.openSession()
            .use { it.find(GuildData::class.java, context.message.guild.idLong) }
            ?: run {
                context.message.channel.sendMessage(
                    failureEmbed(context.message.jda)
                        .appendDescription(
                            """
                            No GuildData entity found for this guild.
                            This shouldn't happen :frowning:
                            
                            I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                        """.trimIndent()
                        )
                        .build()
                ).await()

                return abort()
            }

        val sourceNumber = PhoneNumber(data.phoneNumber ?: "?")

        if (target.isBlank()) {
            // Return phone number
            context.message.channel
                .sendMessage(":mobile_phone: Your server's phone number is $sourceNumber")
                .await()
        } else {
            val previous =
                partners.putIfAbsent(context.message.guild.idLong, PartnerState(ConnectionState.Closed, -1, -1, -1, -1))
            if (previous != null) {
                context.message.channel.sendMessage(":mobile_phone: You're already in a call! Hang up with m!hangup.")
                    .await()
                return
            }

            val targetNumber = PhoneNumber(target.filter { it in '0'..'9' })
            if (targetNumber.value.isBlank()) {
                context.message.channel.sendMessage(":mobile_phone: That's not a valid number.").await()
            } else {
                // Find guild
                val otherGuild = sessionFactory.openSession()
                    .use {
                        it.createQuery("from GuildData data where data.phoneNumber = :phoneNumber")
                            .setParameter("phoneNumber", targetNumber.value)
                            .uniqueResult()
                    } as? GuildData

                if (otherGuild == null) {
                    context.message.channel.sendMessage(":mobile_phone: That number doesn't seem to be in use...")
                        .await()
                    return abort()
                }
                if (otherGuild.guildId == context.message.guild.idLong) {
                    context.message.channel.sendMessage(":mobile_phone: You can't call yourself!").await()
                    return abort()
                }

                val recipient = context.message.jda.getGuildById(otherGuild.guildId)
                if (recipient == null) {
                    context.message.channel.sendMessage(":mobile_phone: That number doesn't seem to be in use...")
                        .await()
                    return abort()
                }

                if (partners[recipient.idLong] != null) {
                    context.message.channel.sendMessage(":mobile_phone: That number seems to be busy. Try calling back later!")
                        .await()
                    return abort()
                }

                val targetChannel = otherGuild.phoneChannel?.let { context.message.jda.getTextChannelById(it) }
                if (targetChannel == null) {
                    context.message.channel
                        .sendMessage(":mobile_phone: The other server hasn't set their phone channel yet, so I can't put you through to anyone.")
                        .await()
                    return abort()
                }

                val state = PartnerState(
                    ConnectionState.Requested,
                    context.message.guild.idLong, recipient.idLong,
                    context.message.channel.idLong, targetChannel.idLong
                )
                partners[state.source] = state
                partners[state.partner] = state.flip()

                context.message.channel.sendMessage(":mobile_phone: You call $targetNumber...").await()

                targetChannel.sendMessage(
                    """
                    :mobile_phone: You're receiving a call from $sourceNumber!
                    Use m!pickup to pick up, or m!hangup to decline.
                """.trimIndent()
                ).await()
            }
        }
    }

    @Command(hidden = true)
    suspend fun pickup(context: CommandContext, ignore: String) {
        val currentState = partners[context.message.guild.idLong] ?: return

        if (currentState.connectionState == ConnectionState.RequestedRecipient && currentState.sourceChannel == context.message.channel.idLong) {
            partners.compute(currentState.partner) { _, state -> state?.copy(connectionState = ConnectionState.Active) }
            partners[context.message.guild.idLong] = currentState.copy(connectionState = ConnectionState.Active)

            context.message.channel.sendMessage(":mobile_phone: You picked up the phone.").await()

            // TODO channel doesn't exist?
            context.message.jda.getTextChannelById(currentState.partnerChannel)
                ?.sendMessage(":mobile_phone: The other party picked up the phone.")?.await()
                ?: return
        }
    }

    @Command(hidden = true)
    suspend fun hangup(context: CommandContext, ignore: String) {
        val oldState = partners.remove(context.message.guild.idLong)
        if (oldState != null) partners.remove(oldState.partner)
        else return

        context.message.channel.sendMessage(":mobile_phone: You hung up the phone.").await()

        context.message.jda.getTextChannelById(oldState.partnerChannel)
            ?.sendMessage(":mobile_phone: The other party hung up the phone.")?.await()
    }

    @Command
    suspend fun `set-phone-channel`(context: CommandContext, ignore: String) {
        if (context.message.author.idLong != OWNER_ID &&
            context.message.member?.permissions?.contains(Permission.ADMINISTRATOR) != true
        ) {
            // TODO actual permission handling
            context.message.channel
                .sendMessage("You can't do that (not an administrator)").await()
            return
        }

        val channel = context.message.channel as? GuildChannel ?: return

        sessionFactory.openSession()
            .use {
                it.beginTransaction().let { tx ->
                    val data = it.find(GuildData::class.java, context.message.guild.idLong)
                    data.phoneChannel = channel.idLong

                    it.save(data)

                    tx.commit()

                    data
                }
            }
            ?: run {
                context.message.channel.sendMessage(
                    failureEmbed(context.message.jda)
                        .appendDescription(
                            """
                            No GuildData entity found for this guild.
                            This shouldn't happen :frowning:
                            
                            I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                        """.trimIndent()
                        )
                        .build()
                ).await()
            }

        context.message.channel
            .sendMessage(":mobile_phone: <#${channel.idLong}> will now be used to receive incoming calls.").await()
    }

    override suspend fun onMessage(message: Message) {
        if (message.author.isBot) return
        val state = partners[message.guild.idLong] ?: return

        if (state.connectionState == ConnectionState.Active && message.channel.idLong == state.sourceChannel) {
            var messageTransformed = message.contentRaw

            // Replace user mentions
            messageTransformed = messageTransformed.replace(USER_MENTION_REGEX) {
                val name = it.groups[1]?.value?.toLongOrNull()?.let(message.jda::getUserById)?.name ?: "deleted-user"
                "@\u200B$name"
            }

            // Replace general mentions
            messageTransformed = messageTransformed.replace(GENERAL_MENTION_REGEX, "@\u200B\$1")

            // TODO filter URLs

            message.jda.getTextChannelById(state.partnerChannel)
                ?.sendMessage("**${message.author.asTag} says**: $messageTransformed".take(2000))
                ?.await()
        }
    }
}

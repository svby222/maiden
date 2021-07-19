package bot.maiden.modules

import bot.maiden.*
import bot.maiden.model.GuildData
import bot.maiden.model.PhoneNumber
import bot.maiden.modules.Common.GENERAL_MENTION_REGEX
import bot.maiden.modules.Common.USER_MENTION_REGEX
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.Message
import java.util.concurrent.ConcurrentHashMap

object Phone : Module {
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
    @HelpText(
        "View your server's phone number.",
        group = "phone"
    )
    suspend fun call(context: CommandContext) {
        val data = context.database.withSession { it.find(GuildData::class.java, context.guild.idLong) }
            ?: run {
                context.replyAsync(
                    failureEmbed(context.jda)
                        .appendDescription(
                            """
                            No GuildData entity found for this guild.
                            This shouldn't happen :frowning:
                            
                            I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                        """.trimIndent()
                        )
                        .build()
                )

                return
            }

        val sourceNumber = PhoneNumber(data.phoneNumber ?: "?")

        context.replyAsync(":mobile_phone: Your server's phone number is $sourceNumber")
    }

    @Command
    @HelpText(
        summary = "Call another server",
        description = "Call another server by using their assigned phone number.",
        group = "phone"
    )
    suspend fun call(context: CommandContext, @JoinRemaining target: String) {
        fun abort() {
            partners.remove(context.guild.idLong)
        }

        val data = context.database.withSession { it.find(GuildData::class.java, context.guild.idLong) }
            ?: run {
                context.replyAsync(
                    failureEmbed(context.jda)
                        .appendDescription(
                            """
                            No GuildData entity found for this guild.
                            This shouldn't happen :frowning:
                            
                            I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                        """.trimIndent()
                        )
                        .build()
                )

                return abort()
            }

        val sourceNumber = PhoneNumber(data.phoneNumber ?: "?")

        val previous =
            partners.putIfAbsent(context.guild.idLong, PartnerState(ConnectionState.Closed, -1, -1, -1, -1))
        if (previous != null) {
            context.replyAsync(":mobile_phone: You're already in a call! Hang up with m!hangup.")
            return
        }

        val targetNumber = PhoneNumber(target.filter { it in '0'..'9' })
        if (targetNumber.value.isBlank()) {
            context.replyAsync(":mobile_phone: That's not a valid number.")
            return abort()
        } else {
            // Find guild
            val otherGuild = context.database.withSession {
                it.createQuery("from GuildData data where data.phoneNumber = :phoneNumber")
                    .setParameter("phoneNumber", targetNumber.value)
                    .uniqueResult()
            } as? GuildData

            if (otherGuild == null) {
                context.replyAsync(":mobile_phone: That number doesn't seem to be in use...")
                return abort()
            }
            if (otherGuild.guildId == context.guild.idLong) {
                context.replyAsync(":mobile_phone: You can't call yourself!")
                return abort()
            }

            val recipient = context.jda.getGuildById(otherGuild.guildId)
            if (recipient == null) {
                context.replyAsync(":mobile_phone: That number doesn't seem to be in use...")
                return abort()
            }

            if (partners[recipient.idLong] != null) {
                context.replyAsync(":mobile_phone: That number seems to be busy. Try calling back later!")
                return abort()
            }

            val targetChannel = otherGuild.phoneChannel?.let { context.jda.getTextChannelById(it) }
            if (targetChannel == null) {
                context.replyAsync(":mobile_phone: The other server hasn't set their phone channel yet, so I can't put you through to anyone.")
                return abort()
            }

            val state = PartnerState(
                ConnectionState.Requested,
                context.guild.idLong, recipient.idLong,
                context.channel.idLong, targetChannel.idLong
            )
            partners[state.source] = state
            partners[state.partner] = state.flip()

            context.replyAsync(":mobile_phone: You call $targetNumber...")

            targetChannel.sendMessage(
                """
                :mobile_phone: You're receiving a call from $sourceNumber!
                Use m!pickup to pick up, or m!hangup to decline.
            """.trimIndent()
            ).await()
        }
    }

    @Command
    @HelpText(
        summary = "Accept an incoming call",
        description = "Accept an incoming call from another server.",
        group = "phone"
    )
    suspend fun pickup(context: CommandContext) {
        val currentState = partners[context.guild.idLong] ?: return

        if (currentState.connectionState == ConnectionState.RequestedRecipient && currentState.sourceChannel == context.channel.idLong) {
            partners.compute(currentState.partner) { _, state -> state?.copy(connectionState = ConnectionState.Active) }
            partners[context.guild.idLong] = currentState.copy(connectionState = ConnectionState.Active)

            context.replyAsync(":mobile_phone: You picked up the phone.")

            // TODO channel doesn't exist?
            context.jda.getTextChannelById(currentState.partnerChannel)
                ?.sendMessage(":mobile_phone: The other party picked up the phone.")?.await()
                ?: return
        }
    }

    @Command
    @HelpText(
        summary = "End or decline a call",
        description = "End a call, or decline an incoming call from another server.",
        group = "phone"
    )
    suspend fun hangup(context: CommandContext) {
        val oldState = partners.remove(context.guild.idLong)
        if (oldState != null) partners.remove(oldState.partner)
        else return

        context.replyAsync(":mobile_phone: You hung up the phone.")

        context.jda.getTextChannelById(oldState.partnerChannel)
            ?.sendMessage(":mobile_phone: The other party hung up the phone.")?.await()
    }

    @Command
    @HelpText(
        summary = "Set the channel used for incoming calls",
        description = "Set the channel you wish to receive incoming calls in. Only available to server administrators.",
        group = "phone"
    )
    suspend fun `set-phone-channel`(context: CommandContext) {
        context.requester ?: return

        if (!context.requester.isOwner(context.bot) &&
            context.guild.getMember(context.requester)?.permissions?.contains(Permission.ADMINISTRATOR) != true
        ) {
            // TODO actual permission handling
            context.replyAsync("You can't do that (not an administrator)")
            return
        }

        val channel = context.channel as? GuildChannel ?: return

        context.database.withSession {
            it.beginTransaction().let { tx ->
                val data = it.find(GuildData::class.java, context.guild.idLong)
                data.phoneChannel = channel.idLong

                it.save(data)

                tx.commit()

                data
            }
        } ?: run {
            context.replyAsync(
                failureEmbed(context.jda)
                    .appendDescription(
                        """
                            No GuildData entity found for this guild.
                            This shouldn't happen :frowning:
                            
                            I'd appreciate it if you could notify the author (`m!help`). Thanks :smile:
                        """.trimIndent()
                    )
                    .build()
            )
        }

        context.replyAsync(":mobile_phone: <#${channel.idLong}> will now be used to receive incoming calls.")
    }

    override suspend fun onMessage(message: Message): Boolean {
        if (message.author.isBot) return true
        val state = partners[message.guild.idLong] ?: return true

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

        return true
    }
}

package bot.maiden.modules

import bot.maiden.*
import bot.maiden.common.ArgumentConverter
import bot.maiden.common.baseEmbed
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import java.time.Duration
import kotlin.reflect.full.findAnnotation

fun User.isOwner(bot: Bot) = idLong == bot.ownerId

object Administration : Module {
    override suspend fun initialize(bot: Bot) {
        bot.conversions.addConverter(object : ArgumentConverter<String, GuildChannelPair> {
            override val fromType get() = String::class
            override val toType get() = GuildChannelPair::class

            override suspend fun convert(from: String): Result<GuildChannelPair> {
                val (guildId, channelId) = from.split("/").map { it.toLong() }

                val guild = bot.jda.getGuildById(guildId)
                val channel = guild?.getTextChannelById(channelId)

                guild ?: return Result.failure(Exception("Guild not found"))
                channel ?: return Result.failure(Exception("Channel not found"))

                return Result.success(GuildChannelPair(guild, channel))
            }
        }, 1)
    }

    @Command(hidden = true)
    suspend fun say(context: CommandContext, @JoinRemaining text: String) {
        context.requester ?: return

        if (context.requester.isOwner(context.bot)) {
            context.message?.delete()?.await()
            context.channel.sendMessage(text).await()
        } else {
            context.replyAsync("${context.requester.asMention} no")
        }
    }

    data class GuildChannelPair(
        val guild: Guild,
        val channel: TextChannel
    )

    @Command(hidden = true)
    suspend fun sayin(context: CommandContext, target: GuildChannelPair, @JoinRemaining text: String) {
        context.requester ?: return

        if (context.requester.isOwner(context.bot)) {
            target.channel.sendMessage(text).await()
        } else {
            context.replyAsync("${context.requester.asMention} no")
        }
    }

    @Command
    suspend fun invite(context: CommandContext) {
        context.replyAsync(
            baseEmbed(context)
                .setTitle("Invite ${context.jda.selfUser.name} to your server")
                .setThumbnail(context.jda.selfUser.avatarUrl)
                .setDescription(
                    "**[Click here](https://discord.com/api/oauth2/authorize?client_id=841947222492577812&permissions=8&scope=bot)**",
                )
                .build()
        )
    }

    @Command
    suspend fun help(context: CommandContext) {
        val ownerUser = context.jda.retrieveUserById(context.bot.ownerId).await()

        context.replyAsync(
            baseEmbed(context)
                .setColor(Color.WHITE)
                .setTitle("About ${context.jda.selfUser.name}")
                .setImage("https://i.imgur.com/S4MOq1f.png")
                .setDescription(
                    """
                    Hi! I'm a bot made by `${ownerUser.asTag}` (${ownerUser.asMention}).
                    
                    This bot is currently self-hosted, so there may be downtime, but I'll try my best to keep it running. It's also very much a work in progress, so you can check back for new additions if you want.
                """.trimIndent()
                )
                .addField("Command prefix", "`m!`", true)
                .addField(
                    "Invite link",
                    "**[Click here](https://discord.com/api/oauth2/authorize?client_id=841947222492577812&permissions=8&scope=bot)**",
                    true
                )
                .addField(
                    "Getting started", """
                    Here are some commands you can try out to get started:
                    `m!commands`
                    `m!invite`
                """.trimIndent(), false
                )
                .addField(
                    "Environment information", """
                    Running on ${System.getProperty("os.name")} ${System.getProperty("os.version")}
                    Kotlin ${KotlinVersion.CURRENT} on JDK ${System.getProperty("java.version")}
                    ${context.database.version ?: "Unknown database"}

                    **Uptime**: ${Duration.ofMillis(System.currentTimeMillis() - START_TIMESTAMP).toPrettyString()}
                    **Server count**: ${context.jda.guilds.size}
                """.trimIndent(), true
                )
                .addField("Source repository", "[github:musubii/maiden](https://github.com/musubii/maiden)", true)
                .build()
        )
    }

    @Command
    suspend fun commands(context: CommandContext) {
        // TODO char limit
        context.replyAsync(
            baseEmbed(context)
                .setTitle("List of commands")
                .setThumbnail(context.jda.selfUser.avatarUrl)
                .apply {
                    setDescription(
                        buildString {
                            for ((_, function) in context.commands) {
                                val annotation = function.findAnnotation<Command>() ?: continue
                                if (annotation.hidden) continue

                                appendLine("`${function.name}`")
                            }
                        }
                    )
                }
                .addField("Command prefix", "`m!`", true)
                .build()
        )
    }

    @Command(hidden = true)
    fun `set-motd`(context: CommandContext, motd: String) {
        context.requester ?: return

        if (context.requester.isOwner(context.bot)) {
            context.bot.motd = motd.takeUnless { it.isBlank() }
        }
    }

    @Command(hidden = true)
    fun `throw`(context: CommandContext) {
        throw Exception("Success")
    }
}

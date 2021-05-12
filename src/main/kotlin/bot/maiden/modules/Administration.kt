package bot.maiden.modules

import bot.maiden.*
import net.dv8tion.jda.api.EmbedBuilder
import java.awt.Color
import java.time.Duration
import java.time.Instant
import kotlin.reflect.full.findAnnotation

object Administration : Module {
    const val OWNER_ID = 829795781715951697L

    @Command(hidden = true)
    suspend fun say(context: CommandContext, text: String) {
        if (context.message.author.idLong == OWNER_ID) {
            context.message.delete().await()
            context.message.channel.sendMessage(text).await()
        } else {
            context.message.channel.sendMessage("${context.message.author.asMention} no")
        }
    }

    @Command(hidden = true)
    suspend fun sayin(context: CommandContext, query: String) {
        val splitIndex = query.indexOf(' ')
        val gc = query.substring(0, splitIndex).trim()
        val text = query.substring(splitIndex + 1).trim()

        val (guildId, channelId) = gc.split("/").map { it.toLong() }

        val channel = context.message.jda.getGuildById(guildId)?.getTextChannelById(channelId) ?: run {
            context.message.channel.sendMessage("I can't do that.").await()
            return
        }

        if (context.message.author.idLong == OWNER_ID) {
            channel.sendMessage(text).await()
        } else {
            context.message.channel.sendMessage("${context.message.author.asMention} no").await()
        }
    }

    @Command
    suspend fun invite(context: CommandContext, ignore: String) {
        context.message.channel.sendMessage(
            EmbedBuilder()
                .setTitle("Invite ${context.message.jda.selfUser.name} to your server")
                .setThumbnail(context.message.jda.selfUser.avatarUrl)
                .setDescription(
                    "**[Click here](https://discord.com/api/oauth2/authorize?client_id=841947222492577812&permissions=8&scope=bot)**",
                )
                .setFooter("Requested by ${context.message.author.asTag}")
                .setTimestamp(Instant.now())
                .build()
        ).await()
    }

    @Command
    suspend fun help(context: CommandContext, ignore: String) {
        val ownerUser = context.message.jda.retrieveUserById(OWNER_ID).await()

        context.message.channel.sendMessage(
            EmbedBuilder()
                .setColor(Color.WHITE)
                .setTitle("About ${context.message.jda.selfUser.name}")
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

                    **Uptime**: ${Duration.ofMillis(System.currentTimeMillis() - START_TIMESTAMP).toPrettyString()}
                    **Server count**: ${context.message.jda.guilds.size}
                """.trimIndent(), true
                )
                .addField("Source repository", "[github:musubii/maiden](https://github.com/musubii/maiden)", true)
                .setFooter("Requested by ${context.message.author.asTag}")
                .setTimestamp(Instant.now())
                .build()
        ).await()
    }

    @Command
    suspend fun commands(context: CommandContext, ignore: String) {
        // TODO char limit
        context.message.channel.sendMessage(
            EmbedBuilder()
                .setTitle("List of commands")
                .setThumbnail(context.message.jda.selfUser.avatarUrl)
                .apply {
                    setDescription(
                        buildString {
                            for ((_, function) in context.handlers) {
                                val annotation = function.findAnnotation<Command>() ?: continue
                                if (annotation.hidden) continue

                                appendLine("`${function.name}`")
                            }
                        }
                    )
                }
                .addField("Command prefix", "`m!`", true)
                .setFooter("Requested by ${context.message.author.asTag}")
                .setTimestamp(Instant.now())
                .build()
        ).await()
    }

    @Command(hidden = true)
    suspend fun `throw`(context: CommandContext, ignore: String) {
        throw Exception("Success")
    }
}

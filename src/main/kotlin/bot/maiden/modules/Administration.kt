package bot.maiden.modules

import bot.maiden.*
import bot.maiden.common.ArgumentConverter
import bot.maiden.common.baseEmbed
import bot.maiden.modules.Common.COMMAND_PARAMETER_PREDICATE
import bot.maiden.modules.modal.DialogStepModal
import bot.maiden.modules.modal.Modals
import bot.maiden.modules.modal.StepModal
import bot.maiden.modules.modal.buildDialog
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import java.awt.Color
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

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

    @Command
    @HelpText("Fetch a user's account information.")
    suspend fun userinfo(context: CommandContext, user: User) {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val member = context.guild.getMember(user)

        context.replyAsync(
            baseEmbed(context)
                .setTitle("Info for user ${user.asTag}")
                .setDescription("User ID: ${user.id}")
                .setThumbnail(user.avatarUrl ?: user.defaultAvatarUrl)
                .addField("Creation date", user.timeCreated.format(formatter), true)
                .apply { member?.let { addField("Join date", it.timeJoined.format(formatter), true) } }
                .build()
        )
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
    @HelpText("Show the bot's invite link.")
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
    @HelpText(
        "Show the bot information dialog.",
        group = "basic"
    )
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

    // TODO move these to a common utility file
    val typeNameOverrides = mapOf(
        String::class to "text",
        BigInteger::class to "int",
        BigDecimal::class to "num",
    )

    fun createDisplayTitle(command: Bot.RegisteredCommand): String {
        val parameters = command.function.parameters.filter(COMMAND_PARAMETER_PREDICATE)

        return buildString {
            append("`")
            append("m!")
            append(command.name)

            if (parameters.isNotEmpty()) {
                append(" ")
                append(
                    parameters
                        .joinToString(" ") {
                            buildString {
                                append("[")

                                append(it.name)
                                append(": ")
                                append(typeNameOverrides[it.type.jvmErasure] ?: it.type.jvmErasure.simpleName)

                                if (it.hasAnnotation<JoinRemaining>()) append("+")
                                if (it.hasAnnotation<Optional>()) append("?")

                                append("]")
                            }
                        }
                )
            }

            append("`")
        }
    }

    @Command
    @HelpText(
        "Show help for the specified command.",
        group = "basic"
    )
    suspend fun help(context: CommandContext, command: String) {
        val commands = context.commands.filter { it.name.equals(command, ignoreCase = true) }

        if (commands.isEmpty()) {
            context.replyAsync("That command doesn't seem to exist.")
            return
        }

        context.replyAsync(
            baseEmbed(context)
                .setColor(Color.WHITE)
                .setTitle("About $command")
                .apply {
                    // TODO this is suboptimal; try caching on startup
                    val related = mutableSetOf<Bot.RegisteredCommand>()

                    for ((i, command) in commands.withIndex()) {
                        val displayTitle = createDisplayTitle(command)

                        addField(
                            if (commands.size == 1) displayTitle
                            else "#${i + 1}: $displayTitle",
                            command.helpText?.description ?: "_No help text available._",
                            false
                        )

                        // Find other commands with the same group
                        command.helpText?.group?.takeIf { it.isNotBlank() }
                            ?.let { groupName ->

                                related.addAll(
                                    context.commands.asSequence()
                                        .filter { it.helpText?.group == groupName }
                                        .toList()
                                )
                            }
                    }

                    val relatedFiltered = related.filterNot { it in commands }

                    if (relatedFiltered.isNotEmpty()) {
                        addField("Related commands", buildString {
                            for (command in relatedFiltered) {
                                append(createDisplayTitle(command))
                                append(": ")
                                command.helpText?.displaySummary?.let { append(it) }
                                appendLine()
                            }
                        }, false)
                    }
                }
                .build()
        )
    }

    @Command
    @HelpText(
        "Show a list of all available commands.",
        group = "basic"
    )
    suspend fun commands(context: CommandContext) {
        // TODO char limit
        val commands = context.commands
            .asSequence()
            .filterNot { it.function.findAnnotation<Command>()?.hidden == true }
            .groupBy { it.name }
            .toList()
            .sortedBy { it.first }
            .map { it.second }
            .flatten()
            .toList()

        val chunks = commands.chunked(15)

        var chunkIndex = 0

        val modal = buildDialog {
            title = "List of commands"

            addStep(dynamic = true) {
                replacePrevious = true

                val previous = option(
                    DialogStepModal.StepOption("Previous", icon = Emoji.fromMarkdown("⬅️"), data = "previous")
                )
                val next =
                    option(
                        DialogStepModal.StepOption("Next", icon = Emoji.fromMarkdown("➡️"), data = "next")
                    )

                val close =
                    option(
                        DialogStepModal.StepOption(
                            "Close",
                            icon = Emoji.fromMarkdown("❎"),
                            buttonStyle = ButtonStyle.DANGER
                        )
                    )

                mainText = "**Use `m!help [command-name]` for information on how to use a specific command.**"

                editEmbed { title ->
                    setThumbnail(context.jda.selfUser.avatarUrl)
                    addField("Command prefix", "`m!`", false)

                    setTitle("${(title)} (page ${chunkIndex + 1} of ${chunks.size})")

                    for (command in chunks[chunkIndex]) {
                        val helpText = command.helpText?.displaySummary

                        // TODO short/long text; just split for now
                        val shortText = helpText?.split(Regex("\\.(?:\\s|$)"))?.firstOrNull()

                        addField(
                            "`${command.name}`",
                            shortText,
                            true
                        )
                    }
                }

                onComplete { option, _, _ ->
                    when (option) {
                        previous -> {
                            chunkIndex = max(0, chunkIndex - 1)
                        }
                        next -> {
                            chunkIndex = min(chunks.lastIndex, chunkIndex + 1)
                        }

                        close -> {
                            return@onComplete StepModal.StepResult.Finish
                        }
                    }

                    StepModal.StepResult.GotoCurrent
                }
            }
        }

        Modals.beginModal(context.channel, context, modal).await()
    }

    @Command(hidden = true)
    fun `set-motd`(context: CommandContext, @JoinRemaining motd: String) {
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

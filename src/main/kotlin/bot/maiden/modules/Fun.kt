package bot.maiden.modules

import bot.maiden.*
import bot.maiden.common.baseEmbed
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildChannel

object Fun : Module {
    @Command
    @HelpText(
        summary = "Pick a random user",
        description = "Select a random user from the current channel."
    )
    suspend fun roulette(context: CommandContext, @Optional @JoinRemaining prize: String = "") {
        if (context.channel !is GuildChannel) return

        val winner = context.guild.loadMembers().await()
            .filter { it.getPermissions(context.channel).contains(Permission.MESSAGE_READ) }
            .filterNot { it.user.isBot }.random()

        val preText =
            if (prize.isBlank()) "And the winner is..."
            else "And the winner of $prize is..."

        val builder = baseEmbed(context)
            .setTitle("Roulette")

        val message = context.replyAsync(builder.setDescription("$preText\n\n🥁🥁").build())

        delay(3000L)

        message.editMessage(builder.setDescription("$preText\n\n${winner.asMention}!").build()).await()
    }
}

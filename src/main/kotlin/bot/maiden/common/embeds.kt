package bot.maiden.common

import bot.maiden.CommandContext
import bot.maiden.CommandSource
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import java.awt.Color
import java.time.Instant

data class EmbedDefaultAdapters(
    val footerText: ((String) -> String)? = null
)

fun baseEmbed(context: CommandContext?, adapters: EmbedDefaultAdapters = EmbedDefaultAdapters()): EmbedBuilder {
    return EmbedBuilder()
        .apply {
            if (context != null) {
                val requesterName = context.requester?.asTag ?: "unknown user"

                val (footer, avatarUser) = when (context.source) {
                    CommandSource.User -> Pair("Requested by $requesterName", context.requester)
                    CommandSource.Scheduled -> Pair("Scheduled by $requesterName", null)
                    CommandSource.Other -> Pair("", null)
                }

                setFooter(
                    (adapters.footerText?.invoke(footer) ?: footer).takeUnless { it.isBlank() },
                    avatarUser?.avatarUrl ?: avatarUser?.defaultAvatarUrl
                )
            }
        }
        .setTimestamp(Instant.now())
}

fun failureEmbed(jda: JDA): EmbedBuilder {
    return baseEmbed(null)
        .setTitle("Failure")
        .setThumbnail(jda.selfUser.avatarUrl)
        .setDescription("**An error occurred:**\n")
        .setColor(Color.RED)
}

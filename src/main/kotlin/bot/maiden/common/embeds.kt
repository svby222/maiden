package bot.maiden.common

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import java.time.Instant

fun baseEmbed(requester: User?): EmbedBuilder {
    return EmbedBuilder()
        .apply { requester?.let { setFooter("Requested by ${it.asTag}", it.avatarUrl ?: it.defaultAvatarUrl) } }
        .setTimestamp(Instant.now())
}

fun failureEmbed(jda: JDA): EmbedBuilder {
    return baseEmbed(null)
        .setTitle("Failure")
        .setThumbnail(jda.selfUser.avatarUrl)
        .setDescription("**An error occurred:**\n")
        .setColor(Color.RED)
}

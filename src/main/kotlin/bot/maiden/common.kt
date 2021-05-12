package bot.maiden

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import java.awt.Color
import java.time.Instant

fun failureEmbed(jda: JDA): EmbedBuilder {
    return EmbedBuilder()
        .setTitle("Failure")
        .setThumbnail(jda.selfUser.avatarUrl)
        .setDescription("**An error occurred:**\n")
        .setColor(Color.RED)
        .setTimestamp(Instant.now())
}

package bot.maiden.modules.modal

import bot.maiden.CommandContext
import bot.maiden.Module
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.GenericMessageEvent
import java.util.concurrent.ConcurrentHashMap

object Modals : Module {
    private val scope = CoroutineScope(Dispatchers.Default)

    private data class ModalData(
        val dataChannel: SendChannel<GenericEvent>,
        val context: CommandContext,
        val modal: StepModal
    )

    private val activeModals = ConcurrentHashMap<Long, ModalData>()

    fun beginModal(channel: MessageChannel, context: CommandContext, modal: StepModal): Deferred<Unit> {
        // TODO error: require requester?
        context.requester!!

        if (modal.steps.isEmpty()) return CompletableDeferred(Unit)

        val dataChannel = Channel<GenericEvent>()
        val data = ModalData(
            dataChannel,
            context,
            modal
        )

        if (activeModals.putIfAbsent(channel.idLong, data) != null) {
            // There is already an active modal in this channel
            dataChannel.close()

            return CompletableDeferred<Unit>().apply {
                completeExceptionally(IllegalStateException("There is already an active modal for channel ${channel.idLong}"))
            }
        }

        return scope.async {
            try {
                modal.start(context, dataChannel)
            } catch (e: CancellationException) {
                // TODO ignore
            } catch (e: Exception) {
                throw e
            } finally {
                activeModals.remove(channel.idLong)
            }
        }
    }

    override suspend fun onEvent(event: GenericEvent) {
        // TODO
        // Only send these event types for now
        val channel = when (event) {
            is GenericMessageEvent -> event.channel
            is ButtonClickEvent -> event.channel
            else -> return
        }

        val data = activeModals[channel.idLong] ?: return

        if (channel.idLong == data.context.channel.idLong) {
            data.dataChannel.send(event)
            return
        } else return
    }

    override suspend fun onMessage(message: Message): Boolean {
        val requesterId = activeModals[message.channel.idLong]?.context?.requester?.idLong

        // Ignore messages from current modal requester
        if (requesterId == message.author.idLong) return false
        return true
    }

    override fun close() {
        scope.cancel()
    }
}

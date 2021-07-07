package bot.maiden.modules.modal

import bot.maiden.CommandContext
import bot.maiden.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.GenericMessageEvent
import java.util.concurrent.ConcurrentHashMap

object Modals : Module {
    private val scope = CoroutineScope(Dispatchers.Default)

    private data class ModalData(
        val dataChannel: SendChannel<GenericMessageEvent>,
        val context: CommandContext,
        val modal: StepModal
    )

    private val activeModals = ConcurrentHashMap<Long, ModalData>()

    fun beginModal(channel: MessageChannel, context: CommandContext, modal: StepModal) {
        if (modal.steps.isEmpty()) return

        val dataChannel = Channel<GenericMessageEvent>()
        val data = ModalData(
            dataChannel,
            context,
            modal
        )

        if (activeModals.putIfAbsent(channel.idLong, data) != null) {
            // There is already an active modal in this channel
            dataChannel.close()
            return
        }

        scope.launch {
            try {
                modal.start(context, dataChannel)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                activeModals.remove(channel.idLong)
            }
        }
    }

    override suspend fun onEvent(event: GenericEvent): Boolean {
        if (event !is GenericMessageEvent) return true

        val data = activeModals[event.channel.idLong] ?: return true

        if (event.channel.idLong == data.context.channel.idLong) {
            data.dataChannel.send(event)
            return false
        } else return true
    }

    override fun close() {
        scope.cancel()
    }
}

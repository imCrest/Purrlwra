package cock.crest.purrfectsnap.lite.core.features.impl.messaging

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.delay
import cock.crest.purrfectsnap.lite.core.event.events.impl.ConversationUpdateEvent
import cock.crest.purrfectsnap.lite.core.event.events.impl.SendMessageWithContentEvent
import cock.crest.purrfectsnap.lite.core.features.Feature
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ConversationSoundEffects : Feature("Conversation Sound Effects") {
    private val seenIncomingMessageIds = LinkedHashSet<Long>()
    private val maxTrackedMessages = 512

    private data class BubbleSpec(
        val durationMs: Int,
        val startFreqHz: Double,
        val endFreqHz: Double,
        val overtoneFreqHz: Double,
        val amplitude: Double
    )

    private data class BubbleStep(
        val spec: BubbleSpec,
        val pauseAfterMs: Long = 0L
    )

    private val iMessageSendBubble = BubbleSpec(
        durationMs = 78,
        startFreqHz = 1160.0,
        endFreqHz = 690.0,
        overtoneFreqHz = 1820.0,
        amplitude = 0.50
    )

    private val iMessageReceiveBubble = BubbleSpec(
        durationMs = 92,
        startFreqHz = 1040.0,
        endFreqHz = 640.0,
        overtoneFreqHz = 1680.0,
        amplitude = 0.46
    )

    private val whatsappSendBubble = BubbleSpec(
        durationMs = 86,
        startFreqHz = 860.0,
        endFreqHz = 520.0,
        overtoneFreqHz = 1410.0,
        amplitude = 0.52
    )

    private val whatsappReceiveBubble = BubbleSpec(
        durationMs = 94,
        startFreqHz = 920.0,
        endFreqHz = 560.0,
        overtoneFreqHz = 1520.0,
        amplitude = 0.50
    )

    private val telegramSendPrimary = BubbleSpec(
        durationMs = 58,
        startFreqHz = 1110.0,
        endFreqHz = 820.0,
        overtoneFreqHz = 1710.0,
        amplitude = 0.42
    )

    private val telegramSendAccent = BubbleSpec(
        durationMs = 34,
        startFreqHz = 1360.0,
        endFreqHz = 980.0,
        overtoneFreqHz = 2060.0,
        amplitude = 0.22
    )

    private val telegramReceivePrimary = BubbleSpec(
        durationMs = 72,
        startFreqHz = 1080.0,
        endFreqHz = 780.0,
        overtoneFreqHz = 1680.0,
        amplitude = 0.44
    )

    private val telegramReceiveAccent = BubbleSpec(
        durationMs = 42,
        startFreqHz = 1280.0,
        endFreqHz = 940.0,
        overtoneFreqHz = 1940.0,
        amplitude = 0.18
    )

    private val subtleSendBubble = BubbleSpec(
        durationMs = 60,
        startFreqHz = 760.0,
        endFreqHz = 520.0,
        overtoneFreqHz = 1180.0,
        amplitude = 0.26
    )

    private val subtleReceiveBubble = BubbleSpec(
        durationMs = 66,
        startFreqHz = 800.0,
        endFreqHz = 560.0,
        overtoneFreqHz = 1260.0,
        amplitude = 0.24
    )

    private fun currentConversationId() = context.feature(Messaging::class).openedConversationUUID?.toString()

    private fun buildBubblePcm(spec: BubbleSpec, sampleRate: Int = 44_100): ByteArray {
        val sampleCount = (sampleRate * (spec.durationMs / 1000.0)).toInt().coerceAtLeast(1)
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val progress = i.toDouble() / sampleCount.toDouble()
            val envelope = exp(-4.8 * progress) * (1.0 - exp(-20.0 * progress))
            val freq = spec.startFreqHz + (spec.endFreqHz - spec.startFreqHz) * progress
            val t = i.toDouble() / sampleRate.toDouble()
            val fundamental = sin(2.0 * PI * freq * t)
            val overtone = 0.18 * sin(2.0 * PI * spec.overtoneFreqHz * t)
            val airyTail = 0.08 * sin(2.0 * PI * (freq * 0.48) * t)
            val warmth = 0.14 * sin(2.0 * PI * (freq * 0.24) * t)
            val sample = ((fundamental + overtone + airyTail + warmth) * envelope * spec.amplitude)
                .coerceIn(-1.0, 1.0)
            val shortValue = (sample * Short.MAX_VALUE).toInt().toShort()
            pcm[i * 2] = (shortValue.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((shortValue.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun playBubble(spec: BubbleSpec) {
        if (context.isMainActivityPaused) return
        context.executeAsync {
            val sampleRate = 44_100
            val pcm = buildBubblePcm(spec, sampleRate)
            val audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                pcm.size,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            runCatching {
                audioTrack.write(pcm, 0, pcm.size)
                audioTrack.play()
                delay(spec.durationMs.toLong() + 24L)
            }.also {
                runCatching {
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
        }
    }

    private fun playBubbleSequence(steps: List<BubbleStep>) {
        if (context.isMainActivityPaused) return
        context.executeAsync {
            steps.forEach { step ->
                val sampleRate = 44_100
                val pcm = buildBubblePcm(step.spec, sampleRate)
                val audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    pcm.size,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                runCatching {
                    audioTrack.write(pcm, 0, pcm.size)
                    audioTrack.play()
                    delay(step.spec.durationMs.toLong() + step.pauseAfterMs + 18L)
                }.also {
                    runCatching {
                        audioTrack.stop()
                        audioTrack.release()
                    }
                }
            }
        }
    }

    private fun playStyledSend() {
        when (context.config.messaging.conversationSoundEffectsStyle.get()) {
            "imessage" -> playBubble(iMessageSendBubble)
            "whatsapp" -> playBubble(whatsappSendBubble)
            "telegram" -> playBubbleSequence(
                listOf(
                    BubbleStep(telegramSendPrimary, pauseAfterMs = 16L),
                    BubbleStep(telegramSendAccent)
                )
            )
            else -> playBubble(subtleSendBubble)
        }
    }

    private fun playStyledReceive() {
        when (context.config.messaging.conversationSoundEffectsStyle.get()) {
            "imessage" -> playBubble(iMessageReceiveBubble)
            "whatsapp" -> playBubbleSequence(
                listOf(
                    BubbleStep(whatsappReceiveBubble, pauseAfterMs = 12L),
                    BubbleStep(
                        whatsappReceiveBubble.copy(
                            durationMs = 42,
                            startFreqHz = 1210.0,
                            endFreqHz = 860.0,
                            overtoneFreqHz = 1980.0,
                            amplitude = 0.20
                        )
                    )
                )
            )
            "telegram" -> playBubbleSequence(
                listOf(
                    BubbleStep(telegramReceivePrimary, pauseAfterMs = 14L),
                    BubbleStep(telegramReceiveAccent)
                )
            )
            else -> playBubble(subtleReceiveBubble)
        }
    }

    private fun markSeen(messageId: Long): Boolean {
        synchronized(seenIncomingMessageIds) {
            val added = seenIncomingMessageIds.add(messageId)
            while (seenIncomingMessageIds.size > maxTrackedMessages) {
                seenIncomingMessageIds.remove(seenIncomingMessageIds.first())
            }
            return added
        }
    }

    override fun init() {
        if (context.config.messaging.conversationSoundEffectsStyle.get() == "disabled") return

        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            val activeConversationId = currentConversationId() ?: return@subscribe
            if (event.destinations.conversations?.none { it.toString() == activeConversationId } != false) return@subscribe

            event.addCallbackResult("onSuccess") {
                playStyledSend()
            }
        }

        context.event.subscribe(ConversationUpdateEvent::class) { event ->
            val activeConversationId = currentConversationId() ?: return@subscribe
            if (event.conversationId != activeConversationId) return@subscribe

            val myUserId = context.database.myUserId ?: return@subscribe

            event.messages
                .asSequence()
                .filter { it.senderId?.toString() != myUserId }
                .mapNotNull { it.messageDescriptor?.messageId }
                .filter { markSeen(it) }
                .firstOrNull()
                ?.let {
                    playStyledReceive()
                }
        }
    }
}

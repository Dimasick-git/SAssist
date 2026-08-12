package dev.ryazha.sassist.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaRef(
    val id: String = "",
    val kind: String, // "image" | "video" | "audio" | "file"
    val mime: String,
    val name: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null // voice/video length
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val channel: String = "",
    val userId: String = "",
    val username: String = "",
    val handle: String = "",
    val premium: Boolean = false,
    val color: String = "5865F2",
    val text: String = "",
    val ts: Long = 0L,
    val media: MediaRef? = null,
    val replyTo: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val readBy: List<String> = emptyList(),
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
    /** True only for this device's own attachment when its original URI can be re-uploaded. */
    val hasLocalMediaBackup: Boolean = false
)

enum class ConnState { Disconnected, Connecting, Connected, Error }

enum class Stage { Welcome, EnterIdentifier, EnterCode, Chats, Chat, Call, Scripts, Profile, UserProfile }

enum class AuthMethod { Phone, Email }

enum class CallKind { Audio, Video }
enum class CallPhase { Incoming, Outgoing, Connecting, Active }

data class CallUi(
    val channel: String,
    val kind: CallKind,
    val phase: CallPhase,
    val peerName: String = "",
    val signals: List<String> = emptyList(),
    val returnStage: Stage = Stage.Chat
)

/** Metadata for rendering channels as pretty chat rows. */
data class ChannelMeta(val id: String, val title: String, val subtitle: String, val emoji: String)

val CHANNEL_META = mapOf(
    "general" to ChannelMeta("general", "General", "Trep & off-topic", "💬"),
    "code-help" to ChannelMeta("code-help", "Code Help", "Stuck? Drop a snippet", "🧑‍💻"),
    "showtime" to ChannelMeta("showtime", "Showtime", "Show what you built", "🚀")
)

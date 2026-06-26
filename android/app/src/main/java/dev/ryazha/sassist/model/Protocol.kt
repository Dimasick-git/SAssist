package dev.ryazha.sassist.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    val channel: String = "",
    val username: String = "",
    val text: String = "",
    val ts: Long = 0L
)

enum class ConnState { Disconnected, Connecting, Connected, Error }

enum class Stage { Welcome, EnterIdentifier, EnterCode, Chats, Chat, Scripts }

enum class AuthMethod { Phone, Email }

/** Metadata for rendering channels as pretty chat rows. */
data class ChannelMeta(val id: String, val title: String, val subtitle: String, val emoji: String)

val CHANNEL_META = mapOf(
    "general" to ChannelMeta("general", "General", "Trep & off-topic", "💬"),
    "code-help" to ChannelMeta("code-help", "Code Help", "Stuck? Drop a snippet", "🧑‍💻"),
    "showtime" to ChannelMeta("showtime", "Showtime", "Show what you built", "🚀")
)

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

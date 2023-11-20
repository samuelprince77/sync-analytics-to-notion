package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.Serializable

@Serializable
internal data class RemoteArchiveEventRequest(
    val archived: Boolean
)
package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RemoteFetchPagesResponse(
    val results: List<RemotePageResponse>,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("next_cursor")
    val nextCursor: String?,
)

@Serializable
internal data class RemotePageResponse(
    val id: String,
    val properties: RemotePropertiesBody,
)
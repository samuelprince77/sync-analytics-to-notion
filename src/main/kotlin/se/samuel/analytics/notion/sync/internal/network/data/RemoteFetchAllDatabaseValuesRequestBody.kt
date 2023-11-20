package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RemoteFetchAllDatabaseValuesRequestBody(
    @SerialName("start_cursor")
    val startCursor: String?
)
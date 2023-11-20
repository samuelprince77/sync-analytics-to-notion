package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal typealias RemotePropertiesBody = Map<String, JsonElement>

@Serializable
internal data class RemoteInsertPageRequest(
    val parent: RemoteParent,
    val properties: RemotePropertiesBody,
)

@Serializable
internal data class RemoteParent(
    @SerialName("database_id")
    val databaseId: String,
)

@Serializable
internal data class RemoteInsertEventProperty(
    val title: List<NotionTitleText>,
)

@Serializable
internal data class RemoteInsertParametersProperty(
    @SerialName("rich_text")
    val richText: List<RichTextData>
)
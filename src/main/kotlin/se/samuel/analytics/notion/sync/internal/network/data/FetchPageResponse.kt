package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FetchPageResponse(
    val id: String,
    val properties: FetchPagePropertyResponse,
)

@Serializable
internal data class FetchPagePropertyResponse(
    val event: EventPropertyResponse,
    val parameters: ParametersValuePropertyResponse,
)

@Serializable
internal data class EventPropertyResponse(
    val id: String,
    val title: List<NotionTitleText>,
)

@Serializable
internal data class ParametersValuePropertyResponse(
    val id: String,
    @SerialName("rich_text")
    val richText: List<RichTextData>
)
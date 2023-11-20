package se.samuel.analytics.notion.sync.internal.network.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NotionTitleText(
    val text: RichTextContent,
)

@Serializable
internal data class RichTextData(
    val text: RichTextContent,
    val annotations: RichTextAnnotations,
)

@Serializable
internal data class RichTextAnnotations(
    val bold: Boolean? = false,
    val italic: Boolean? = false,
    @SerialName("strikethrough")
    val strikeThrough: Boolean? = false,
    val underline: Boolean? = false,
    val code: Boolean? = false,
    val color: String? = "default",
)

@Serializable
internal data class RichTextContent(
    val content: String,
)
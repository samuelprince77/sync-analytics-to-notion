package se.samuel.analytics.notion.sync.internal.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.gradle.api.logging.Logger
import se.samuel.analytics.notion.sync.data.AnalyticsEventInfo
import se.samuel.analytics.notion.sync.internal.network.data.FetchPagePropertyResponse
import se.samuel.analytics.notion.sync.internal.network.data.FetchPageResponse
import se.samuel.analytics.notion.sync.internal.network.data.NotionTitleText
import se.samuel.analytics.notion.sync.internal.network.data.RemoteArchiveEventRequest
import se.samuel.analytics.notion.sync.internal.network.data.RemoteFetchAllDatabaseValuesRequestBody
import se.samuel.analytics.notion.sync.internal.network.data.RemoteFetchPagesResponse
import se.samuel.analytics.notion.sync.internal.network.data.RemoteInsertEventProperty
import se.samuel.analytics.notion.sync.internal.network.data.RemoteInsertPageRequest
import se.samuel.analytics.notion.sync.internal.network.data.RemoteInsertParametersProperty
import se.samuel.analytics.notion.sync.internal.network.data.RemoteParent
import se.samuel.analytics.notion.sync.internal.network.data.RemotePatchPageRequest
import se.samuel.analytics.notion.sync.internal.network.data.RemotePropertiesBody
import se.samuel.analytics.notion.sync.internal.network.data.RichTextAnnotations
import se.samuel.analytics.notion.sync.internal.network.data.RichTextContent
import se.samuel.analytics.notion.sync.internal.network.data.RichTextData

private const val AUTHORIZATION_HEADER = "Authorization"
private const val NOTION_VERSION_HEADER = "Notion-Version"
private const val NOTION_VERSION_VALUE = "2022-06-28"

internal class NotionApiService(
    private val logger: Logger,
    private val notionAuthKey: String,
    private val notionDatabaseId: String,
    private val notionEventNameColumnName: String,
    private val notionParametersColumnName: String,
) {

    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(JsonFormat)
        }

        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    this@NotionApiService.logger.debug("httpClient {}", message)
                }
            }
            level = LogLevel.ALL
        }

        engine {
            requestTimeout = 30_000
        }
    }

    internal fun uploadAnalyticsEvents(
        analyticsEventInfo: List<AnalyticsEventInfo>,
    ) {

        val analyticsEventsMap: Map<String, AnalyticsEventInfo> = analyticsEventInfo.associateBy {
            it.eventName
        }.toSortedMap()

        runBlocking {
            val remoteEvents = fetchAllDatabaseValues()
            val (eventsAlreadyExisting, eventsToArchive) = remoteEvents.partition { remotePageResponse ->
                remotePageResponse.properties.event.title.any { notionTitleText ->
                    analyticsEventsMap.containsKey(notionTitleText.text.content)
                }
            }

            // These will be patched
            val eventsAlreadyExistingKeys = eventsAlreadyExisting.mapNotNull { remotePageResponse ->
                val eventName = remotePageResponse.properties.event.title.firstOrNull()?.text?.content
                eventName
            }.toSet()

            logger.debug("need to patch {}", eventsAlreadyExistingKeys)

            // These have to be added
            val newEvents = analyticsEventsMap.toMutableMap().apply {
                keys.removeAll(eventsAlreadyExistingKeys)
            }

            logger.debug("new events {}", newEvents)

            eventsAlreadyExisting.mapNotNull { remotePageResponse ->
                val analyticsEvent =
                    remotePageResponse.properties.event.title.firstOrNull()?.text?.content?.let { eventName ->
                        analyticsEventsMap[eventName]
                    }

                if (analyticsEvent != null) {
                    async {
                        updateEvent(
                            event = analyticsEvent,
                            pageId = remotePageResponse.id,
                        )
                    }
                } else {
                    null
                }
            }.plus(
                newEvents.values.map { analyticsEventsInfo ->
                    async {
                        insertPage(event = analyticsEventsInfo)
                    }
                }
            ).awaitAll()

            logger.debug(
                "need to archive {}",
                eventsToArchive.map { remotePageResponse ->
                    remotePageResponse.properties.event.title.map { it.text.content }
                }
            )

            // These will be archived
            eventsToArchive.map { remotePageResponse ->
                async {
                    archiveEvent(
                        eventName = remotePageResponse.properties.event.title.map { titleText ->
                            titleText.text.content
                        }.toString(),
                        pageId = remotePageResponse.id,
                    )
                }
            }.awaitAll()

            println("Successfully completed notion analytics sync")
        }
    }

    private suspend fun fetchAllDatabaseValues(
        remotePagesFound: List<FetchPageResponse> = emptyList(),
        startCursor: String? = null,
    ): List<FetchPageResponse> {
        val url = String.format("https://api.notion.com/v1/databases/%s/query", notionDatabaseId)
        val request = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header(AUTHORIZATION_HEADER, notionAuthKey)
            header(NOTION_VERSION_HEADER, NOTION_VERSION_VALUE)

            setBody(
                RemoteFetchAllDatabaseValuesRequestBody(startCursor = startCursor,)
            )
        }

        val foundRemotePages = remotePagesFound.toMutableList()

        if (request.status.isSuccess()) {
            val parsedResponse = request.body<RemoteFetchPagesResponse>()

            foundRemotePages += parsedResponse.results.map {
                FetchPageResponse(
                    id = it.id,
                    properties = FetchPagePropertyResponse(
                        event = JsonFormat.decodeFromJsonElement(it.properties.getValue(notionEventNameColumnName)),
                        parameters = JsonFormat.decodeFromJsonElement(
                            it.properties.getValue(
                                notionParametersColumnName
                            )
                        ),
                    )
                )
            }

            return if (parsedResponse.nextCursor != null) {
                fetchAllDatabaseValues(
                    foundRemotePages,
                    parsedResponse.nextCursor
                )
            } else {
                foundRemotePages
            }
        } else {
            throw IllegalStateException("Failed to fetch properties from notion, response is: ${request.bodyAsText()}")
        }
    }

    private suspend fun insertPage(
        event: AnalyticsEventInfo,
    ) {
        val url = "https://api.notion.com/v1/pages"
        val request = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header(AUTHORIZATION_HEADER, notionAuthKey)
            header(NOTION_VERSION_HEADER, NOTION_VERSION_VALUE)

            setBody(
                RemoteInsertPageRequest(
                    parent = RemoteParent(databaseId = notionDatabaseId),
                    properties = event.toRemotePropertiesBody(
                        eventColumnName = notionEventNameColumnName,
                        parametersColumnName = notionParametersColumnName
                    )
                )
            )
        }

        if (!request.status.isSuccess()) {
            throw IllegalStateException("Failed to insert event $event because ${request.bodyAsText()}")
        }
    }

    private suspend fun updateEvent(
        event: AnalyticsEventInfo,
        pageId: String,
    ) {
        val url = String.format("https://api.notion.com/v1/pages/%s", pageId)
        val request = httpClient.patch(url) {
            contentType(ContentType.Application.Json)
            header(AUTHORIZATION_HEADER, notionAuthKey)
            header(NOTION_VERSION_HEADER, NOTION_VERSION_VALUE)

            setBody(
                RemotePatchPageRequest(
                    properties = event.toRemotePropertiesBody(
                        eventColumnName = notionEventNameColumnName,
                        parametersColumnName = notionParametersColumnName
                    )
                )
            )
        }

        if (!request.status.isSuccess()) {
            throw IllegalStateException("Unable to update event: $event because ${request.bodyAsText()}")
        }

    }

    private suspend fun archiveEvent(
        eventName: String,
        pageId: String,
    ) {
        val url = String.format("https://api.notion.com/v1/pages/%s", pageId)
        val request = httpClient.patch(url) {
            contentType(ContentType.Application.Json)
            header(AUTHORIZATION_HEADER, notionAuthKey)
            header(NOTION_VERSION_HEADER, NOTION_VERSION_VALUE)

            setBody(RemoteArchiveEventRequest(archived = true))
        }

        if (!request.status.isSuccess()) {
            throw IllegalStateException("Unable to archive event $eventName because ${request.bodyAsText()}")
        }
    }

}

internal fun AnalyticsEventInfo.toRemotePropertiesBody(
    eventColumnName: String,
    parametersColumnName: String,
): RemotePropertiesBody =
    mapOf(
        eventColumnName to JsonFormat.encodeToJsonElement(
            RemoteInsertEventProperty(
                title = listOf(
                    NotionTitleText(
                        text = RichTextContent(
                            content = eventName
                        )
                    )
                )
            )
        ),
        parametersColumnName to JsonFormat.encodeToJsonElement(
            RemoteInsertParametersProperty(
                richText = listOf(
                    RichTextData(
                        text = RichTextContent(
                            content = parameters.joinToString(separator = "\n") { parameter ->
                                String.format("- %s", parameter)
                            },
                        ),
                        annotations = RichTextAnnotations()
                    )
                )
            )
        )
    )
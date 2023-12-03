package se.samuel.analytics.notion.sync.internal.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
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
import java.time.Duration

private const val AUTHORIZATION_HEADER = "Authorization"
private const val NOTION_VERSION_HEADER = "Notion-Version"
private const val NOTION_VERSION_VALUE = "2022-06-28"

private val okhttpTimeoutDuration = Duration.ofMinutes(3)

internal class NotionApiService(
    private val logger: Logger,
    private val notionAuthKey: String,
    private val notionDatabaseId: String,
    private val notionEventNameColumnName: String,
    private val notionParametersColumnName: String,
) {

    private val httpClient = OkHttpClient
        .Builder()
        .addInterceptor { chain ->
            with(chain) {
                proceed(
                    request()
                        .newBuilder()
                        .addHeader(AUTHORIZATION_HEADER, notionAuthKey)
                        .addHeader(NOTION_VERSION_HEADER, NOTION_VERSION_VALUE)
                        .build()
                )
            }
        }
        .callTimeout(okhttpTimeoutDuration)
        .readTimeout(okhttpTimeoutDuration)
        .writeTimeout(okhttpTimeoutDuration)
        .connectTimeout(okhttpTimeoutDuration)
        .build()

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

        val request: Request = Request
            .Builder()
            .url(url)
            .post(RemoteFetchAllDatabaseValuesRequestBody(startCursor = startCursor).toApplicationJsonRequestBody())
            .build()

        val response = httpClient.newCall(request).awaitResponse()

        val foundRemotePages = remotePagesFound.toMutableList()

        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            val parsedResponse = JsonFormat.decodeFromString<RemoteFetchPagesResponse>(body)

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
            throw IllegalStateException("Failed to fetch properties from notion, response is: $body")
        }
    }

    private suspend fun insertPage(
        event: AnalyticsEventInfo,
    ) {
        val url = "https://api.notion.com/v1/pages"
        val request: Request = Request
            .Builder()
            .url(url)
            .post(
                RemoteInsertPageRequest(
                    parent = RemoteParent(databaseId = notionDatabaseId),
                    properties = event.toRemotePropertiesBody(
                        eventColumnName = notionEventNameColumnName,
                        parametersColumnName = notionParametersColumnName
                    )
                ).toApplicationJsonRequestBody()
            )
            .build()

        val response = httpClient.newCall(request).awaitResponse()

        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to insert event $event because ${response.body?.string()}")
        }
    }

    private suspend fun updateEvent(
        event: AnalyticsEventInfo,
        pageId: String,
    ) {
        val url = String.format("https://api.notion.com/v1/pages/%s", pageId)
        val request: Request = Request
            .Builder()
            .url(url)
            .patch(
                RemotePatchPageRequest(
                    properties = event.toRemotePropertiesBody(
                        eventColumnName = notionEventNameColumnName,
                        parametersColumnName = notionParametersColumnName
                    )
                ).toApplicationJsonRequestBody()
            )
            .build()

        val response = httpClient.newCall(request).awaitResponse()

        if (!response.isSuccessful) {
            throw IllegalStateException("Unable to update event: $event because ${response.body?.string()}")
        }
    }

    private suspend fun archiveEvent(
        eventName: String,
        pageId: String,
    ) {
        val url = String.format("https://api.notion.com/v1/pages/%s", pageId)
        val request: Request = Request
            .Builder()
            .url(url)
            .patch(RemoteArchiveEventRequest(archived = true).toApplicationJsonRequestBody())
            .build()

        val response = httpClient.newCall(request).awaitResponse()

        if (!response.isSuccessful) {
            throw IllegalStateException("Unable to archive event $eventName because ${response.body?.string()}")
        }
    }

}

private fun AnalyticsEventInfo.toRemotePropertiesBody(
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
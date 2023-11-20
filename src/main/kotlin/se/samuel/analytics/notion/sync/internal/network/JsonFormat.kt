package se.samuel.analytics.notion.sync.internal.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal val JsonFormat: Json = Json {
    prettyPrint = true
    coerceInputValues = true
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}
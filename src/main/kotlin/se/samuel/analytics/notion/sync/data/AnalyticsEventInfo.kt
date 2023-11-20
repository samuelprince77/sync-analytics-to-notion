package se.samuel.analytics.notion.sync.data

data class AnalyticsEventInfo(
    val eventName: String,
    val parameters: List<String>,
)
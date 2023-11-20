package se.samuel.analytics.notion.sync

import org.gradle.api.provider.Property
import se.samuel.analytics.notion.sync.parser.AnalyticsEventInfoParser

abstract class SyncAnalyticsToNotionExtension internal constructor() {

    internal abstract val notionAuthKey: Property<String>
    internal abstract val notionDatabaseId: Property<String>
    internal abstract val analyticsEventInfoParser: Property<AnalyticsEventInfoParser>

    internal abstract val notionEventNameColumnName: Property<String>
    internal abstract val notionParametersColumnName: Property<String>

    fun setNotionAuthKey(authKey: String) {
        notionAuthKey.set(authKey)
    }

    fun setNotionDatabaseId(databaseId: String) {
        notionDatabaseId.set(databaseId)
    }

    fun setAnalyticsEventInfoParser(parser: AnalyticsEventInfoParser) {
        analyticsEventInfoParser.set(parser)
    }

    fun setNotionEventNameColumnName(name: String) {
        notionEventNameColumnName.set(name)
    }

    fun setNotionParametersColumnName(name: String) {
        notionParametersColumnName.set(name)
    }
}
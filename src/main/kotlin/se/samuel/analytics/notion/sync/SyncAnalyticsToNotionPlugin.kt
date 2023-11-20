package se.samuel.analytics.notion.sync

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import se.samuel.analytics.notion.sync.tasks.SyncAnalyticsToNotionTask

open class SyncAnalyticsToNotionPlugin : Plugin<Project> {

    override fun apply(target: Project) {

        val rootExtension = target.extensions.create(
            "syncAnalyticsToNotion",
            SyncAnalyticsToNotionExtension::class.java
        )

        target.afterEvaluate {
            when {
                rootExtension.notionAuthKey.orNull == null -> "Notion authentication key is missing"
                rootExtension.notionDatabaseId.orNull == null -> "Notion databaseId is missing"
                rootExtension.notionEventNameColumnName.orNull == null -> "Notion event name column name is missing"
                rootExtension.notionParametersColumnName.orNull == null -> "Notion parameters column name is missing"
                else -> null
            }?.let { errorMessage ->
                target.logger.error(errorMessage)
                throw GradleException(errorMessage)
            }
        }

        target
            .tasks
            .register("syncAnalyticsToNotion", SyncAnalyticsToNotionTask::class.java)
            .configure {
                with(it) {
                    analyticsEventInfoParser.set(rootExtension.analyticsEventInfoParser)
                    notionAuthKey.set(rootExtension.notionAuthKey)
                    notionDatabaseId.set(rootExtension.notionDatabaseId)
                    notionEventNameColumnName.set(rootExtension.notionEventNameColumnName)
                    notionParametersColumnName.set(rootExtension.notionParametersColumnName)

                    kotlinFilesFromMainSourceSet.setFrom(
                        target
                            .extensions
                            .getByType(KotlinProjectExtension::class.java)
                            .sourceSets
                            .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                            .kotlin
                    )
                }
            }
    }

}


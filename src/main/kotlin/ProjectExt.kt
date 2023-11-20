import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import se.samuel.analytics.notion.sync.SyncAnalyticsToNotionExtension

fun Project.syncAnalyticsToNotion(block: SyncAnalyticsToNotionExtension.() -> Unit) {
    (this as ExtensionAware).extensions.configure(SyncAnalyticsToNotionExtension::class.java) {
        block(it)
    }
}
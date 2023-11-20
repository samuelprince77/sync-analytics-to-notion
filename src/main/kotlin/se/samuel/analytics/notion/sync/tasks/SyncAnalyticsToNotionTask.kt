package se.samuel.analytics.notion.sync.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import se.samuel.analytics.notion.sync.internal.network.NotionApiService
import se.samuel.analytics.notion.sync.internal.parser.DefaultAnalyticsEventInfoParser
import se.samuel.analytics.notion.sync.parser.AnalyticsEventInfoParser

abstract class SyncAnalyticsToNotionTask : DefaultTask() {

    @get:InputFiles
    abstract val kotlinFilesFromMainSourceSet: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val analyticsEventInfoParser: Property<AnalyticsEventInfoParser>

    @get:Input
    abstract val notionAuthKey: Property<String>

    @get:Input
    abstract val notionDatabaseId: Property<String>

    @get:Input
    abstract val notionEventNameColumnName: Property<String>

    @get:Input
    abstract val notionParametersColumnName: Property<String>

    init {
        group = "syncanalytics"
        description = "Syncs analytics with Notion"
    }

    @TaskAction
    fun syncAnalyticsToNotion() {
        val kotlinCoreEnvironment = KotlinCoreEnvironment
            .createForProduction(
                Disposer.newDisposable(),
                CompilerConfiguration().apply {
                    put(
                        CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                        PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
                    )
                },
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

        val psiManager = PsiManager.getInstance(kotlinCoreEnvironment.project)
        val allKtFiles = kotlinFilesFromMainSourceSet.mapNotNull { file ->
            psiManager.findFile(CoreLocalVirtualFile(CoreLocalFileSystem(), file)) as? KtFile
        }

        val parser = analyticsEventInfoParser.orNull ?: DefaultAnalyticsEventInfoParser
        val analyticsEventInfo = parser.parseKtFiles(ktFiles = allKtFiles)

        val apiService = NotionApiService(
            logger = logger,
            notionAuthKey = notionAuthKey.get(),
            notionDatabaseId = notionDatabaseId.get(),
            notionEventNameColumnName = notionEventNameColumnName.get(),
            notionParametersColumnName = notionParametersColumnName.get(),
        )

        apiService.uploadAnalyticsEvents(analyticsEventInfo = analyticsEventInfo)
    }
}
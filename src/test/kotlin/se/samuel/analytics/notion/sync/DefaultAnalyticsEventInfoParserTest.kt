package se.samuel.analytics.notion.sync

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Test
import org.jetbrains.kotlin.psi.KtPsiFactory
import se.samuel.analytics.notion.sync.data.AnalyticsEventInfo
import se.samuel.analytics.notion.sync.internal.parser.DefaultAnalyticsEventInfoParser

class DefaultAnalyticsEventInfoParserTest {

    @Test
    fun testParsingASingleKotlinFile() {

        val environment = KotlinCoreEnvironment
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

        val sampleTestKotlinCode = """
            private const val SOME_PARAM_NAME = "example_reference"

            fun example(
                example1: String,
                example2: Int,
                example3: Double,
                example4: Long,
            ) {
                sampleEventLogger(
                    eventName = "your_event_name", 
                    Bundle().apply {
                        putString(SOME_PARAM_NAME, example1)
                        putString("example_string", example1)
                        putInt("example_int", example2)
                        putDouble("example_double", example3)
                        putLong("example_long", example4)
                    }
                )
            }
            
            fun someOtherExample(
                example: String
            ) {
                sampleEventLogger(
                    eventName = "your_other_event_name",
                    Bundle().apply {
                        putString("another_param_name", example)
                    }
                )
            }
            
            fun exampleWithNoParameters() {
                sampleEventLogger(
                    eventName = "your_no_params_event_name",
                    Bundle()
                )
            }
            
            @AnalyticsEventLogger
            private fun sampleEventLogger(eventName: String, params: Bundle) {
                
            }
        """.trimIndent()

        val testKtFile = KtPsiFactory(environment.project).createFile(sampleTestKotlinCode)

        val parsedAnalyticsEventInfo = DefaultAnalyticsEventInfoParser.parseKtFiles(listOf(testKtFile))

        val expectedAnalyticsEventInfo = listOf(
            AnalyticsEventInfo(
                eventName = "your_event_name",
                parameters = listOf(
                    "example_reference",
                    "example_string",
                    "example_int",
                    "example_double",
                    "example_long"
                )
            ),
            AnalyticsEventInfo(
                eventName = "your_other_event_name",
                parameters = listOf("another_param_name")
            ),
            AnalyticsEventInfo(
                eventName = "your_no_params_event_name",
                parameters = listOf()
            )
        )

        assert(parsedAnalyticsEventInfo == expectedAnalyticsEventInfo)
    }

}
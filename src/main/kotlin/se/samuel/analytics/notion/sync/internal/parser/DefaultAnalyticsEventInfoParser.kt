package se.samuel.analytics.notion.sync.internal.parser

import org.gradle.api.GradleException
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import se.samuel.analytics.notion.sync.AnalyticsEventLogger
import se.samuel.analytics.notion.sync.data.AnalyticsEventInfo
import se.samuel.analytics.notion.sync.parser.AnalyticsEventInfoParser

private val analyticsLoggerAnnotationName = AnalyticsEventLogger::class.simpleName
private val identifiersOfInterest = listOf("putString", "putInt", "putDouble", "putLong")

internal object DefaultAnalyticsEventInfoParser : AnalyticsEventInfoParser {

    override fun parseKtFiles(ktFiles: List<KtFile>): List<AnalyticsEventInfo> {
        var eventLoggerFunctionName: String? = null

        val propertiesMap = ktFiles.fold(mapOf<String, String>()) { metadata, file ->
            val parsedMetaData = file.parseMetadata()
            if (parsedMetaData.eventLoggerFunctionName != null) {
                eventLoggerFunctionName = parsedMetaData.eventLoggerFunctionName
            }
            metadata + parsedMetaData.propertiesMap
        }

        eventLoggerFunctionName?.let {functionName ->
            return ktFiles.flatMap { file ->
                file.parseAnalyticsEventCalls(
                    eventLoggerName = functionName,
                    mapOfTopLevelProperties = propertiesMap
                )
            }
        } ?: throw GradleException(
            "No function annotated with @$analyticsLoggerAnnotationName was found. You need to annotate the function that logs the event with @$analyticsLoggerAnnotationName"
        )
    }
}

/**
 * Get all top level constants as well as the name of the function that does the actual logging to the analytics provider
 * Getting top level constants here because it doesn't seem like you can resolve the usages of references during the
 * single pass parse
 */
private fun KtFile.parseMetadata(): PreDocumentGenerationMetadata {
    var eventLoggerFunctionName: String? = null
    val propertyNameAndMetadataMap = mutableMapOf<String, String>()

    this.accept(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {

            if (element is KtProperty && element.getChildrenOfType<KtStringTemplateExpression>().isNotEmpty()) {
                val stringTemplateExpression = element.getChildOfType<KtStringTemplateExpression>()
                val propertyName = element.name
                val value = stringTemplateExpression?.text?.replace("\"", "")

                if (propertyName != null && value != null) {
                    propertyNameAndMetadataMap[propertyName] = value
                }
            }

            if (element is KtNamedFunction &&
                element.annotationEntries.any { it.shortName?.asString() == analyticsLoggerAnnotationName }
            ) {
                eventLoggerFunctionName = element.name
            }

            element.acceptChildren(this)
        }
    })

    return PreDocumentGenerationMetadata(
        eventLoggerFunctionName = eventLoggerFunctionName,
        propertiesMap = propertyNameAndMetadataMap,
    )
}

private fun KtFile.parseAnalyticsEventCalls(
    eventLoggerName: String,
    mapOfTopLevelProperties: Map<String, String>
): List<AnalyticsEventInfo> {
    val analyticsEventInfo = mutableListOf<AnalyticsEventInfo>()

    accept(object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {

            if (element is KtCallExpression && element.referenceExpression() is KtNameReferenceExpression) {
                val name = (element.referenceExpression() as KtNameReferenceExpression).getReferencedName()

                if (name == eventLoggerName) {
                    val functionArguments = element.valueArgumentList?.arguments

                    // Assumes the first argument is the event name and it must be a string representation
                    val eventName = functionArguments
                        ?.firstOrNull()
                        ?.getChildrenOfType<KtStringTemplateExpression>()
                        ?.firstOrNull()
                        ?.text?.replace("\"", "")

                    if (eventName != null) {
                        val analyticsParameters = mutableListOf<String>()

                        functionArguments.forEach { functionArgument ->
                            functionArgument.accept(object : PsiElementVisitor() {
                                override fun visitElement(element: PsiElement) {

                                    if (element is KtCallExpression) {
                                        val childrenAreOfInterest = element
                                            .getChildrenOfType<KtNameReferenceExpression>()
                                            .mapNotNull { ktNameReferenceExpression ->
                                                ktNameReferenceExpression.getIdentifier()?.text
                                            }
                                            .filter { identifier -> identifier in identifiersOfInterest }

                                        if (childrenAreOfInterest.isNotEmpty()) {
                                            element
                                                .valueArgumentList
                                                ?.arguments
                                                ?.firstOrNull()
                                                ?.let { ktValueArgument ->
                                                    // Looking for a reference to a property
                                                    val nameReferenceExpressionAsText = ktValueArgument
                                                        .getChildOfType<KtNameReferenceExpression>()
                                                        ?.text

                                                    val argumentFormatted = ktValueArgument
                                                        .text
                                                        .replace("\"", "")

                                                    val parameter = if (nameReferenceExpressionAsText != null) {
                                                        mapOfTopLevelProperties.getOrDefault(
                                                            nameReferenceExpressionAsText,
                                                            argumentFormatted
                                                        )
                                                    } else {
                                                        argumentFormatted
                                                    }

                                                    analyticsParameters.add(parameter)
                                                }
                                        }
                                    }
                                    element.acceptChildren(this)
                                }
                            })
                        }

                        analyticsEventInfo.add(
                            AnalyticsEventInfo(eventName = eventName, parameters = analyticsParameters)
                        )
                    }
                }
            }

            element.acceptChildren(this)
        }
    })

    return analyticsEventInfo
}

private data class PreDocumentGenerationMetadata(
    val eventLoggerFunctionName: String?,
    val propertiesMap: Map<String, String>,
)
package se.samuel.analytics.notion.sync.parser

import org.jetbrains.kotlin.psi.KtFile
import se.samuel.analytics.notion.sync.data.AnalyticsEventInfo

interface AnalyticsEventInfoParser {
    fun parseKtFiles(ktFiles: List<KtFile>): List<AnalyticsEventInfo>
}
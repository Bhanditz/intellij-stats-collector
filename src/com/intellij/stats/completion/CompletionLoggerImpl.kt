package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.stats.completion.events.*
import java.util.*

class CompletionFileLoggerProvider(private val logFileManager: LogFileManager) : CompletionLoggerProvider() {
    override fun dispose() {
        logFileManager.dispose()
    }

    private fun String.shortedUUID(): String {
        val start = this.lastIndexOf('-')
        if (start > 0 && start + 1 < this.length) {
            return this.substring(start + 1)
        }
        return this
    }

    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionFileLogger(installationUID.shortedUUID(), completionUID.shortedUUID(), logFileManager)
    }
}


class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val logFileManager: LogFileManager) : CompletionLogger() {

    val elementToId = mutableMapOf<String, Int>()

    private fun LookupElement.toIdString(): String {
        val p = LookupElementPresentation()
        this.renderElement(p)
        return "${p.itemText} ${p.tailText} ${p.typeText}"
    }

    private fun registerElement(item: LookupElement): Int {
        val itemString = item.toIdString()
        val newId = elementToId.size
        elementToId[itemString] = newId
        return newId
    }

    private fun getElementId(item: LookupElement): Int? {
        val itemString = item.toIdString()
        return elementToId[itemString]
    }

    private fun logEvent(event: LogEvent) {
        val line = LogEventSerializer.toString(event)
        logFileManager.println(line)
    }

    private fun getRecentlyAddedLookupItems(lookup: LookupImpl): List<LookupEntryInfo> {
        val newElements = mutableListOf<LookupElement>()
        lookup.items.forEach {
            if (getElementId(it) == null) {
                registerElement(it)
                newElements.add(it)
            }
        }

        val relevanceObjects = lookup.getRelevanceObjects(newElements, false)
        val newInfos = newElements.map {
            val id = getElementId(it)!!
            val relevanceMap = relevanceObjects[it]?.map { Pair(it.first, it.second.toString()) }?.toMap()
            LookupEntryInfo(id, it.lookupString.length, relevanceMap)
        }

        return newInfos
    }

    override fun completionStarted(lookup: LookupImpl, isExperimentPerformed: Boolean, experimentVersion: Int) {
        lookup.items.forEach { registerElement(it) }
        val relevanceObjects = lookup.getRelevanceObjects(lookup.items, false)

        val lookupEntryInfos = lookup.items.map {
            val id = getElementId(it)!!
            val relevanceMap = relevanceObjects[it]?.map { Pair(it.first, it.second?.toString()) }?.toMap()
            LookupEntryInfo(id, it.lookupString.length, relevanceMap)
        }

        val event = CompletionStartedEvent(installationUID, completionUID, isExperimentPerformed, experimentVersion, lookupEntryInfos, 0)
        logEvent(event)
    }

    override fun customMessage(message: String) {
        val event = CustomMessageEvent(installationUID, completionUID, message)
        logEvent(event)
    }

    override fun afterCharTyped(c: Char, lookup: LookupImpl) {
        val newInfos = getRecentlyAddedLookupItems(lookup)
        val ids = lookup.items.map { getElementId(it)!! }
        val currentPosition = lookup.items.indexOf(lookup.currentItem)

        val event = TypeEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        logEvent(event)
    }

    override fun downPressed(lookup: LookupImpl) {
        val newInfos = getRecentlyAddedLookupItems(lookup)
        val ids = if (newInfos.isNotEmpty()) lookup.items.map { getElementId(it)!! } else emptyList<Int>()
        val currentPosition = lookup.items.indexOf(lookup.currentItem)

        val event = DownPressedEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        logEvent(event)
    }

    override fun upPressed(lookup: LookupImpl) {
        val newInfos = getRecentlyAddedLookupItems(lookup)
        val ids = if (newInfos.isNotEmpty()) lookup.items.map { getElementId(it)!! } else emptyList<Int>()
        val currentPosition = lookup.items.indexOf(lookup.currentItem)

        val event = UpPressedEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        logEvent(event)
    }

    override fun completionCancelled() {
        val event = CompletionCancelledEvent(installationUID, completionUID)
        logEvent(event)
    }

    override fun itemSelectedByTyping(lookup: LookupImpl) {
        val item = lookup.currentItem!!
        val id = getElementId(item)!!
        val event = TypedSelectEvent(installationUID, completionUID, id)
        logEvent(event)
    }

    override fun itemSelectedCompletionFinished(lookup: LookupImpl) {
        val item = lookup.currentItem!!
        val index = lookup.items.indexOf(item)
        val event = ExplicitSelectEvent(installationUID, completionUID, emptyList(), emptyList(), index)
        logEvent(event)
    }
    
    override fun afterBackspacePressed(lookup: LookupImpl) {
        val newInfos = getRecentlyAddedLookupItems(lookup)
        val ids = lookup.items.map { getElementId(it)!! }
        val currentPosition = lookup.items.indexOf(lookup.currentItem)

        val event = BackspaceEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        logEvent(event)
    }

}

enum class Action {
    COMPLETION_STARTED,
    TYPE,
    BACKSPACE,
    UP,
    DOWN,
    COMPLETION_CANCELED,
    EXPLICIT_SELECT,
    TYPED_SELECT,
    CUSTOM
}
package com.intellij.stats.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
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

    private val LOG_NOTHING = { items: List<LookupStringWithRelevance> -> Unit }
    
    private val itemsToId: MutableMap<String, Int> = hashMapOf()
    
    private var logLastAction = LOG_NOTHING
    private var lastCompletionList: List<LookupStringWithRelevance> = emptyList()
    
    override fun completionStarted(completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction = { items ->
            val builder = logBuilder(Action.COMPLETION_STARTED)
            builder.addPair("COMP_LIST_LEN", items.size)
            builder.addText(firstCompletionListText(items))
            log(builder)
        }
    }

    override fun beforeCharTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        
        logLastAction = { items ->
            val builder = logBuilder(Action.TYPE)
            builder.addPair("COMP_LIST_LEN", items.size)
            builder.addText(createIdListAddUntrackedItems(items))
            log(builder)
        }
    }

    override fun afterCharTyped(c: Char, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
    }

    override fun downPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = logBuilder(Action.DOWN)
        builder.addPair("POS", pos)

        val id = getItemId(itemName)
        var idsList = ""
        if (id == null) {
            idsList = createIdListAddUntrackedItems(completionList)
        }

        builder.addPair("ID", getItemId(itemName)!!)

        if (idsList.isNotEmpty()) {
            builder.addText(idsList)
        }
        
        log(builder)
    }

    override fun upPressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = logBuilder(Action.UP)
        builder.addPair("POS", pos)

        val id = getItemId(itemName)
        var idsList = ""
        if (id == null) {
            idsList = createIdListAddUntrackedItems(completionList)
        }

        builder.addPair("ID", getItemId(itemName)!!)

        if (idsList.isNotEmpty()) {
            builder.addText(idsList)
        }
        
        builder.addPair("ID", getItemId(itemName)!!)
        log(builder)
    }

    override fun completionCancelled() {
        logLastAction(lastCompletionList)
        logLastAction = LOG_NOTHING
        val builder = logBuilder(Action.COMPLETION_CANCELED)
        log(builder)
    }

    override fun itemSelectedByTyping(itemName: String) {
        logLastAction(lastCompletionList)
        logLastAction = LOG_NOTHING
        
        val builder = logBuilder(Action.TYPED_SELECT)
        
        //todo remove: for debugging purposes
        if (getItemId(itemName) == null) {
            builder.addPair("ID", "NOT_IN_LIST $itemName")

            val allValues = itemsToId.map {
                "${it.key}=${it.value}"
            }.joinToString(",", "[", "]")
            
            builder.addText(allValues)
            log(builder)
            return
        }

        val id = getItemId(itemName)!!
        builder.addPair("ID", id)
        log(builder)
    }

    override fun itemSelectedCompletionFinished(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
        
        val builder = logBuilder(Action.EXPLICIT_SELECT)
        builder.addPair("POS", pos)
        val id = getItemId(itemName)!!
        builder.addPair("ID", id)
        log(builder)
    }

    override fun beforeBackspacePressed(completionList: List<LookupStringWithRelevance>) {
        logLastAction(completionList)
        logLastAction = LOG_NOTHING
    }

    override fun afterBackspacePressed(pos: Int, itemName: String, completionList: List<LookupStringWithRelevance>) {
        lastCompletionList = completionList
        
        val builder = logBuilder(Action.BACKSPACE)
        builder.addPair("POS", pos)
        builder.addPair("COMP_LIST_LEN", completionList.size)
        val ids = createIdListAddUntrackedItems(completionList)
        val id = getItemId(itemName)!!
        builder.addPair("ID", id)
        builder.addText(ids)
        log(builder)
    }

    private fun firstCompletionListText(items: List<LookupStringWithRelevance>): String {
        check(items.size > 0)
        val builder = StringBuilder()
        with(builder, {
            append("[")
            var first = true
            items.forEach {
                if (!first) {
                    append(", ")
                }
                first = false
                val id = addNewItem(it.item)
                append("{ID=$id, ${it.toData()}}")
            }
            append("]")
        })
        return builder.toString()
    }
    
    private fun log(logLineBuilder: LogLineBuilder) {
        val msg = logLineBuilder.text()
        println(msg)
        logFileManager.println(msg)
    }

    private fun getItemId(itemName: String): Int? {
        return itemsToId[itemName]
    }
    
    private fun addNewItem(itemName: String): Int {
        val size = itemsToId.size
        itemsToId[itemName] = size
        return size 
    }

    private fun createIdListAddUntrackedItems(items: List<LookupStringWithRelevance>): String {
        val idsList = StringBuilder()
        with (idsList) {
            append("[")
            var first = true
            items.forEach {
                if (!first) {
                    append(", ")
                }
                first = false
                
                var relevance = ""
                if (getItemId(it.item) == null) {
                    addNewItem(it.item)
                    relevance = " ${it.toData()}"
                }
                append("ID=${getItemId(it.item)!!}$relevance")
            }
            append(']')
        }
        return idsList.toString()
    }

    private fun logBuilder(action: Action): LogLineBuilder {
        return LogLineBuilder(installationUID, completionUID, action)
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
    TYPED_SELECT
}

class LogLineBuilder(val installationUID: String, val completionUID: String, val action: Action) {
    private val timestamp = System.currentTimeMillis()
    private val builder = StringBuilder()

    init {
        builder.append("$installationUID $completionUID $timestamp $action")
    }

    fun addText(any: Any) = builder.append(" $any")
    
    fun addPair(name: String, value: Any) = builder.append(" $name=$value")
    
    fun text() = builder.toString()

}

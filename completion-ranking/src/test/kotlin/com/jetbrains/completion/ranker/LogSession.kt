package com.jetbrains.completion.ranker


class CompletionLog(events: List<CompletionLogEvent>) {
    private val sessions = events.groupBy { it.sessionUid }

    fun session(session_id: String): CompletionSession {
        val events = sessions[session_id] ?: emptyList()
        return CompletionSession(events)
    }
}


class CompletionSession(val events: List<CompletionLogEvent>) {

    val lookupItems: Map<Int, LookupItemRelevance> by lazy {
        events.mapNotNull { it.newCompletionListItems }
                .concat()
                .map { LookupItemRelevance(it) }
                .associate { it.id.toInt() to it }
    }


    val lookupPages: List<LookupPage> by lazy {
        events.mapNotNull { it.intCompletionListIds }
                .filter { it.isNotEmpty() }
                .map { ids ->
                    val items = ids.mapIndexed { index, id -> PositionedItem(index, lookupItems[id]!!) }
                    LookupPage(items)
                }
    }

}


/**
 * Static picture of what is shown in the lookup
 */
class LookupPage(val lookupItems: List<PositionedItem>) {
    val size = lookupItems.size
}


class CompletionLogEvent(event: MutableMap<String, Any>) {
    init {
        //delegation will fail without key
        event.putIfAbsent("newCompletionListItems", emptyList<Map<String, Any>>())
        event.putIfAbsent("completionListIds", emptyList<Double>())
    }

    val newCompletionListItems: List<Map<String, Any>> by event
    val sessionUid: String by event
    val completionListIds: List<Double> by event

    val intCompletionListIds: List<Int>
        get() = completionListIds.map { it.toInt() }
}



class PositionedItem(val position: Int, val item: LookupItemRelevance) {
    val relevance: Map<String, Any>
        get() = item.relevance

    val length: Int
        get() = item.length.toInt()

    val id: Int
        get() = item.id.toInt()
}


class LookupItemRelevance(relevanceLog: Map<String, Any>) {
    val relevance: Map<String, Any> by relevanceLog
    val length: Double by relevanceLog
    val id: Double by relevanceLog
}
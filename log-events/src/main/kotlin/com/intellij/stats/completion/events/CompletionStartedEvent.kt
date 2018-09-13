/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.completion.events

import com.intellij.stats.completion.Action
import com.intellij.stats.completion.LogEventVisitor
import com.intellij.stats.completion.LookupEntryInfo


class CompletionStartedEvent(
        @JvmField var ideVersion: String,
        @JvmField var pluginVersion: String,
        @JvmField var mlRankingVersion: String,
        userId: String,
        sessionId: String,
        @JvmField var language: String?,
        @JvmField var performExperiment: Boolean,
        @JvmField var experimentVersion: Int,
        completionList: List<LookupEntryInfo>,
        @JvmField var userFactors: Map<String, String?>,
        selectedPosition: Int,
        timestamp: Long)

    : LookupStateLogData(
        userId,
        sessionId,
        Action.COMPLETION_STARTED,
        completionList.map { it.id },
        completionList,
        selectedPosition,
        timestamp)
{

    //seems it's not needed, remove when possible
    @JvmField var completionListLength: Int = completionList.size
    @JvmField var isOneLineMode: Boolean = false

    @JvmField var lookupShownTime: Long = -1
    @JvmField var mlTimeContribution: Long = -1
    @JvmField var isAutoPopup: Boolean? = null

    override fun accept(visitor: LogEventVisitor) {
        visitor.visit(this)
    }
}
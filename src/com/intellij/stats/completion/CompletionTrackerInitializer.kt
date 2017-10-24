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
package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.enhancer.LookupElementPositionTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.reporting.isSendAllowed
import com.intellij.reporting.isUnitTestMode
import com.intellij.stats.completion.experiment.WebServiceStatus
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(experimentHelper: WebServiceStatus): ApplicationComponent {
    companion object {
        var isEnabledInTests = false
    }

    private val actionListener = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup == null) {
            actionListener.listener = CompletionPopupListener.Adapter()
        }
        else if (lookup is LookupImpl) {
            if (isUnitTestMode() && !isEnabledInTests) return@PropertyChangeListener

            val shownTimesTracker = LookupElementPositionTracker(lookup)
            lookup.setPrefixChangeListener(shownTimesTracker)

            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
            val tracker = CompletionActionsTracker(lookup, logger, experimentHelper)
            actionListener.listener = tracker
            lookup.addLookupListener(tracker)
            lookup.setPrefixChangeListener(tracker)
        }
    }

    private fun shouldInitialize() = isSendAllowed() || isUnitTestMode()

    override fun initComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().addAnActionListener(actionListener)
        ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectOpened(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.addPropertyChangeListener(lookupTrackerInitializer)
            }

            override fun projectClosed(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.removePropertyChangeListener(lookupTrackerInitializer)
            }
        })
    }

    override fun disposeComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().removeAnActionListener(actionListener)
    }

}
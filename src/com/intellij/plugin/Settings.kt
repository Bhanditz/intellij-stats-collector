package com.intellij.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.sorting.SortingTimeStatistics
import com.intellij.stats.completion.experiment.WebServiceStatus
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel


class PluginSettingsConfigurableProvider : ConfigurableProvider() {
    override fun createConfigurable() = PluginSettingsConfigurable()
    override fun canCreateConfigurable() = ApplicationManager.getApplication().isInternal
}

class PluginSettingsConfigurable : Configurable {

    private lateinit var isForceExperimentCb: JBCheckBox

    override fun isModified(): Boolean {
        return isForceExperimentCb.isSelected != isMlSortingEnabledByForce()
    }

    override fun getDisplayName() = "Completion Stats Collector"

    override fun apply() {
        setMlSortingEnabledByForce(isForceExperimentCb.isSelected)
    }

    override fun createComponent(): JComponent? {
        isForceExperimentCb = JBCheckBox("Force Experiment", isMlSortingEnabledByForce())
        isForceExperimentCb.border = IdeBorderFactory.createEmptyBorder(5)

        val timingStats = JBLabel(getStatsText())
        timingStats.border = IdeBorderFactory.createEmptyBorder(5)

        val status = JBLabel(getStatusText())
        status.border = IdeBorderFactory.createEmptyBorder(5)

        val panel = JPanel()
        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(timingStats)
            add(status)
            add(isForceExperimentCb)
        }

        return panel
    }
    
    private fun getStatsText(): String {
        val stats = SortingTimeStatistics.getInstance()
        val time: List<String> = stats.state.getTimeDistribution()
        val avgTime: List<String> = stats.state.getAvgTimeByElementsSortedDistribution()

        if (time.isEmpty() && avgTime.isEmpty()) {
            return "No stats available"
        }

        return "<html>" +
                "<b>Time to Sorts Number Distribution:</b>" +
                "<br>" +
                time.joinToString("<br>") +
                "<br><br>" +
                "<b>Elements Count to Avg Sorting Time:</b>" +
                "<br>" +
                avgTime.joinToString("<br>") +
                "</html>"
    }

    private fun getStatusText(): String {
        val status = WebServiceStatus.getInstance()
        val isExperimentOnCurrentIDE = status.isExperimentOnCurrentIDE()
        val isExperimentGoingOnNow = status.isExperimentGoingOnNow()

        return "<html>" +
                "<br><br>" +
                "<b>Is experiment going on now:</b> $isExperimentGoingOnNow" +
                "<br>" +
                "<b>Is experiment on current IDE:</b> $isExperimentOnCurrentIDE" +
                "</html>"
    }

    override fun reset() {
        isForceExperimentCb.isSelected = isMlSortingEnabledByForce()
    }

    override fun getHelpTopic(): String? = null

    override fun disposeUIResources() = Unit
    
}